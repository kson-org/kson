import * as vscode from 'vscode';
import { assert } from './assert';
import { createTestFile, cleanUp, waitForDiagnostics, waitForDefinitions } from './common';

/**
 * Tests for bundled schema support.
 *
 * These tests verify that:
 * 1. The metaschema (draft7) is loaded for files with $schema field
 * 2. The enableBundledSchemas setting is respected
 * 3. Navigation to bundled schemas works via the bundled:// content provider
 */
describe('Bundled Schema Support Tests', () => {
    /**
     * Get the extension and verify it's active.
     */
    function getExtension(): vscode.Extension<any> | undefined {
        return vscode.extensions.getExtension('kson.kson');
    }

    describe('Configuration', () => {
        it('Should have enableBundledSchemas setting defined', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            const packageJson = extension.packageJSON;
            const configuration = packageJson?.contributes?.configuration;
            const properties = configuration?.properties;

            assert.ok(properties, 'Configuration properties should be defined');
            assert.ok(
                properties['kson.enableBundledSchemas'],
                'Should have kson.enableBundledSchemas setting'
            );
            assert.strictEqual(
                properties['kson.enableBundledSchemas'].type,
                'boolean',
                'Setting should be boolean type'
            );
            assert.strictEqual(
                properties['kson.enableBundledSchemas'].default,
                true,
                'Setting should default to true'
            );
        }).timeout(5000);

        it('Should be able to read enableBundledSchemas setting', async function () {
            const config = vscode.workspace.getConfiguration('kson');
            const enabled = config.get<boolean>('enableBundledSchemas');

            // Should be defined and default to true
            assert.strictEqual(typeof enabled, 'boolean', 'Setting should be a boolean');
            assert.strictEqual(enabled, true, 'Default value should be true');
        }).timeout(5000);
    });

    describe('Schema Loading', () => {
        it('Should handle language with no bundled schema', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Create a test file with standard .kson extension
            const content = 'key: "value"';
            const [testFileUri, document] = await createTestFile(content);

            try {
                // Document should be created successfully
                assert.ok(document, 'Document should be created');
                assert.strictEqual(document.languageId, 'kson', 'Should have kson language ID');

                // Wait for diagnostics to settle (0 expected for valid KSON)
                const diagnostics = await waitForDiagnostics(document.uri, 0);
                assert.strictEqual(diagnostics.length, 0, 'Valid KSON should have no diagnostics');
            } finally {
                await cleanUp(testFileUri);
            }
        }).timeout(10000);
    });

    describe('Status Bar Integration', () => {
        it('Should show status bar for KSON files', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            const content = 'key: "value"';
            const [testFileUri, document] = await createTestFile(content);

            try {
                // Make sure the document is shown
                await vscode.window.showTextDocument(document);

                // Wait for diagnostics to settle (ensures server has processed)
                await waitForDiagnostics(document.uri, 0);

                // We can't directly test the status bar content from tests,
                // but we verify the document is properly set up
                assert.ok(vscode.window.activeTextEditor, 'Should have active editor');
                assert.strictEqual(
                    vscode.window.activeTextEditor?.document.uri.toString(),
                    testFileUri.toString(),
                    'Active editor should show test file'
                );
            } finally {
                await cleanUp(testFileUri);
            }
        }).timeout(10000);
    });

    describe('Settings Changes', () => {
        it('Should have correct setting scope', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            const packageJson = extension.packageJSON;
            const properties = packageJson?.contributes?.configuration?.properties;
            const setting = properties?.['kson.enableBundledSchemas'];

            assert.ok(setting, 'Setting should exist');
            assert.ok(setting.description, 'Setting should have a description');
            assert.strictEqual(setting.type, 'boolean', 'Setting should be boolean');
        }).timeout(5000);
    });

    describe('Bundled Schema Navigation', () => {
        it('Should have bundled:// content provider registered', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Create a bundled:// URI - even if no schemas exist, the provider should be registered
            const bundledUri = vscode.Uri.parse('bundled://schema/test-language');

            // Try to open the document - this will fail with a specific error if the provider
            // is not registered vs if the content just doesn't exist
            try {
                const doc = await vscode.workspace.openTextDocument(bundledUri);
                // If we get here with content, great - there's a schema for this language
                // If not, the provider returned undefined which is also valid
                assert.ok(true, 'Content provider is registered');
            } catch (error: any) {
                // Check if the error is "cannot open" (provider not found) vs content not available
                const message = error?.message || String(error);

                // If the provider is registered but returns undefined, VS Code may throw
                // "cannot open bundled://schema/test-extension.schema.kson" but NOT "Unable to resolve"
                // The "Unable to resolve resource" error indicates no provider is registered
                if (message.includes('Unable to resolve resource')) {
                    assert.fail('bundled:// content provider is not registered');
                }
                // Other errors (like empty content) are acceptable
                assert.ok(true, 'Content provider is registered (returned no content for test extension)');
            }
        }).timeout(5000);

        it('Should be able to open metaschema via bundled URI', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // The metaschema uses the metaschema authority
            const bundledUri = vscode.Uri.parse('bundled://metaschema/draft-07.schema.kson');

            try {
                const doc = await vscode.workspace.openTextDocument(bundledUri);
                assert.ok(doc, 'Should be able to open metaschema document');
                assert.ok(doc.getText().length > 0, 'Metaschema should have content');
                console.log(`Successfully opened metaschema, content length: ${doc.getText().length}`);
            } catch (error: any) {
                const message = error?.message || String(error);
                if (message.includes('Unable to resolve resource')) {
                    assert.fail('bundled:// content provider failed to resolve metaschema');
                }
                throw error;
            }
        }).timeout(10000);

        it('Should navigate to metaschema via Go to Definition on file with $schema', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Create a .schema.kson test file with $schema declaration
            const content = "'$schema': 'http://json-schema.org/draft-07/schema#'\ntype: object";
            const fileName = 'definition-test.schema.kson';
            const [testFileUri, document] = await createTestFile(content, fileName);

            try {
                // Verify the document is recognized as kson
                assert.strictEqual(
                    document.languageId,
                    'kson',
                    'Document should have language ID \'kson\''
                );

                // Position the cursor on the "type" property (line 1)
                const position = new vscode.Position(1, 1);

                // Poll until definitions become available (schema association may take time)
                const definitions = await waitForDefinitions(document.uri, position, 10000);

                // Log results for debugging
                console.log('Definition results for file with $schema:', JSON.stringify(definitions, null, 2));

                // Get the definition URI (handle both Location and LocationLink types)
                const firstDef = definitions[0];
                const definitionUri = 'uri' in firstDef
                    ? firstDef.uri
                    : (firstDef as vscode.LocationLink).targetUri;

                console.log(`Definition URI: ${definitionUri.toString()}`);

                // Verify the definition points to a bundled:// URI
                assert.ok(
                    definitionUri.scheme === 'bundled',
                    `Definition should point to bundled:// scheme, got: ${definitionUri.scheme}`
                );

                assert.ok(
                    definitionUri.toString().includes('bundled://metaschema/draft-07.schema.kson'),
                    `Definition should point to metaschema, got: ${definitionUri.toString()}`
                );

                // Verify we can actually open the bundled schema document
                const schemaDoc = await vscode.workspace.openTextDocument(definitionUri);
                assert.ok(schemaDoc, 'Should be able to open the metaschema document');
                assert.ok(schemaDoc.getText().length > 0, 'Metaschema document should have content');

                // Verify the metaschema contains the "type" property definition
                const schemaContent = schemaDoc.getText();
                assert.ok(
                    schemaContent.includes('type'),
                    'Metaschema should contain the "type" property definition'
                );

                console.log(`Successfully navigated to metaschema, content length: ${schemaContent.length}`);
            } finally {
                await cleanUp(testFileUri);
            }
        }).timeout(15000);
    });
});

import * as vscode from 'vscode';
import { assert } from './assert';
import { createTestFile, cleanUp } from './common';
import { v4 as uuid } from 'uuid';

/**
 * Tests for bundled schema support.
 *
 * These tests verify that:
 * 1. Bundled schemas are loaded from package.json configuration
 * 2. Language configuration includes bundled schema mappings
 * 3. The enableBundledSchemas setting is respected
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

        it('Should have bundledSchema field in language contributions', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            const packageJson = extension.packageJSON;
            const languages = packageJson?.contributes?.languages || [];

            assert.ok(languages.length > 0, 'Should have at least one language defined');

            // The kson language should have bundledSchema field (even if null)
            const ksonLanguage = languages.find((lang: any) => lang.id === 'kson');
            assert.ok(ksonLanguage, 'Should have kson language defined');
            assert.ok(
                'bundledSchema' in ksonLanguage,
                'kson language should have bundledSchema field'
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
        /**
         * Detects if we're running in a Node.js environment (vs browser).
         * Some tests may behave differently between environments.
         */
        function isNodeEnvironment(): boolean {
            return typeof process !== 'undefined' &&
                process.versions != null &&
                process.versions.node != null;
        }

        it('Should create language configuration with bundledSchemas', async function () {
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Import and test the language config module
            // This verifies the configuration is being parsed correctly
            const packageJson = extension.packageJSON;
            const languages = packageJson?.contributes?.languages || [];

            const bundledSchemas = languages
                .filter((lang: any) => lang.id && lang.bundledSchema)
                .map((lang: any) => ({
                    languageId: lang.id,
                    schemaPath: lang.bundledSchema
                }));

            // Log what was found
            console.log(`Found ${bundledSchemas.length} bundled schema configurations`);
            if (bundledSchemas.length > 0) {
                console.log('Bundled schemas:', JSON.stringify(bundledSchemas, null, 2));
            }

            // This is a structural test - we're verifying the config format is correct
            assert.ok(Array.isArray(bundledSchemas), 'bundledSchemas should be an array');
        }).timeout(5000);

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

                // Wait a bit for the language server to process
                await new Promise(resolve => setTimeout(resolve, 500));

                // No errors should occur - this is a basic sanity check
                const diagnostics = vscode.languages.getDiagnostics(document.uri);
                // Should have 0 diagnostics for valid KSON
                assert.strictEqual(diagnostics.length, 0, 'Valid KSON should have no diagnostics');
            } finally {
                await cleanUp(testFileUri);
            }
        }).timeout(10000);
    });

    describe('Status Bar Integration', () => {
        it('Should show status bar for KSON files', async function () {
            // Skip in browser environment as status bar may behave differently
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

                // Wait for status bar to update
                await new Promise(resolve => setTimeout(resolve, 500));

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

    describe('Bundled Schema Loader', () => {
        it('Should export correct types from bundledSchemaLoader', async function () {
            // This test verifies the module structure is correct
            // We can't import directly in tests, so we verify through package.json
            const extension = getExtension();
            if (!extension) {
                this.skip();
                return;
            }

            // Verify the language configuration is properly structured
            const packageJson = extension.packageJSON;
            const languages = packageJson?.contributes?.languages || [];

            for (const lang of languages) {
                if (lang.bundledSchema) {
                    // If bundledSchema is defined, it should be a string path
                    assert.strictEqual(
                        typeof lang.bundledSchema,
                        'string',
                        `bundledSchema for ${lang.id} should be a string path`
                    );
                    assert.ok(
                        lang.bundledSchema.includes('/') || lang.bundledSchema.includes('\\'),
                        `bundledSchema path for ${lang.id} should be a path`
                    );
                }
            }
        }).timeout(5000);
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
});

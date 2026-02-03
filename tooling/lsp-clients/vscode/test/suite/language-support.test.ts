import * as vscode from 'vscode';
import { assert } from './assert';
import { createTestFile, cleanUp } from './common';

/**
 * Tests for KSON language support.
 *
 * These tests verify that language files (e.g., .KuStON) get the same
 * language server features as .kson files.
 */
describe('Language Support Tests', () => {

    async function waitForDiagnostics(uri: vscode.Uri, expectedCount: number, timeout: number = 5000): Promise<vscode.Diagnostic[]> {
        const startTime = Date.now();

        while (Date.now() - startTime < timeout) {
            const diagnostics = vscode.languages.getDiagnostics(uri);
            if (diagnostics.length === expectedCount) {
                return diagnostics;
            }
            await new Promise(resolve => setTimeout(resolve, 100));
        }

        throw new Error(`Timeout waiting for ${expectedCount} diagnostics, found ${vscode.languages.getDiagnostics(uri).length}`);
    }

    it('Should report diagnostics for invalid language file', async function() {
        // Skip this test if no languages are configured
        const extension = vscode.extensions.getExtension('kson.kson');
        if (!extension) {
            this.skip();
            return;
        }

        const packageJson = extension.packageJSON;
        const languages = packageJson?.contributes?.languages || [];
        const additionalLanguages = languages.filter((lang: any) => lang.id !== 'kson');

        if (additionalLanguages.length === 0) {
            console.log('No additional languages configured, skipping test');
            this.skip();
            return;
        }

        // Test with the first configured language
        const language = additionalLanguages[0];
        const extension_suffix = language.extensions?.[0] || '.test';
        const fileName = `test${extension_suffix}`;

        const errorContent = 'key: "value" extraValue'; // Invalid KSON
        const [testFileUri, document] = await createTestFile(errorContent, fileName);

        // Verify the document has the correct language ID
        assert.strictEqual(document.languageId, language.id, `Document should have language ID: ${language.id}`);

        // Should get diagnostics just like a .kson file
        const diagnostics = await waitForDiagnostics(document.uri, 1);
        assert.ok(diagnostics.length === 1, `Language file should receive diagnostics`);

        await cleanUp(testFileUri);
    }).timeout(10000);

    it('Should not report diagnostics for valid language file', async function() {
        // Skip this test if no languages are configured
        const extension = vscode.extensions.getExtension('kson.kson');
        if (!extension) {
            this.skip();
            return;
        }

        const packageJson = extension.packageJSON;
        const languages = packageJson?.contributes?.languages || [];
        const additionalLanguages = languages.filter((lang: any) => lang.id !== 'kson');

        if (additionalLanguages.length === 0) {
            console.log('No additional languages configured, skipping test');
            this.skip();
            return;
        }

        // Test with the first configured language
        const language = additionalLanguages[0];
        const extension_suffix = language.extensions?.[0] || '.test';
        const fileName = `test${extension_suffix}`;

        const validContent = 'key: "value"'; // Valid KSON
        const [testFileUri, document] = await createTestFile(validContent, fileName);

        // Verify the document has the correct language ID
        assert.strictEqual(document.languageId, language.id, `Document should have language ID: ${language.id}`);

        // Should not get diagnostics
        const diagnostics = await waitForDiagnostics(document.uri, 0);
        assert.ok(diagnostics.length === 0, `Valid language file should not have diagnostics`);

        await cleanUp(testFileUri);
    }).timeout(10000);

    it('Should support multiple languages if configured', async function() {
        const extension = vscode.extensions.getExtension('kson.kson');
        if (!extension) {
            this.skip();
            return;
        }

        const packageJson = extension.packageJSON;
        const languages = packageJson?.contributes?.languages || [];

        // Should have at least kson registered
        assert.ok(languages.length >= 1, 'Should have at least kson language registered');
        assert.ok(languages.some((lang: any) => lang.id === 'kson'), 'Should have kson language');

        // Log configured languages for debugging
        console.log('Configured languages:', languages.map((l: any) => l.id).join(', '));
    }).timeout(5000);
});
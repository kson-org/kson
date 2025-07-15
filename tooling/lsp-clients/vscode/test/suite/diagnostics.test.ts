import * as vscode from 'vscode';
import { assert } from './assert';
import {createTestFile, cleanUp} from './common';


describe('Diagnostic Tests', () => {

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

    it('Should report errors for an invalid Kson file', async () => {
        const errorContent = 'key: "value" extraValue';
        const [testFileUri, document] = await createTestFile(errorContent);

        const diagnostics = await waitForDiagnostics(document.uri, 1);

        assert.ok(diagnostics.length == 1, `Diagnostic is reported for ${testFileUri}.`);

        await cleanUp(testFileUri);
    }).timeout(10000);

    it('Should not report errors for an valid Kson file', async () => {
        const errorContent = 'key: "value"';
        const [testFileUri, document] = await createTestFile(errorContent);

        const diagnostics = await waitForDiagnostics(document.uri, 0);

        assert.ok(diagnostics.length == 0, `Diagnostic is reported for ${testFileUri}.`);

        await cleanUp(testFileUri);
    }).timeout(10000);
});
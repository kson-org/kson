import * as vscode from 'vscode';
import * as assert from 'assert';
import {createTestFile, cleanUp} from './common';


describe('Diagnostic Tests', () => {

    it('Should report errors for an invalid Kson file', async () => {
        const errorContent = 'key: "value" extraValue';
        const [testFileUri, document] = await createTestFile(errorContent);

        const diagnostics = vscode.languages.getDiagnostics(document.uri);

        assert.ok(diagnostics.length == 1, `Diagnostic is reported for ${testFileUri}.`);

        await cleanUp(testFileUri);
    }).timeout(10000);
});
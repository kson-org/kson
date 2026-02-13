import * as vscode from 'vscode';
import { assert } from './assert';
import {createTestFile, cleanUp, waitForDiagnostics} from './common';
import {DiagnosticSeverity} from "vscode";


describe('Diagnostic Tests', () => {

    it('Should report errors for an invalid Kson file', async () => {
        const errorContent = 'key: "value" extraValue';
        const [testFileUri, document] = await createTestFile(errorContent);

        const diagnostics = await waitForDiagnostics(document.uri, 1);

        assert.ok(diagnostics.length == 1, `Diagnostic is reported for ${testFileUri}.`);

        await cleanUp(testFileUri);
    }).timeout(10000);

    it('Should report both errors and warnings', async () => {
        const errorContent = [
            '- {list_item: false false}',
            '    - deceptive_indent_list_item'
        ].join('\n');
        const [testFileUri, document] = await createTestFile(errorContent);

        const diagnostics = await waitForDiagnostics(document.uri, 2);

        assert.strictEqual(diagnostics[0].severity, DiagnosticSeverity.Error, `should have error diagnostic for invalid object.`);
        assert.strictEqual(diagnostics[1].severity, DiagnosticSeverity.Warning, `should have warning diagnostic for bad indent.`)
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
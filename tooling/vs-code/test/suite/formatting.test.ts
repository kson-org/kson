import * as vscode from 'vscode';
import { createTestFile, cleanUp, assertTextEqual } from './common';
import {afterEach, describe, test} from 'mocha';


describe('Formatting Tests', () => {
    let testFileUri: vscode.Uri | undefined;

    afterEach(async () => {
        if (testFileUri) {
            await cleanUp(testFileUri);
            testFileUri = undefined;
        }
    });

    test('Should format a Kson document', async () => {
        const unformattedContent = '{"a":1,"b":{"c":2}}';
        const expectedText = [
            'a: 1',
            'b:',
            '    c: 2'
        ].join('\n');

        const [uri, document] = await createTestFile(unformattedContent);
        testFileUri = uri;

        await vscode.commands.executeCommand('editor.action.formatDocument');
        await new Promise(resolve => setTimeout(resolve, 1000));

        assertTextEqual(document, expectedText);
    }).timeout(10000);
}); 
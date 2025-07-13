import * as vscode from 'vscode';
import { createTestFile, cleanUp, assertTextEqual } from './common';


describe('Editing Tests', () => {
    let testFileUri: vscode.Uri | undefined;

    afterEach(async () => {
        if (testFileUri) {
            await cleanUp(testFileUri);
            testFileUri = undefined;
        }
    });

    it('Should auto-close $ to $$$', async () => {
        const [uri, document] = await createTestFile();
        testFileUri = uri;

        await vscode.commands.executeCommand('type', { text: '$' });

        assertTextEqual(document, '$$$');
    }).timeout(10000);

    it('Should auto-close % to %%%', async () => {
        const [uri, document] = await createTestFile();
        testFileUri = uri;

        await vscode.commands.executeCommand('type', { text: '%' });

        assertTextEqual(document, '%%%');
    }).timeout(10000);

    it('Should indent an embed block delimited with $, without tag', async () => {
        const [uri, document] = await createTestFile();
        testFileUri = uri;

        await vscode.commands.executeCommand('type', { text: 'key: $\n' });

        assertTextEqual(document, [
            'key: $',
            '    $$'
        ].join('\n'));
    }).timeout(10000);

    it('Should indent an embed block delimited with %, with tag', async () => {
        const [uri, document] = await createTestFile();
        testFileUri = uri;

        await vscode.commands.executeCommand('type', { text: 'key: %tag\n' });

        assertTextEqual(document, [
            'key: %tag',
            '    %%'
        ].join('\n'));
    }).timeout(10000);
}); 
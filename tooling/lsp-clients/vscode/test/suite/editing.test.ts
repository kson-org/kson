import * as vscode from 'vscode';
import { createTestFile, cleanUp, assertTextEqual } from './common';

/**
 * Types `text` at the end of the document after forcing tokenization to catch
 * up, since VS Code ignores onEnterRules on lines it has not tokenized yet.
 */
async function typeAtEnd(text: string): Promise<void> {
    await vscode.commands.executeCommand('cursorBottom');
    await vscode.commands.executeCommand('editor.action.forceRetokenize');
    await vscode.commands.executeCommand('type', { text });
}

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

    it('Should outdent after an end-dot', async () => {
        const [uri, document] = await createTestFile([
            'key:',
            '    x: 1',
            '    .'
        ].join('\n'));
        testFileUri = uri;

        await typeAtEnd('\ny: 2');

        assertTextEqual(document, [
            'key:',
            '    x: 1',
            '    .',
            'y: 2'
        ].join('\n'));
    }).timeout(10000);

    it('Should outdent after an end-dash', async () => {
        const [uri, document] = await createTestFile([
            '-',
            '    - 1',
            '    ='
        ].join('\n'));
        testFileUri = uri;

        await typeAtEnd('\n- 2');

        assertTextEqual(document, [
            '-',
            '    - 1',
            '    =',
            '- 2'
        ].join('\n'));
    }).timeout(10000);

    it('Should outdent after an end-dot that follows a value', async () => {
        const [uri, document] = await createTestFile([
            'key:',
            '    list: end .'
        ].join('\n'));
        testFileUri = uri;

        await typeAtEnd('\ny: 2');

        assertTextEqual(document, [
            'key:',
            '    list: end .',
            'y: 2'
        ].join('\n'));
    }).timeout(10000);

    it('Should outdent after an end-dash that follows a list item', async () => {
        const [uri, document] = await createTestFile([
            '-',
            '    - nested',
            '    - list ='
        ].join('\n'));
        testFileUri = uri;

        await typeAtEnd('\n- next');

        assertTextEqual(document, [
            '-',
            '    - nested',
            '    - list =',
            '- next'
        ].join('\n'));
    }).timeout(10000);

    it('Should not outdent after a comment ending in a dot', async () => {
        const [uri, document] = await createTestFile([
            'key:',
            '    # done.'
        ].join('\n'));
        testFileUri = uri;

        await typeAtEnd('\nx: 1');

        assertTextEqual(document, [
            'key:',
            '    # done.',
            '    x: 1'
        ].join('\n'));
    }).timeout(10000);
}); 
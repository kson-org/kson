import * as vscode from 'vscode';
import {createTestFile, cleanUp, pollUntil} from './common';


describe('Formatting Settings Test Suite', () => {
    let testFileUri: vscode.Uri | undefined;

    /**
     * This function returns a guaranteed fresh copy of our test document.
     */
    let document: () => Promise<vscode.TextDocument> = async () => {
        // always fetch from the workspace to ensure we see the latest edits
        return vscode.workspace.openTextDocument(testFileUri!);
    };

    /**
     * Format the document and poll until the result matches the expected text.
     *
     * Configuration changes use the LSP pull model: the server receives a
     * didChangeConfiguration notification and then makes an async round-trip
     * to pull the new settings.  A format request issued immediately after
     * a config change may arrive before that pull completes.  Polling
     * accounts for this inherent race.
     */
    async function formatAndAwait(expectedText: string): Promise<void> {
        await pollUntil(
            async () => {
                await vscode.commands.executeCommand('editor.action.formatDocument');
                return (await document()).getText().replace(/\r\n/g, '\n');
            },
            text => text === expectedText,
            { timeout: 5000, interval: 100, message: `Expected formatting:\n${expectedText}` }
        );
    }

    beforeEach(async () => {
        // Sample unformatted content used in all tests
        const unformattedContent = '{"a":1,"b":{"c":2}}';

        const [uri, _] = await createTestFile(unformattedContent);
        testFileUri = uri;

        // Wait for the extension to be active
        const extension = vscode.extensions.getExtension('kson.kson');
        if (extension && !extension.isActive) {
            await extension.activate();
        }
    })

    afterEach(async () => {
        // Reset all kson settings to defaults before cleaning up
        const config = vscode.workspace.getConfiguration('kson');
        await config.update('format.insertSpaces', undefined, vscode.ConfigurationTarget.Workspace);
        await config.update('format.tabSize', undefined, vscode.ConfigurationTarget.Workspace);
        await config.update('format.formattingStyle', undefined, vscode.ConfigurationTarget.Workspace);

        if (testFileUri) {
            await cleanUp(testFileUri);
            testFileUri = undefined;
        }
    });

    it('Should format with 8 spaces when insertSpaces is true', async () => {
        // Update settings to use spaces with 8-space indentation
        const config = vscode.workspace.getConfiguration('kson');
        await config.update('format.insertSpaces', true, vscode.ConfigurationTarget.Workspace);
        await config.update('format.tabSize', 8, vscode.ConfigurationTarget.Workspace);
        await config.update('format.formattingStyle', 'plain', vscode.ConfigurationTarget.Workspace);

        const expectedText = [
            'a: 1',
            'b:',
            '        c: 2'
        ].join('\n');

        await formatAndAwait(expectedText);
    }).timeout(10000);

    it('Should format with tabs when insertSpaces is false', async () => {
        // Update settings to use tabs
        const config = vscode.workspace.getConfiguration('kson');
        await config.update('format.insertSpaces', false, vscode.ConfigurationTarget.Workspace);
        await config.update('format.tabSize', 4, vscode.ConfigurationTarget.Workspace);
        await config.update('format.formattingStyle', 'plain', vscode.ConfigurationTarget.Workspace);

        const expectedText = [
            'a: 1',
            'b:',
            '\tc: 2'
        ].join('\n');

        await formatAndAwait(expectedText);
    }).timeout(10000);

    it('Should format delimited when formattingStyle is delimited', async () => {
        // Update settings to use delimited formatting with spaces
        const config = vscode.workspace.getConfiguration('kson');
        await config.update('format.insertSpaces', true, vscode.ConfigurationTarget.Workspace);
        await config.update('format.tabSize', 2, vscode.ConfigurationTarget.Workspace);
        await config.update('format.formattingStyle', "delimited", vscode.ConfigurationTarget.Workspace);

        const expectedText = [
            '{',
            '  a: 1',
            '  b: {',
            '    c: 2',
            '  }',
            '}'
        ].join('\n');

        await formatAndAwait(expectedText);
    }).timeout(10000);

    it('Should update formatting when settings change dynamically', async () => {
        const expectedSpacesText = [
            'a: 1',
            'b:',
            '  c: 2'
        ].join('\n');
        const expectedTabsText = [
            'a: 1',
            'b:',
            '\tc: 2'
        ].join('\n');

        const config = vscode.workspace.getConfiguration('kson');

        // First format with spaces
        await config.update('format.insertSpaces', true, vscode.ConfigurationTarget.Workspace);
        await config.update('format.tabSize', 2, vscode.ConfigurationTarget.Workspace);
        await config.update('format.formattingStyle', 'plain', vscode.ConfigurationTarget.Workspace);

        await formatAndAwait(expectedSpacesText);

        // Now change to tabs
        await config.update('format.insertSpaces', false, vscode.ConfigurationTarget.Workspace);

        await formatAndAwait(expectedTabsText);
    }).timeout(15000);
});
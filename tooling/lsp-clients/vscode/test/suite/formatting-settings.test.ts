import * as vscode from 'vscode';
import {createTestFile, cleanUp, pollUntil} from './common';
import { assert } from './assert';


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
     * Show the test document and set the editor's indentation (the "Spaces/Tabs"
     * status-bar toggle), which is the source of truth the formatter reads.
     */
    async function showWithIndentation(insertSpaces: boolean, tabSize: number): Promise<vscode.TextEditor> {
        const editor = await vscode.window.showTextDocument(await document());
        editor.options = { insertSpaces, tabSize };
        return editor;
    }

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
        // Reset kson settings to defaults before cleaning up. Indentation is per-editor
        // (gone when the editor closes in cleanUp), so only the style needs resetting.
        const config = vscode.workspace.getConfiguration('kson');
        await config.update('format.formattingStyle', undefined, vscode.ConfigurationTarget.Workspace);

        if (testFileUri) {
            await cleanUp(testFileUri);
            testFileUri = undefined;
        }
    });

    it('Should format with 8 spaces when the editor uses 8-wide spaces', async () => {
        await showWithIndentation(true, 8);

        const expectedText = [
            'a: 1',
            'b:',
            '        c: 2'
        ].join('\n');

        await formatAndAwait(expectedText);
    }).timeout(10000);

    it('Should format with tabs when the editor uses tabs', async () => {
        await showWithIndentation(false, 4);

        const expectedText = [
            'a: 1',
            'b:',
            '\tc: 2'
        ].join('\n');

        await formatAndAwait(expectedText);
    }).timeout(10000);

    it('Should format delimited when formattingStyle is delimited', async () => {
        // Style has no editor equivalent, so it stays a kson config key.
        const config = vscode.workspace.getConfiguration('kson');
        await config.update('format.formattingStyle', "delimited", vscode.ConfigurationTarget.Workspace);

        // Pin the editor indentation so the delimited output is deterministic.
        await showWithIndentation(true, 2);

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

    it('Should update formatting when the editor indentation changes', async () => {
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

        // First format with spaces
        const editor = await showWithIndentation(true, 2);
        await formatAndAwait(expectedSpacesText);

        // Now switch the same editor to tabs
        editor.options = { insertSpaces: false, tabSize: 2 };
        await formatAndAwait(expectedTabsText);
    }).timeout(15000);

    it('Should honor the editor indentation when running a CodeLens format command', async () => {
        // Resolve the real CodeLenses so we invoke the exact wire command + args the buttons use.
        const lenses = await pollUntil(
            () => vscode.commands.executeCommand<vscode.CodeLens[]>('vscode.executeCodeLensProvider', testFileUri!),
            result => !!result && result.length > 0,
            { timeout: 5000, message: 'No code lenses resolved' }
        );

        const plainLens = lenses.find(lens => lens.command?.command.endsWith('.plainFormat'));
        assert.ok(plainLens?.command, 'Expected a plainFormat code lens');

        // Drive the editor to tabs; the client middleware must inject this into the command.
        await showWithIndentation(false, 4);

        const expectedText = [
            'a: 1',
            'b:',
            '\tc: 2'
        ].join('\n');

        // Invoking the wire command routes through the LanguageClient executeCommand
        // middleware, which injects the active editor's indentation into the args.
        await pollUntil(
            async () => {
                await vscode.commands.executeCommand(
                    plainLens!.command!.command,
                    ...(plainLens!.command!.arguments ?? [])
                );
                return (await document()).getText().replace(/\r\n/g, '\n');
            },
            text => text === expectedText,
            { timeout: 5000, interval: 100, message: `Expected tab-indented formatting:\n${expectedText}` }
        );
    }).timeout(15000);
});

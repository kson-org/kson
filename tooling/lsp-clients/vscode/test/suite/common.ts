import * as vscode from 'vscode';
import { v4 as uuid } from 'uuid';
import { assert } from './assert';

export async function createTestFile(initialContent: string = '', fileName: string = `${uuid()}.kson`): Promise<[vscode.Uri, vscode.TextDocument]> {
    const workspaceFolder = getWorkspaceFolder();

    // Always use VS Code's URI joining which works in both environments
    const uri = vscode.Uri.joinPath(workspaceFolder.uri, fileName);

    // Use TextEncoder for browser compatibility (works in Node.js too)
    const encoder = new TextEncoder();
    await vscode.workspace.fs.writeFile(uri, encoder.encode(initialContent));

    const document = await vscode.workspace.openTextDocument(uri);
    await vscode.window.showTextDocument(document);

    return [uri, document];
}

export async function cleanUp(uri: vscode.Uri) {
    if (vscode.window.activeTextEditor && vscode.window.activeTextEditor.document.uri.toString() === uri.toString()) {
        await vscode.commands.executeCommand('workbench.action.closeActiveEditor');
    }
    await vscode.workspace.fs.delete(uri, { useTrash: false });
}

export function assertTextEqual(document: vscode.TextDocument, expectedText: string) {
    // Note: we transform `\r\n` into `\n` for cross-platform compatibility
    const actualText = document.getText().replace(/\r\n/g, '\n');
    assert.strictEqual(actualText, expectedText);
}

function getWorkspaceFolder(): vscode.WorkspaceFolder {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (!workspaceFolders || workspaceFolders.length === 0) {
        throw new Error('No workspace folder open');
    }
    return workspaceFolders[0];
}

/**
 * Generic polling helper. Repeatedly calls `fn` until `predicate` returns true, or timeout.
 */
export async function pollUntil<T>(
    fn: () => T | PromiseLike<T>,
    predicate: (result: T) => boolean,
    options: { timeout?: number; interval?: number; message?: string } = {}
): Promise<T> {
    const { timeout = 5000, interval = 100, message = 'Condition not met' } = options;
    const startTime = Date.now();

    while (Date.now() - startTime < timeout) {
        const result = await fn();
        if (predicate(result)) {
            return result;
        }
        await new Promise(resolve => setTimeout(resolve, interval));
    }

    throw new Error(`Timeout: ${message}`);
}

/**
 * Poll until diagnostics reach the expected count.
 */
export function waitForDiagnostics(uri: vscode.Uri, expectedCount: number, timeout: number = 5000): Promise<vscode.Diagnostic[]> {
    return pollUntil(
        () => vscode.languages.getDiagnostics(uri),
        diagnostics => diagnostics.length === expectedCount,
        { timeout, message: `Expected ${expectedCount} diagnostics, found ${vscode.languages.getDiagnostics(uri).length}` }
    );
}

/**
 * Poll until Go to Definition returns results.
 */
export function waitForDefinitions(
    uri: vscode.Uri,
    position: vscode.Position,
    timeout: number = 5000
): Promise<vscode.Location[] | vscode.LocationLink[]> {
    return pollUntil(
        () => vscode.commands.executeCommand<vscode.Location[] | vscode.LocationLink[]>(
            'vscode.executeDefinitionProvider', uri, position
        ),
        definitions => !!(definitions && definitions.length > 0),
        { timeout, interval: 200, message: `No definitions at ${uri.toString()}:${position.line}:${position.character}` }
    );
}

/**
 * Poll until hover information is available.
 */
export function waitForHover(
    document: vscode.TextDocument,
    position: vscode.Position,
    timeout: number = 5000
): Promise<vscode.Hover[]> {
    return pollUntil(
        () => vscode.commands.executeCommand<vscode.Hover[]>(
            'vscode.executeHoverProvider', document.uri, position
        ),
        hovers => !!(hovers && hovers.length > 0),
        { timeout, message: `No hover at position ${position.line}:${position.character}` }
    );
}

/**
 * Poll until completion items are available.
 */
export function waitForCompletions(
    document: vscode.TextDocument,
    position: vscode.Position,
    timeout: number = 5000
): Promise<vscode.CompletionList> {
    return pollUntil(
        () => vscode.commands.executeCommand<vscode.CompletionList>(
            'vscode.executeCompletionItemProvider', document.uri, position
        ),
        completions => !!(completions && completions.items.length > 0),
        { timeout, message: `No completions at position ${position.line}:${position.character}` }
    );
}
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
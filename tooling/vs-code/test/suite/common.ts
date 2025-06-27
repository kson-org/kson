import * as assert from 'node:assert';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { v4 as uuid } from 'uuid';


export async function createTestFile(initialContent: string = ''): Promise<[vscode.Uri, vscode.TextDocument]> {
    const workspaceFolder = getWorkspaceFolder();
    const fileName = `${uuid()}.kson`;
    const uri = vscode.Uri.file(path.resolve(workspaceFolder.uri.fsPath, fileName));
    
    await vscode.workspace.fs.writeFile(uri, Buffer.from(initialContent));
    const document = await vscode.workspace.openTextDocument(uri);
    await vscode.window.showTextDocument(document);
    
    // Wait for language server to initialize
    await new Promise(resolve => setTimeout(resolve, 1000));

    return [uri, document];
}

export async function cleanUp(uri: vscode.Uri) {
    if (vscode.window.activeTextEditor && vscode.window.activeTextEditor.document.uri.toString() === uri.toString()) {
        await vscode.commands.executeCommand('workbench.action.closeActiveEditor');
    }
    await vscode.workspace.fs.delete(uri, { useTrash: false });
}

export function assertTextEqual(document: vscode.TextDocument, expectedText: string) {
    const actualText = document.getText();
    assert.strictEqual(actualText, expectedText);
}

function getWorkspaceFolder(): vscode.WorkspaceFolder {
    const workspaceFolders = vscode.workspace.workspaceFolders;
    assert.ok(workspaceFolders, 'No workspace folder found');
    return workspaceFolders[0];
}
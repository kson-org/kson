import vscode from 'vscode';
import {
    LanguageClientOptions,
    ErrorAction,
    CloseAction,
} from 'vscode-languageclient';

// Create shared client options
export const createClientOptions = (outputChannel: vscode.OutputChannel): LanguageClientOptions => {
    const config = vscode.workspace.getConfiguration('kson');

    return {
        documentSelector: [
            {scheme: 'file', language: 'kson'},
            {scheme: 'untitled', language: 'kson'}
        ],
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.kson')
        },
        initializationOptions: {
            settings: {
                kson: {
                    format: {
                        indentSize: config.get('editor.indentSize', 2),
                        insertSpaces: config.get('editor.insertSpaces', true),
                    }
                }
            }
        },
        outputChannel,
        errorHandler: {
            error: () => ({action: ErrorAction.Continue}),
            closed: () => ({action: CloseAction.DoNotRestart})
        }
    };
}
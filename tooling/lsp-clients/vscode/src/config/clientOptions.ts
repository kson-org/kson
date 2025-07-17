import vscode from 'vscode';
import {
    LanguageClientOptions,
    ErrorAction,
    CloseAction,
} from 'vscode-languageclient';

// Create shared client options
export const createClientOptions = (outputChannel: vscode.OutputChannel): LanguageClientOptions => {

    return {
        documentSelector: [
            {scheme: 'file', language: 'kson'},
            {scheme: 'untitled', language: 'kson'}
        ],
        synchronize: {
            /**
             * TODO - Even though this setting is deprecated it is the easiest way to get configuration going.
             * We should find a way in the future to replace this with the /pull model.
             */
            configurationSection: 'kson',
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.kson')
        },
        outputChannel,
        errorHandler: {
            error: () => ({action: ErrorAction.Continue}),
            closed: () => ({action: CloseAction.DoNotRestart})
        }
    };
}
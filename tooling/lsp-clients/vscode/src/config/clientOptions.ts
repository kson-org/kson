import vscode from 'vscode';
import {
    LanguageClientOptions,
    ErrorAction,
    CloseAction,
    DocumentSelector,
} from 'vscode-languageclient';
import { getLanguageConfiguration } from './languageConfig';

// Create shared client options
export const createClientOptions = (outputChannel: vscode.OutputChannel): LanguageClientOptions => {
    const { languageIds, fileExtensions } = getLanguageConfiguration();

    // Build document selector for all supported language IDs
    const documentSelector: DocumentSelector = languageIds.flatMap(languageId => [
        { scheme: 'file', language: languageId },
        { scheme: 'untitled', language: languageId }
    ]);

    // Build file watcher pattern for all file extensions
    const fileWatcherPattern = fileExtensions.length > 1
        ? `**/*.{${fileExtensions.join(',')}}`
        : `**/*.${fileExtensions[0]}`;

    return {
        documentSelector,
        synchronize: {
            /**
             * TODO - Even though this setting is deprecated it is the easiest way to get configuration going.
             * We should find a way in the future to replace this with the /pull model.
             */
            configurationSection: 'kson',
            fileEvents: vscode.workspace.createFileSystemWatcher(fileWatcherPattern)
        },
        outputChannel,
        errorHandler: {
            error: () => ({action: ErrorAction.Continue}),
            closed: () => ({action: CloseAction.DoNotRestart})
        }
    };
}
import vscode from 'vscode';
import {
    LanguageClientOptions,
    ErrorAction,
    CloseAction,
    DocumentSelector,
} from 'vscode-languageclient';
import { getLanguageConfiguration } from './languageConfig';
import type { KsonInitializationOptions } from 'kson-language-server';

const MAX_RESTART_COUNT = 3;

/**
 * Create shared client options.
 * @param outputChannel The output channel for logging
 * @param initializationOptions Optional initialization options including bundled schemas
 */
export const createClientOptions = (
    outputChannel: vscode.OutputChannel,
    initializationOptions?: KsonInitializationOptions
): LanguageClientOptions => {
    const { languageIds, fileExtensions } = getLanguageConfiguration();
    let restartCount = 0;

    // Build document selector for all supported language IDs
    const documentSelector: DocumentSelector = languageIds.flatMap(languageId => [
        { scheme: 'file', language: languageId },
        { scheme: 'untitled', language: languageId },
        { scheme: 'bundled', language: languageId }
    ]);

    // Build file watcher pattern for all file extensions
    const fileWatcherPattern = fileExtensions.length > 1
        ? `**/*.{${fileExtensions.join(',')}}`
        : `**/*.${fileExtensions[0]}`;

    return {
        documentSelector,
        initializationOptions,
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
            closed: () => {
                restartCount++;
                if (restartCount <= MAX_RESTART_COUNT) {
                    return {action: CloseAction.Restart};
                }
                // NOTE: restartCount is never reset, so three crashes across an
                // entire session (even hours apart) will permanently disable
                // restart.  This is acceptable for now — a reload clears it.
                vscode.window.showErrorMessage(
                    'KSON Language Server crashed repeatedly. Please reload the window.'
                );
                return {action: CloseAction.DoNotRestart};
            }
        }
    };
}
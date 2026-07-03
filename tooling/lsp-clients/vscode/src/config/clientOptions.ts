import vscode from 'vscode';
import {
    LanguageClientOptions,
    ErrorAction,
    CloseAction,
    DocumentSelector,
} from 'vscode-languageclient';
import { getLanguageConfiguration } from './languageConfig';
import { CommandType, type KsonInitializationOptions } from 'kson-language-server';

const MAX_RESTART_COUNT = 3;

/** Wire ids for the four formatting commands are `${distributionId}.${CommandType}`. */
const FORMAT_COMMAND_TYPES: CommandType[] = [
    CommandType.PLAIN_FORMAT,
    CommandType.DELIMITED_FORMAT,
    CommandType.COMPACT_FORMAT,
    CommandType.CLASSIC_FORMAT,
];

/** Match a format command by its wire-id suffix, staying agnostic to the distribution id. */
function isFormatCommandId(command: string): boolean {
    return FORMAT_COMMAND_TYPES.some(type => command.endsWith(`.${type}`));
}

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
            fileEvents: vscode.workspace.createFileSystemWatcher(fileWatcherPattern)
        },
        outputChannel,
        middleware: {
            // The editor's indentation (the "Spaces/Tabs" status-bar toggle) is the source of
            // truth for formatting. DocumentFormattingParams carry it automatically, but the
            // CodeLens format buttons run commands with no such params — inject it here so the
            // buttons honor the editor setting too.
            executeCommand: (command, args, next) => {
                if (isFormatCommandId(command)) {
                    const options = vscode.window.activeTextEditor?.options;
                    const insertSpaces = typeof options?.insertSpaces === 'boolean' ? options.insertSpaces : true;
                    const tabSize = typeof options?.tabSize === 'number' ? options.tabSize : 2;
                    args = [{ ...(args?.[0] ?? {}), insertSpaces, tabSize }, ...args.slice(1)];
                }
                return next(command, args);
            }
        },
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
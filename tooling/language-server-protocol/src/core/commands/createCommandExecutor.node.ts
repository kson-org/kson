import {Connection} from 'vscode-languageserver';
import {KsonDocumentsManager} from '../document/KsonDocumentsManager.js';
import {FormattingService} from '../features/FormattingService.js';
import type {KsonSettings} from '../KsonSettings.js';
import {CommandExecutor} from './CommandExecutor.node.js';

/**
 * Node.js-specific command executor factory.
 * Creates CommandExecutor with full file system support.
 */
export function createCommandExecutor(
    connection: Connection,
    documentManager: KsonDocumentsManager,
    formattingService: FormattingService,
    getConfiguration: () => Required<KsonSettings>,
    workspaceRoot: string | null = null
): CommandExecutor {
    return new CommandExecutor(
        connection,
        documentManager,
        formattingService,
        getConfiguration,
        workspaceRoot
    );
}
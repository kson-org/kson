import {
    Connection,
    InitializeResult,
    TextDocumentSyncKind,
    ServerCapabilities,
    DiagnosticRegistrationOptions,
} from 'vscode-languageserver';
import {KsonDocumentsManager} from './core/document/KsonDocumentsManager.js';
import {KsonTextDocumentService} from './core/services/KsonTextDocumentService.js';
import {KSON_LEGEND} from './core/features/SemanticTokensService.js';
import {getAllCommandIds} from './core/commands/CommandType.js';
import { ksonSettingsWithDefaults } from './core/KsonSettings.js';

/**
 * Core Kson Language Server implementation.
 *
 * @param connection
 */
export function startKsonServer(connection: Connection): void {
    // Initialize core components
    const documentManager = new KsonDocumentsManager();
    const textDocumentService = new KsonTextDocumentService(documentManager);

    // Setup connection event handlers
    connection.onInitialize((): InitializeResult => {
        const capabilities: ServerCapabilities = {
            // Document synchronization
            textDocumentSync: TextDocumentSyncKind.Full,

            // Semantic tokens
            semanticTokensProvider: {
                legend: KSON_LEGEND,
                full: true,
            },

            // Document formatting
            documentFormattingProvider: true,

            // Diagnostics (pull model preferred)
            diagnosticProvider: {
                identifier: 'kson',
                interFileDependencies: false,
                workspaceDiagnostics: false
            } as DiagnosticRegistrationOptions,

            // Code lens
            codeLensProvider: {
                resolveProvider: false
            },

            // Execute command
            executeCommandProvider: {
                commands: getAllCommandIds()
            },

            // Document symbols
            documentSymbolProvider: true,

            workspace: {
                workspaceFolders: {
                    supported: true,
                    changeNotifications: true
                }
            }
        };

        return {capabilities};
    });

    // Handle configuration changes
    connection.onDidChangeConfiguration((change) => {
        // Update the text document service with new configuration
        const configuration = ksonSettingsWithDefaults(change.settings);
        textDocumentService.updateConfiguration(configuration);

        connection.console.info('Configuration updated');
    });

    // Setup document handling
    documentManager.listen(connection);

    // Connect services
    textDocumentService.connect(connection);

    // Start listening for requests
    connection.listen();
    connection.console.info('Kson Language Server started and listening');
}
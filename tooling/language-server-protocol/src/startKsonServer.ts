import {
    Connection,
    InitializeResult,
    TextDocumentSyncKind,
    ServerCapabilities,
    DiagnosticRegistrationOptions,
} from 'vscode-languageserver';
import { URI } from 'vscode-uri'
import {KsonDocumentsManager} from './core/document/KsonDocumentsManager.js';
import {KsonTextDocumentService} from './core/services/KsonTextDocumentService.js';
import {KSON_LEGEND} from './core/features/SemanticTokensService.js';
import {getAllCommandIds} from './core/commands/CommandType.js';
import { ksonSettingsWithDefaults } from './core/KsonSettings.js';
import {SchemaProvider} from './core/schema/SchemaProvider.js';
import {SCHEMA_CONFIG_FILENAME} from "./core/schema/SchemaConfig";
import {CommandExecutorFactory} from "./core/commands/CommandExecutorFactory";

type SchemaProviderFactory = (
    workspaceRootUri: URI | undefined,
    logger: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
) => Promise<SchemaProvider | undefined>;

/**
 * Core Kson Language Server implementation.
 *
 * @param connection The LSP connection
 * @param createSchemaProvider Factory function to create the appropriate schema provider for the environment
 * @param createCommandExecutor Factory function to create the appropriate command executor for the environment
 */
export function startKsonServer(
    connection: Connection,
    createSchemaProvider: SchemaProviderFactory,
    createCommandExecutor: CommandExecutorFactory
): void {
    // Variables to store state during initialization
    let workspaceRootUri: URI | undefined;

    // Create logger that uses the connection
    const logger = {
        info: (message: string) => connection.console.info(message),
        warn: (message: string) => connection.console.warn(message),
        error: (message: string) => connection.console.error(message)
    };

    // Initialize core components (documentManager will be created after onInitialize)
    let documentManager: KsonDocumentsManager;
    let textDocumentService: KsonTextDocumentService;

    // Setup connection event handlers
    connection.onInitialize(async (params): Promise<InitializeResult> => {
        // Capture workspace root from initialization parameters
        const stringUri = params.workspaceFolders?.[0]?.uri
        if (stringUri) {
            workspaceRootUri = URI.parse(stringUri)
        }

        // Create the appropriate schema provider for this environment
        const schemaProvider = await createSchemaProvider(workspaceRootUri, logger);

        // Now that we have workspace root and schema provider, create the document manager
        documentManager = new KsonDocumentsManager(schemaProvider);

        // Extract workspace root path from URI if available
        const workspaceRoot = workspaceRootUri ? workspaceRootUri.fsPath : null;

        textDocumentService = new KsonTextDocumentService(
            documentManager,
            createCommandExecutor,
            workspaceRoot
        );

        // Setup document handling and connect services
        documentManager.listen(connection);
        textDocumentService.connect(connection);

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

            // Document highlight
            documentHighlightProvider: true,

            // Document symbols
            documentSymbolProvider: true,

            // Hover information
            hoverProvider: true,

            // Go to definition
            definitionProvider: true,

            workspace: {
                workspaceFolders: {
                    supported: true,
                    changeNotifications: true
                }
            },

            completionProvider: {
                triggerCharacters: ['"', "'", ':', ',', '{', '[', '\n'],
                resolveProvider: false
            }
        };

        return {capabilities};
    });

    // Called after initialization is complete
    connection.onInitialized(() => {
        logger.info('Kson Language Server initialized');
    });

    // Handle custom request to get schema information for a document
    connection.onRequest('kson/getDocumentSchema', (params: { uri: string }) => {
        try {
            const schemaDocument = documentManager.get(params.uri)?.getSchemaDocument();
            if (schemaDocument) {
                const schemaUri = schemaDocument.uri;
                // Extract readable path from URI
                const schemaPath = schemaUri.startsWith('file://') ? URI.parse(schemaUri).fsPath : schemaUri;
                return {
                    schemaUri,
                    schemaPath,
                    hasSchema: true
                };
            }
            return {
                schemaUri: undefined,
                schemaPath: undefined,
                hasSchema: false
            };
        } catch (error) {
            logger.error(`Error getting schema for document: ${error}`);
            return {
                schemaUri: undefined,
                schemaPath: undefined,
                hasSchema: false
            };
        }
    });

    // Handle changes to watched files
    connection.onDidChangeWatchedFiles((params) => {
        for (const change of params.changes) {
            if (change.uri.endsWith(SCHEMA_CONFIG_FILENAME)) {
                logger.info('Schema configuration file changed, reloading...');
                documentManager.reloadSchemaConfiguration();
                // Refresh all open documents with the updated schemas
                documentManager.refreshDocumentSchemas();
                // Notify client that schema configuration changed so it can update UI (e.g., status bar)
                connection.sendNotification('kson/schemaConfigurationChanged');
                // Rerun diagnostics for open files, so we immediately see errors of schema
                connection.sendRequest('workspace/diagnostic/refresh')
            }
        }
    });

    // Handle configuration changes
    connection.onDidChangeConfiguration((change) => {
        // Update the text document service with new configuration
        const configuration = ksonSettingsWithDefaults(change.settings);
        textDocumentService.updateConfiguration(configuration);

        connection.console.info('Configuration updated');
    });

    // Start listening for requests
    connection.listen();
    connection.console.info('Kson Language Server started and listening');
}
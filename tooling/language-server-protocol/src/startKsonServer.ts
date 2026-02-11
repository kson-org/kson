import {
    Connection,
    InitializeResult,
    TextDocumentSyncKind,
    ServerCapabilities,
    DiagnosticRegistrationOptions,
} from 'vscode-languageserver';
import { URI } from 'vscode-uri'
import {KsonDocumentsManager} from './core/document/KsonDocumentsManager.js';
import {isKsonSchemaDocument} from './core/document/KsonSchemaDocument.js';
import {KsonTextDocumentService} from './core/services/KsonTextDocumentService.js';
import {KSON_LEGEND} from './core/features/SemanticTokensService.js';
import {getAllCommandIds} from './core/commands/CommandType.js';
import { ksonSettingsWithDefaults } from './core/KsonSettings.js';
import {SchemaProvider} from './core/schema/SchemaProvider.js';
import {BundledSchemaProvider, BundledSchemaConfig, BundledMetaSchemaConfig} from './core/schema/BundledSchemaProvider.js';
import {CompositeSchemaProvider} from './core/schema/CompositeSchemaProvider.js';
import {SCHEMA_CONFIG_FILENAME} from "./core/schema/SchemaConfig";
import {CommandExecutorFactory} from "./core/commands/CommandExecutorFactory";

/**
 * Initialization options passed from the VSCode client.
 */
export interface KsonInitializationOptions {
    /** Bundled schemas to be loaded (matched by file extension) */
    bundledSchemas?: BundledSchemaConfig[];
    /** Bundled metaschemas to be loaded (matched by document $schema content) */
    bundledMetaSchemas?: BundledMetaSchemaConfig[];
    /** Whether bundled schemas are enabled */
    enableBundledSchemas?: boolean;
}

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
    let bundledSchemaProvider: BundledSchemaProvider | undefined;

    // Setup connection event handlers
    connection.onInitialize(async (params): Promise<InitializeResult> => {
        // Capture workspace root from initialization parameters
        const stringUri = params.workspaceFolders?.[0]?.uri
        if (stringUri) {
            workspaceRootUri = URI.parse(stringUri)
        }

        // Extract bundled schema configuration from initialization options
        const initOptions = params.initializationOptions as KsonInitializationOptions | undefined;
        const bundledSchemas = initOptions?.bundledSchemas ?? [];
        const bundledMetaSchemas = initOptions?.bundledMetaSchemas ?? [];
        const enableBundledSchemas = initOptions?.enableBundledSchemas ?? true;

        // Create the appropriate schema provider for this environment (file system or no-op)
        const fileSystemSchemaProvider = await createSchemaProvider(workspaceRootUri, logger);

        // Create bundled schema provider if schemas or metaschemas are configured
        let schemaProvider: SchemaProvider | undefined;
        if (bundledSchemas.length > 0 || bundledMetaSchemas.length > 0) {
            bundledSchemaProvider = new BundledSchemaProvider({
                schemas: bundledSchemas,
                metaSchemas: bundledMetaSchemas,
                enabled: enableBundledSchemas,
                logger
            });

            // Create composite provider: file system takes priority over bundled
            const providers: SchemaProvider[] = [];
            if (fileSystemSchemaProvider) {
                providers.push(fileSystemSchemaProvider);
            }
            providers.push(bundledSchemaProvider);

            schemaProvider = new CompositeSchemaProvider(providers, logger);
            logger.info(`Created composite schema provider with ${providers.length} providers`);
        } else {
            schemaProvider = fileSystemSchemaProvider;
        }

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
            const doc = documentManager.get(params.uri);
            const schemaDocument = doc
                ? isKsonSchemaDocument(doc) ? doc.getMetaSchemaDocument() : doc.getSchemaDocument()
                : undefined;
            if (schemaDocument) {
                const schemaUri = schemaDocument.uri;
                // Check if this is a bundled schema (uses bundled:// scheme)
                const isBundled = schemaUri.startsWith('bundled://');
                // Extract readable path from URI
                const schemaPath = schemaUri.startsWith('file://') ? URI.parse(schemaUri).fsPath : schemaUri;
                return {
                    schemaUri,
                    schemaPath,
                    hasSchema: true,
                    isBundled
                };
            }
            return {
                schemaUri: undefined,
                schemaPath: undefined,
                hasSchema: false,
                isBundled: false
            };
        } catch (error) {
            logger.error(`Error getting schema for document: ${error}`);
            return {
                schemaUri: undefined,
                schemaPath: undefined,
                hasSchema: false,
                isBundled: false
            };
        }
    });

    /**
     * Refresh all documents with updated schemas, notify the client,
     * and trigger diagnostic refresh.
     */
    function notifySchemaChange(): void {
        documentManager.refreshDocumentSchemas();
        connection.sendNotification('kson/schemaConfigurationChanged');
        connection.sendRequest('workspace/diagnostic/refresh');
    }

    // Handle changes to watched files
    connection.onDidChangeWatchedFiles((params) => {
        const schemaProvider = documentManager.getSchemaProvider();
        let schemaChanged = false;

        for (const change of params.changes) {
            // Check if schema configuration file or any schema file changed
            if (change.uri.endsWith(SCHEMA_CONFIG_FILENAME)) {
                logger.info('Schema configuration file changed, reloading...');
                documentManager.reloadSchemaConfiguration();
                schemaChanged = true;
            } else if (schemaProvider.isSchemaFile(change.uri)) {
                logger.info('Schema file changed, reloading schemas...');
                schemaChanged = true;
            }
        }

        if (schemaChanged) {
            notifySchemaChange();
        }
    });

    // Handle configuration changes
    connection.onDidChangeConfiguration((change) => {
        // Update the text document service with new configuration
        const configuration = ksonSettingsWithDefaults(change.settings);
        textDocumentService.updateConfiguration(configuration);

        // Check if bundled schema setting changed
        if (bundledSchemaProvider && change.settings?.kson?.enableBundledSchemas !== undefined) {
            const enabled = change.settings.kson.enableBundledSchemas;
            bundledSchemaProvider.setEnabled(enabled);
            notifySchemaChange();
        }

        connection.console.info('Configuration updated');
    });

    // Start listening for requests
    connection.listen();
    connection.console.info('Kson Language Server started and listening');
}
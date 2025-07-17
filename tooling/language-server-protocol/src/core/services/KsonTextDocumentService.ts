import {
    Connection,
    SemanticTokens,
    TextEdit,
    SemanticTokensParams,
    DocumentFormattingParams,
    DocumentDiagnosticParams,
    DocumentDiagnosticReport,
} from 'vscode-languageserver';
import {KsonDocumentsManager} from '../document/KsonDocumentsManager.js';
import {FormattingService} from '../features/FormattingService.js';
import {DiagnosticService} from '../features/DiagnosticService.js';
import {SemanticTokensService} from '../features/SemanticTokensService.js';
import {KsonSettings, ksonSettingsWithDefaults} from '../KsonSettings.js';

/**
 * This is the coordinator for all the service classes.
 */
export class KsonTextDocumentService {
    private connection!: Connection;

    private formattingService: FormattingService;
    private diagnosticService: DiagnosticService;
    private semanticTokensService: SemanticTokensService;
    private configuration: Required<KsonSettings>;

    constructor(private documentManager: KsonDocumentsManager) {
        this.formattingService = new FormattingService();
        this.diagnosticService = new DiagnosticService();
        this.semanticTokensService = new SemanticTokensService();
        
        // Default configuration
        this.configuration = ksonSettingsWithDefaults();
    }

    /**
     * Connects the document service to a given {@link Connection} instance.
     * This method initializes the language features (e.g., formatting, semantic tokens, diagnostics)
     * provided by the {@link KsonTextDocumentService} by binding them to the LSP connection.
     *
     * @param connection - The {@link Connection} instance used to register language features and log messages.
     *
     * @remarks
     * This method should be called after creating an instance of {@link KsonTextDocumentService}.
     * It ensures that the necessary language server features are set up and ready to handle client requests.
     */
    connect(connection: Connection): void {
        this.connection = connection;

        this.setupLanguageFeatures();
    }

    /**
     * Updates the configuration for the text document service.
     * @param config - The new configuration to apply
     */
    updateConfiguration(config: Required<KsonSettings>): void {
        this.configuration = config;
        this.connection.console.info('Text document service configuration updated');
    }

    private setupLanguageFeatures(): void {
        /**
         * Bind the {@link Connection} to specific methods that handle the requests.
         */
        this.connection.onDocumentFormatting(this.onDocumentFormatting.bind(this));
        this.connection.languages.semanticTokens.on(this.onSemanticTokensFull.bind(this));
        this.connection.languages.diagnostics.on(this.onDiagnostic.bind(this));
    }


    private async onSemanticTokensFull(params: SemanticTokensParams): Promise<SemanticTokens> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return {data: []};
            }
            return this.semanticTokensService.getSemanticTokens(document);
        } catch (error) {
            this.connection.console.error(`Error providing semantic tokens: ${error}`);
            return {data: []};
        }
    }

    private async onDocumentFormatting(params: DocumentFormattingParams): Promise<TextEdit[] | null> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return [];
            }
            return this.formattingService.formatDocument(document, this.configuration.kson.formatOptions);
        } catch (error) {
            this.connection.console.error(`Error formatting document: ${error}`);
            return [];
        }
    }

    private async onDiagnostic(params: DocumentDiagnosticParams): Promise<DocumentDiagnosticReport> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            return this.diagnosticService.createDocumentDiagnosticReport(document);
        } catch (error) {
            this.connection.console.error(`Error providing diagnostics: ${error}`);
            return {
                kind: 'full',
                items: []
            };
        }
    }
} 
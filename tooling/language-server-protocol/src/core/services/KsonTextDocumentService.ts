import {
    Connection,
    SemanticTokens,
    TextEdit,
    SemanticTokensParams,
    DocumentFormattingParams,
    DocumentDiagnosticParams,
    DocumentDiagnosticReport,
    DocumentSymbol,
    DocumentSymbolParams,
    CodeLens,
    CodeLensParams,
    ExecuteCommandParams,
} from 'vscode-languageserver';
import {KsonDocumentsManager} from '../document/KsonDocumentsManager.js';
import {FormattingService} from '../features/FormattingService.js';
import {DiagnosticService} from '../features/DiagnosticService.js';
import {SemanticTokensService} from '../features/SemanticTokensService.js';
import {CodeLensService} from '../features/CodeLensService.js';
import {DocumentSymbolService} from '../features/DocumentSymbolService.js';
import {CommandExecutor} from '../commands/CommandExecutor.js';
import {KsonSettings, ksonSettingsWithDefaults} from '../KsonSettings.js';
import {IndexedDocumentSymbols} from "../features/IndexedDocumentSymbols";

/**
 * This is the coordinator for all the service classes.
 */
export class KsonTextDocumentService {
    private connection!: Connection;

    private readonly formattingService: FormattingService;
    private diagnosticService: DiagnosticService;
    private semanticTokensService: SemanticTokensService;
    private codeLensService: CodeLensService;
    private documentSymbolService: DocumentSymbolService;
    private commandExecutor!: CommandExecutor;
    private configuration: Required<KsonSettings>;

    constructor(private documentManager: KsonDocumentsManager) {
        this.formattingService = new FormattingService();
        this.diagnosticService = new DiagnosticService();
        this.semanticTokensService = new SemanticTokensService();
        this.codeLensService = new CodeLensService();
        this.documentSymbolService = new DocumentSymbolService();

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

        // Initialize CommandExecutor with the connection
        this.commandExecutor = new CommandExecutor(
            this.connection,
            this.documentManager,
            this.formattingService,
            () => this.configuration
        );

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
        this.connection.onCodeLens(this.onCodeLens.bind(this));
        this.connection.onExecuteCommand(this.onExecuteCommand.bind(this));
        this.connection.onDocumentSymbol(this.onDocumentSymbol.bind(this));
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

    private async onCodeLens(params: CodeLensParams): Promise<CodeLens[]> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return [];
            }
            return this.codeLensService.getCodeLenses(document);
        } catch (error) {
            this.connection.console.error(`Error providing code lenses: ${error}`);
            return [];
        }
    }

    private async onExecuteCommand(params: ExecuteCommandParams): Promise<any> {
        try {
            return await this.commandExecutor.execute(params);
        } catch (error) {
            this.connection.console.error(`Error executing command: ${error}`);
            this.connection.window.showErrorMessage(`Command execution failed: ${error}`);
        }
    }
    private async onDocumentSymbol(params: DocumentSymbolParams): Promise<DocumentSymbol[]> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return [];
            }
            const documentSymbols = this.documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue)
            document.setSymbolsWithIndex(new IndexedDocumentSymbols(documentSymbols))
            return documentSymbols
        } catch (error) {
            this.connection.console.error(`Error providing document symbols: ${error}`);
            return [];
        }
    }
}
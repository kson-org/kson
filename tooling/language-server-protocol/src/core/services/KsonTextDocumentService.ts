import {
    Connection,
    SemanticTokens,
    TextEdit,
    SemanticTokensParams,
    DocumentFormattingParams,
    DocumentDiagnosticParams,
    DocumentDiagnosticReport,
    DocumentHighlight,
    DocumentHighlightParams,
    DocumentSymbol,
    DocumentSymbolParams,
    CodeLens,
    CodeLensParams,
    ExecuteCommandParams,
    Hover,
    HoverParams,
    CompletionList,
    CompletionParams,
    DefinitionParams,
    DefinitionLink,
} from 'vscode-languageserver';
import {KsonDocumentsManager} from '../document/KsonDocumentsManager.js';
import {FormattingService} from '../features/FormattingService.js';
import {DiagnosticService} from '../features/DiagnosticService.js';
import {SemanticTokensService} from '../features/SemanticTokensService.js';
import {CodeLensService} from '../features/CodeLensService.js';
import {DocumentHighlightService} from '../features/DocumentHighlightService.js';
import {DocumentSymbolService} from '../features/DocumentSymbolService.js';
import {HoverService} from '../features/HoverService.js';
import {CompletionService} from '../features/CompletionService.js';
import {DefinitionService} from '../features/DefinitionService.js';
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
    private documentHighlightService: DocumentHighlightService;
    private documentSymbolService: DocumentSymbolService;
    private hoverService: HoverService;
    private completionService: CompletionService;
    private definitionService: DefinitionService;
    private commandExecutor!: CommandExecutor;
    private configuration: Required<KsonSettings>;

    constructor(private documentManager: KsonDocumentsManager) {
        this.formattingService = new FormattingService();
        this.diagnosticService = new DiagnosticService();
        this.semanticTokensService = new SemanticTokensService();
        this.codeLensService = new CodeLensService();
        this.documentHighlightService = new DocumentHighlightService();
        this.documentSymbolService = new DocumentSymbolService();
        this.hoverService = new HoverService();
        this.completionService = new CompletionService();
        this.definitionService = new DefinitionService();

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
        this.connection.onDocumentHighlight(this.onDocumentHighlight.bind(this));
        this.connection.onDocumentSymbol(this.onDocumentSymbol.bind(this));
        this.connection.onHover(this.onHover.bind(this));
        this.connection.onCompletion(this.onCompletion.bind(this));
        this.connection.onDefinition(this.onDefinition.bind(this));
    }


    private async onSemanticTokensFull(params: SemanticTokensParams): Promise<SemanticTokens> {
        try {
            this.connection.console.info(`Semantic tokens requested for ${params.textDocument.uri}`);
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return {data: []};
            }
            const result = this.semanticTokensService.getSemanticTokens(document);
            this.connection.console.info(`Semantic tokens result: ${result.data.length} tokens`);
            return result;
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
            this.connection.console.info(`Diagnostics requested for ${params.textDocument.uri}`);
            const document = this.documentManager.get(params.textDocument.uri);
            const result = this.diagnosticService.createDocumentDiagnosticReport(document);
            this.connection.console.info(`Diagnostics result: ${JSON.stringify(result)}`);
            return result;
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

    private async onDocumentHighlight(params: DocumentHighlightParams): Promise<DocumentHighlight[]> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return [];
            }
            return this.documentHighlightService.getDocumentHighlights(document, params.position);
        } catch (error) {
            this.connection.console.error(`Error providing document highlights: ${error}`);
            return [];
        }
    }

    private async onDocumentSymbol(params: DocumentSymbolParams): Promise<DocumentSymbol[]> {
        try {
            this.connection.console.info(`Document symbols requested for ${params.textDocument.uri}`);
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return [];
            }
            const documentSymbols = this.documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue)
            document.setSymbolsWithIndex(new IndexedDocumentSymbols(documentSymbols))
            this.connection.console.info(`Document symbols result: ${documentSymbols.length} symbols`);
            return documentSymbols
        } catch (error) {
            this.connection.console.error(`Error providing document symbols: ${error}`);
            return [];
        }
    }

    private async onHover(params: HoverParams): Promise<Hover | null> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return null;
            }
            const result = this.hoverService.getHover(document, params.position);
            this.connection.console.info(`Hover result: ${JSON.stringify(result)}`);
            return result;
        } catch (error) {
            this.connection.console.error(`Error providing hover info: ${error}`);
            return null;
        }
    }

    private async onCompletion(params: CompletionParams): Promise<CompletionList | null> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return null;
            }
            const result = this.completionService.getCompletions(document, params.position);
            this.connection.console.info(`Completion result: ${JSON.stringify(result)}`);
            return result;
        } catch (error) {
            this.connection.console.error(`Error providing completions: ${error}`);
            return null;
        }
    }

    private async onDefinition(params: DefinitionParams): Promise<DefinitionLink[] | null> {
        try {
            const document = this.documentManager.get(params.textDocument.uri);
            if (!document) {
                return null;
            }
            const result = this.definitionService.getDefinition(document, params.position);
            this.connection.console.info(`Definition result: ${JSON.stringify(result)}`);
            return result;
        } catch (error) {
            this.connection.console.error(`Error providing definition: ${error}`);
            return null;
        }
    }
}
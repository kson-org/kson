import {
    ServerRequestHandler,
    DocumentFormattingParams,
    TextEdit,
    DidOpenTextDocumentParams,
    NotificationHandler,
    Disposable,
    SemanticTokensParams,
    SemanticTokens,
    DocumentDiagnosticParams,
    DocumentDiagnosticReport,
    DidChangeTextDocumentParams, DidCloseTextDocumentParams, WillSaveTextDocumentParams, DidSaveTextDocumentParams,
    CodeLensParams,
    CodeLens,
    ExecuteCommandParams,
    DocumentHighlight,
    DocumentHighlightParams,
    DocumentSymbol,
    DocumentSymbolParams,
    SymbolInformation,
    Hover,
    HoverParams,
    CompletionParams,
    CompletionList, DefinitionParams,
    FoldingRange,
    FoldingRangeParams,
    SelectionRange,
    SelectionRangeParams
} from "vscode-languageserver";
import {BoilerplateConnectionStub} from "./BoilerplateConnectionStub";
import {Languages} from "vscode-languageserver/lib/common/server";
import {Definition, DefinitionLink, Location} from "vscode-languageserver-protocol";
import {Position} from "vscode-languageserver-types";

/**
 * A stub implementation of the `Connection` interface for testing purposes.
 *
 * This class extends {@link BoilerplateConnectionStub}, which provides a base implementation
 * of the `Connection` interface where all methods throw an error by default.
 *
 * `ConnectionStub` overrides the specific methods from `Connection` that are required by
 * the services under test (e.g., `onDocumentFormatting`, `onDidOpenTextDocument`).
 * These overridden methods store the provided handlers in public properties (e.g., `formattingHandler`),
 * allowing tests to invoke them directly and verify the behavior of the services.
 *
 * This setup allows to test language features by simulating client-side events and requests.
 */
export class ConnectionStub extends BoilerplateConnectionStub {
    public formattingHandler: ServerRequestHandler<DocumentFormattingParams, TextEdit[] | null | undefined, never, void>;
    public didOpenHandler: NotificationHandler<DidOpenTextDocumentParams>;
    public didChangeHandler: NotificationHandler<DidChangeTextDocumentParams>;
    public didCloseHandler: NotificationHandler<DidCloseTextDocumentParams>;
    public willSaveHandler: NotificationHandler<WillSaveTextDocumentParams>;
    public willSaveWaitUntilHandler: ServerRequestHandler<WillSaveTextDocumentParams, TextEdit[] | null | undefined, never, void>;
    public didSaveHandler: NotificationHandler<DidSaveTextDocumentParams>;
    public semanticTokensHandler: ServerRequestHandler<SemanticTokensParams, SemanticTokens, any, void>;
    public diagnosticsHandler: ServerRequestHandler<DocumentDiagnosticParams, DocumentDiagnosticReport, any, void>;
    public codeLensHandler: ServerRequestHandler<CodeLensParams, CodeLens[] | null | undefined, never, void>;
    public executeCommandHandler: ServerRequestHandler<ExecuteCommandParams, any | null | undefined, never, void>;
    public documentHighlightHandler: ServerRequestHandler<DocumentHighlightParams, DocumentHighlight[] | null | undefined, DocumentHighlight[], void>;
    public documentSymbolHandler: ServerRequestHandler<DocumentSymbolParams, SymbolInformation[] | DocumentSymbol[] | null | undefined, SymbolInformation[] | DocumentSymbol[], void>;
    public hoverHandler: ServerRequestHandler<HoverParams, Hover | null | undefined, never, void>;
    public completionHandler: ServerRequestHandler<CompletionParams, CompletionList | null | undefined, never, void>;
    public onDefinitionHandler: ServerRequestHandler<DefinitionParams, Definition | DefinitionLink[] | undefined | null, Location[] | DefinitionLink[], void>;
    public foldingRangeHandler: ServerRequestHandler<FoldingRangeParams, FoldingRange[] | null | undefined, FoldingRange[], void>;
    public selectionRangeHandler: ServerRequestHandler<SelectionRangeParams, SelectionRange[] | null | undefined, SelectionRange[], void>;

    languages: Languages;

    constructor() {
        super();

        // Initialize console with mock implementation
        this.console = {
            error: () => {},
            warn: () => {},
            info: () => {},
            log: () => {}
        } as any;
        this.languages = {
            semanticTokens: {
                on: (handler: ServerRequestHandler<SemanticTokensParams, SemanticTokens, any, void>) => {
                    this.semanticTokensHandler = handler;
                    return NOOP_DISPOSABLE;
                }
            },
            diagnostics: {
                on: (handler: ServerRequestHandler<DocumentDiagnosticParams, DocumentDiagnosticReport, any, void>) => {
                    this.diagnosticsHandler = handler;
                    return NOOP_DISPOSABLE;
                }
            }
        } as Languages;
        this.workspace = {
            getConfiguration: () => Promise.resolve({})
        } as any;
        this.client = {
            register: () => Promise.resolve()
        } as any;
    }

    override onDocumentFormatting(handler: ServerRequestHandler<DocumentFormattingParams, TextEdit[] | undefined | null, never, void>): Disposable {
        this.formattingHandler = handler;
        return NOOP_DISPOSABLE;
    }

    onDidOpenTextDocument(handler: NotificationHandler<DidOpenTextDocumentParams>): Disposable {
        this.didOpenHandler = handler;
        return NOOP_DISPOSABLE;
    }

    onDidChangeTextDocument(handler: NotificationHandler<DidChangeTextDocumentParams>): Disposable {
        this.didChangeHandler = handler;
        return NOOP_DISPOSABLE;
    }

    onDidCloseTextDocument(handler: NotificationHandler<DidCloseTextDocumentParams>): Disposable {
        this.didCloseHandler = handler;
        return NOOP_DISPOSABLE;
    }

    onWillSaveTextDocument(handler: NotificationHandler<WillSaveTextDocumentParams>): Disposable {
        this.willSaveHandler = handler;
        return NOOP_DISPOSABLE;
    }

    onWillSaveTextDocumentWaitUntil(handler: ServerRequestHandler<WillSaveTextDocumentParams, TextEdit[] | null | undefined, never, void>): Disposable {
        this.willSaveWaitUntilHandler = handler;
        return NOOP_DISPOSABLE;
    }

    onDidSaveTextDocument(handler: NotificationHandler<DidSaveTextDocumentParams>): Disposable {
        this.didSaveHandler = handler;
        return NOOP_DISPOSABLE;
    }

    override onCodeLens(handler: ServerRequestHandler<CodeLensParams, CodeLens[] | null | undefined, never, void>): Disposable {
        this.codeLensHandler = handler;
        return NOOP_DISPOSABLE;
    }

    override onExecuteCommand(handler: ServerRequestHandler<ExecuteCommandParams, any | null | undefined, never, void>): Disposable {
        this.executeCommandHandler = handler;
        return NOOP_DISPOSABLE;
    }

    override onDocumentHighlight(handler: ServerRequestHandler<DocumentHighlightParams, DocumentHighlight[] | null | undefined, DocumentHighlight[], void>): Disposable {
        this.documentHighlightHandler = handler;
        return NOOP_DISPOSABLE;
    }

    override onDocumentSymbol(handler: ServerRequestHandler<DocumentSymbolParams, SymbolInformation[] | DocumentSymbol[] | null | undefined, SymbolInformation[] | DocumentSymbol[], void>): Disposable {
        this.documentSymbolHandler = handler;
        return NOOP_DISPOSABLE;
    }

    override onHover(handler: ServerRequestHandler<HoverParams, Hover | null | undefined, never, void>): Disposable {
        this.hoverHandler = handler;
        return NOOP_DISPOSABLE;
    }

    override onCompletion(handler: ServerRequestHandler<CompletionParams, CompletionList | null | undefined, never, void>): Disposable {
        this.completionHandler = handler;
        return NOOP_DISPOSABLE;
    }

    override onDefinition(handler: ServerRequestHandler<DefinitionParams, Definition | DefinitionLink[] | undefined | null, Location[] | DefinitionLink[], void>): Disposable {
        this.onDefinitionHandler = handler;
        return NOOP_DISPOSABLE;
    }


    override onFoldingRanges(handler: ServerRequestHandler<FoldingRangeParams, FoldingRange[] | null | undefined, FoldingRange[], void>): Disposable {
        this.foldingRangeHandler = handler;
        return NOOP_DISPOSABLE;
    }

    override onSelectionRanges(handler: ServerRequestHandler<SelectionRangeParams, SelectionRange[] | null | undefined, SelectionRange[], void>): Disposable {
        this.selectionRangeHandler = handler;
        return NOOP_DISPOSABLE;
    }

    async requestFormatting(uri: string, tabSize = 2, insertSpaces = true) {
        return this.formattingHandler(
            {textDocument: {uri}, options: {tabSize, insertSpaces}},
            {} as any, {} as any, undefined
        );
    }

    async requestSemanticTokens(uri: string) {
        return this.semanticTokensHandler(
            {textDocument: {uri}},
            {} as any, {} as any, undefined
        );
    }

    async requestDiagnostics(uri: string) {
        return this.diagnosticsHandler(
            {textDocument: {uri}},
            {} as any, {} as any, undefined
        );
    }

    async requestCodeLens(uri: string) {
        return this.codeLensHandler(
            {textDocument: {uri}},
            {} as any, {} as any, undefined
        );
    }

    async requestHover(uri: string, position: Position) {
        return this.hoverHandler(
            {textDocument: {uri}, position},
            {} as any, {} as any, undefined
        );
    }

    async requestCompletion(uri: string, position: Position) {
        return this.completionHandler(
            {textDocument: {uri}, position},
            {} as any, {} as any, undefined
        );
    }

    async requestDefinition(uri: string, position: Position) {
        return this.onDefinitionHandler(
            {textDocument: {uri}, position},
            {} as any, {} as any, undefined
        );
    }

    async requestDocumentHighlight(uri: string, position: Position) {
        return this.documentHighlightHandler(
            {textDocument: {uri}, position},
            {} as any, {} as any, undefined
        );
    }

    async requestDocumentSymbol(uri: string) {
        return this.documentSymbolHandler(
            {textDocument: {uri}},
            {} as any, {} as any, undefined
        );
    }

    async requestFoldingRanges(uri: string) {
        return this.foldingRangeHandler(
            {textDocument: {uri}},
            {} as any, {} as any, undefined
        );
    }

    async requestSelectionRanges(uri: string, positions: Position[]) {
        return this.selectionRangeHandler(
            {textDocument: {uri}, positions},
            {} as any, {} as any, undefined
        );
    }
}

const NOOP_DISPOSABLE: Disposable = {
    dispose: () => {
    }
};

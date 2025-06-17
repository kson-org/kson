import {
    InitializeParams,
    InitializeResult,
    CompletionParams,
    CompletionList,
    DocumentFormattingParams,
    TextEdit,
    DidOpenTextDocumentParams,
    DidChangeTextDocumentParams,
    DidCloseTextDocumentParams,
    Connection,
    _,
    _Languages,
    _Notebooks,
    _RemoteWindow,
    _RemoteWorkspace,
    CodeAction,
    CodeActionParams,
    CodeLens,
    CodeLensParams,
    ColorInformation,
    ColorPresentation,
    ColorPresentationParams,
    Command,
    CompletionItem,
    Declaration,
    DeclarationLink,
    DeclarationParams,
    Definition,
    DefinitionLink,
    DefinitionParams,
    DidChangeConfigurationParams,
    DidChangeWatchedFilesParams,
    DidSaveTextDocumentParams,
    Disposable,
    DocumentColorParams,
    DocumentHighlight,
    DocumentHighlightParams,
    DocumentLink,
    DocumentLinkParams,
    DocumentOnTypeFormattingParams,
    DocumentRangeFormattingParams,
    DocumentSymbol,
    DocumentSymbolParams,
    ExecuteCommandParams,
    FoldingRange,
    FoldingRangeParams,
    Hover,
    HoverParams,
    ImplementationParams,
    InitializedParams,
    InitializeError,
    Location,
    NotificationHandler,
    NotificationHandler0,
    PrepareRenameParams,
    ProgressType,
    PublishDiagnosticsParams,
    Range,
    ReferenceParams,
    RemoteClient,
    RemoteConsole,
    RemoteTracer,
    RenameParams,
    RequestHandler,
    RequestHandler0,
    SelectionRange,
    SelectionRangeParams,
    ServerRequestHandler,
    SignatureHelp,
    SignatureHelpParams,
    SymbolInformation,
    Telemetry,
    TypeDefinitionParams,
    WillSaveTextDocumentParams,
    WorkspaceEdit,
    WorkspaceSymbol,
    WorkspaceSymbolParams
} from 'vscode-languageserver';
import {CallHierarchy} from 'vscode-languageserver/lib/common/callHierarchy.js';
import {Configuration} from 'vscode-languageserver/lib/common/configuration.js';
import {DiagnosticFeatureShape} from 'vscode-languageserver/lib/common/diagnostic.js';
import {FileOperationsFeatureShape} from 'vscode-languageserver/lib/common/fileOperations.js';
import {FoldingRangeFeatureShape} from 'vscode-languageserver/lib/common/foldingRange.js';
import {InlayHintFeatureShape} from 'vscode-languageserver/lib/common/inlayHint.js';
import {InlineValueFeatureShape} from 'vscode-languageserver/lib/common/inlineValue.js';
import {LinkedEditingRangeFeatureShape} from 'vscode-languageserver/lib/common/linkedEditingRange.js';
import {MonikerFeatureShape} from 'vscode-languageserver/lib/common/moniker.js';
import {NotebookSyncFeatureShape} from 'vscode-languageserver/lib/common/notebook.js';
import {WindowProgress} from 'vscode-languageserver/lib/common/progress.js';
import {SemanticTokensFeatureShape} from 'vscode-languageserver/lib/common/semanticTokens.js';
import {ShowDocumentFeatureShape} from 'vscode-languageserver/lib/common/showDocument.js';
import {TypeHierarchyFeatureShape} from 'vscode-languageserver/lib/common/typeHierarchy.js';
import {WorkspaceFolders} from 'vscode-languageserver/lib/common/workspaceFolder.js';
import {ConnectionStub} from "./ConnectionStub";

/**
 * This is the {@link BaseConnectionStub} used for any test that needs to use a {@link Connection}.
 * An example implementation can be found in {@link ConnectionStub}. You could make your own by extending this class.
 */
export abstract class BaseConnectionStub implements Connection {
    private stubMustImplement = 'This method must be overridden in your stub, if your test need to call it';

    listen(): void {
        throw new Error(this.stubMustImplement);
    }

    onRequest(_method: unknown, _handler?: unknown): import("vscode-languageserver").Disposable {
        throw new Error(this.stubMustImplement);
    }

    sendRequest<R>(_method: unknown, _params?: unknown, _token?: unknown): Promise<R> {
        throw new Error(this.stubMustImplement);
    }

    onNotification(_method: unknown, _handler?: unknown): import("vscode-languageserver").Disposable {
        throw new Error(this.stubMustImplement);
    }

    sendNotification(_method: unknown, _params?: unknown): Promise<void> {
        throw new Error(this.stubMustImplement);
    }

    onProgress<P>(_type: ProgressType<P>, _token: string | number, _handler: NotificationHandler<P>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    sendProgress<P>(_type: ProgressType<P>, _token: string | number, _value: P): Promise<void> {
        throw new Error(this.stubMustImplement);
    }

    onInitialize(_handler: ServerRequestHandler<InitializeParams, InitializeResult, never, InitializeError>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onInitialized(_handler: NotificationHandler<InitializedParams>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onShutdown(_handler: RequestHandler0<void, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onExit(_handler: NotificationHandler0): Disposable {
        throw new Error(this.stubMustImplement);
    }

    console: RemoteConsole & _;
    tracer: RemoteTracer & _;
    telemetry: Telemetry & _;
    client: RemoteClient & _;
    window: _RemoteWindow & WindowProgress & ShowDocumentFeatureShape & _;
    workspace: _RemoteWorkspace & Configuration & WorkspaceFolders & FileOperationsFeatureShape & _;
    languages: _Languages & CallHierarchy & SemanticTokensFeatureShape & LinkedEditingRangeFeatureShape & TypeHierarchyFeatureShape & InlineValueFeatureShape & InlayHintFeatureShape & DiagnosticFeatureShape & MonikerFeatureShape & FoldingRangeFeatureShape & _;
    notebooks: _Notebooks & NotebookSyncFeatureShape & _;

    onDidChangeConfiguration(_handler: NotificationHandler<DidChangeConfigurationParams>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDidChangeWatchedFiles(_handler: NotificationHandler<DidChangeWatchedFilesParams>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDidOpenTextDocument(_handler: NotificationHandler<DidOpenTextDocumentParams>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDidChangeTextDocument(_handler: NotificationHandler<DidChangeTextDocumentParams>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDidCloseTextDocument(_handler: NotificationHandler<DidCloseTextDocumentParams>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onWillSaveTextDocument(_handler: NotificationHandler<WillSaveTextDocumentParams>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onWillSaveTextDocumentWaitUntil(_handler: RequestHandler<WillSaveTextDocumentParams, TextEdit[] | undefined | null, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDidSaveTextDocument(_handler: NotificationHandler<DidSaveTextDocumentParams>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    sendDiagnostics(_params: PublishDiagnosticsParams): Promise<void> {
        throw new Error(this.stubMustImplement);
    }

    onHover(_handler: ServerRequestHandler<HoverParams, Hover | undefined | null, never, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onCompletion(_handler: ServerRequestHandler<CompletionParams, CompletionItem[] | CompletionList | undefined | null, CompletionItem[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onCompletionResolve(_handler: RequestHandler<CompletionItem, CompletionItem, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onSignatureHelp(_handler: ServerRequestHandler<SignatureHelpParams, SignatureHelp | undefined | null, never, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDeclaration(_handler: ServerRequestHandler<DeclarationParams, Declaration | DeclarationLink[] | undefined | null, Location[] | DeclarationLink[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDefinition(_handler: ServerRequestHandler<DefinitionParams, Definition | DefinitionLink[] | undefined | null, Location[] | DefinitionLink[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onTypeDefinition(_handler: ServerRequestHandler<TypeDefinitionParams, Definition | DefinitionLink[] | undefined | null, Location[] | DefinitionLink[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onImplementation(_handler: ServerRequestHandler<ImplementationParams, Definition | DefinitionLink[] | undefined | null, Location[] | DefinitionLink[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onReferences(_handler: ServerRequestHandler<ReferenceParams, Location[] | undefined | null, Location[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDocumentHighlight(_handler: ServerRequestHandler<DocumentHighlightParams, DocumentHighlight[] | undefined | null, DocumentHighlight[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDocumentSymbol(_handler: ServerRequestHandler<DocumentSymbolParams, SymbolInformation[] | DocumentSymbol[] | undefined | null, SymbolInformation[] | DocumentSymbol[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onWorkspaceSymbol(_handler: ServerRequestHandler<WorkspaceSymbolParams, SymbolInformation[] | WorkspaceSymbol[] | undefined | null, SymbolInformation[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onWorkspaceSymbolResolve(_handler: RequestHandler<WorkspaceSymbol, WorkspaceSymbol, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onCodeAction(_handler: ServerRequestHandler<CodeActionParams, (Command | CodeAction)[] | undefined | null, (Command | CodeAction)[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onCodeActionResolve(_handler: RequestHandler<CodeAction, CodeAction, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onCodeLens(_handler: ServerRequestHandler<CodeLensParams, CodeLens[] | undefined | null, CodeLens[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onCodeLensResolve(_handler: RequestHandler<CodeLens, CodeLens, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDocumentFormatting(_handler: ServerRequestHandler<DocumentFormattingParams, TextEdit[] | undefined | null, never, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDocumentRangeFormatting(_handler: ServerRequestHandler<DocumentRangeFormattingParams, TextEdit[] | undefined | null, never, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDocumentOnTypeFormatting(_handler: RequestHandler<DocumentOnTypeFormattingParams, TextEdit[] | undefined | null, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onRenameRequest(_handler: ServerRequestHandler<RenameParams, WorkspaceEdit | undefined | null, never, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onPrepareRename(_handler: RequestHandler<PrepareRenameParams, Range | { range: Range; placeholder: string; } | {
        defaultBehavior: boolean;
    } | undefined | null, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDocumentLinks(_handler: ServerRequestHandler<DocumentLinkParams, DocumentLink[] | undefined | null, DocumentLink[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDocumentLinkResolve(_handler: RequestHandler<DocumentLink, DocumentLink | undefined | null, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onDocumentColor(_handler: ServerRequestHandler<DocumentColorParams, ColorInformation[] | undefined | null, ColorInformation[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onColorPresentation(_handler: ServerRequestHandler<ColorPresentationParams, ColorPresentation[] | undefined | null, ColorPresentation[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onFoldingRanges(_handler: ServerRequestHandler<FoldingRangeParams, FoldingRange[] | undefined | null, FoldingRange[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onSelectionRanges(_handler: ServerRequestHandler<SelectionRangeParams, SelectionRange[] | undefined | null, SelectionRange[], void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    onExecuteCommand(_handler: ServerRequestHandler<ExecuteCommandParams, any | undefined | null, never, void>): Disposable {
        throw new Error(this.stubMustImplement);
    }

    dispose(): void {
        throw new Error(this.stubMustImplement);
    }

}
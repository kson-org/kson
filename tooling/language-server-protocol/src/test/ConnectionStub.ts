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
    ExecuteCommandParams, WorkspaceEdit, ApplyWorkspaceEditResult, ApplyWorkspaceEditParams
} from "vscode-languageserver";
import {BoilerplateConnectionStub} from "./BoilerplateConnectionStub";
import {Languages, RemoteWorkspace} from "vscode-languageserver/lib/common/server";
import {TextDocument} from "vscode-languageserver-textdocument";
import {KsonDocumentsManager} from "../core/document/KsonDocumentsManager";

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

    languages: Languages;
    workspace: RemoteWorkspace;
    private documentsManager?: KsonDocumentsManager;

    constructor() {
        super();
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
            applyEdit: async (paramOrEdit: ApplyWorkspaceEditParams | WorkspaceEdit) => {
                // Extract the WorkspaceEdit - it could be passed directly or wrapped in params
                const edit: WorkspaceEdit = 'edit' in paramOrEdit ? paramOrEdit.edit : paramOrEdit;
                // Apply edits to documents in the test environment
                if (this.documentsManager && edit.changes) {
                    for (const [uri, edits] of Object.entries(edit.changes)) {
                        const document = this.documentsManager.get(uri);
                        if (document && edits.length > 0) {
                            // Apply edits to the document
                            const updatedText = TextDocument.applyEdits(document.textDocument, edits);
                            // Simulate a document change event
                            const changeEvent: DidChangeTextDocumentParams = {
                                textDocument: {
                                    uri: document.uri,
                                    version: document.version + 1
                                },
                                contentChanges: [{
                                    text: updatedText
                                }]
                            };
                            if (this.didChangeHandler) {
                                this.didChangeHandler(changeEvent);
                            }
                        }
                    }
                }
                return Promise.resolve({ applied: true } as ApplyWorkspaceEditResult);
            }
        } as RemoteWorkspace;
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

    setDocumentsManager(documentsManager: KsonDocumentsManager): void {
        this.documentsManager = documentsManager;
    }
}

const NOOP_DISPOSABLE: Disposable = {
    dispose: () => {
    }
};

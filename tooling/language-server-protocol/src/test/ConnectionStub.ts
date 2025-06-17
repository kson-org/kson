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
    DidChangeTextDocumentParams, DidCloseTextDocumentParams, WillSaveTextDocumentParams, DidSaveTextDocumentParams
} from "vscode-languageserver";
import {BaseConnectionStub} from "./BaseConnectionStub";
import {Languages} from "vscode-languageserver/lib/common/server";

/**
 * Stub class used for tests that need Connections.
 * It extends the {@link BaseConnectionStub} by overriding its methods.
 */
export class ConnectionStub extends BaseConnectionStub {
    public formattingHandler: ServerRequestHandler<DocumentFormattingParams, TextEdit[] | null | undefined, never, void>;
    public didOpenHandler: NotificationHandler<DidOpenTextDocumentParams>;
    public didChangeHandler: NotificationHandler<DidChangeTextDocumentParams>;
    public didCloseHandler: NotificationHandler<DidCloseTextDocumentParams>;
    public willSaveHandler: NotificationHandler<WillSaveTextDocumentParams>;
    public willSaveWaitUntilHandler: ServerRequestHandler<WillSaveTextDocumentParams, TextEdit[] | null | undefined, never, void>;
    public didSaveHandler: NotificationHandler<DidSaveTextDocumentParams>;
    public semanticTokensHandler: ServerRequestHandler<SemanticTokensParams, SemanticTokens, any, void>;
    public diagnosticsHandler: ServerRequestHandler<DocumentDiagnosticParams, DocumentDiagnosticReport, any, void>;

    languages: Languages;

    constructor() {
        super();
        this.languages = {
            semanticTokens: {
                on: (handler: ServerRequestHandler<SemanticTokensParams, SemanticTokens, any, void>) => {
                    this.semanticTokensHandler = handler;
                    return {
                        dispose: () => {
                        }
                    };
                }
            },
            diagnostics: {
                on: (handler: ServerRequestHandler<DocumentDiagnosticParams, DocumentDiagnosticReport, any, void>) => {
                    this.diagnosticsHandler = handler;
                    return {
                        dispose: () => {
                        }
                    };
                }
            }
        } as any;
    }

    override onDocumentFormatting(handler: ServerRequestHandler<DocumentFormattingParams, TextEdit[] | undefined | null, never, void>): Disposable {
        this.formattingHandler = handler;
        return {
            dispose: () => {
            }
        };
    }

    onDidOpenTextDocument(handler: NotificationHandler<DidOpenTextDocumentParams>): Disposable {
        this.didOpenHandler = handler;
        return {
            dispose: () => {
            }
        };
    }

    onDidChangeTextDocument(handler: NotificationHandler<DidChangeTextDocumentParams>): Disposable {
        this.didChangeHandler = handler;
        return {
            dispose: () => {
            }
        };
    }

    onDidCloseTextDocument(handler: NotificationHandler<DidCloseTextDocumentParams>): Disposable {
        this.didCloseHandler = handler;
        return {
            dispose: () => {
            }
        };
    }

    onWillSaveTextDocument(handler: NotificationHandler<WillSaveTextDocumentParams>): Disposable {
        this.willSaveHandler = handler;
        return {
            dispose: () => {
            }
        };
    }

    onWillSaveTextDocumentWaitUntil(handler: ServerRequestHandler<WillSaveTextDocumentParams, TextEdit[] | null | undefined, never, void>): Disposable {
        this.willSaveWaitUntilHandler = handler;
        return {
            dispose: () => {
            }
        };
    }

    onDidSaveTextDocument(handler: NotificationHandler<DidSaveTextDocumentParams>): Disposable {
        this.didSaveHandler = handler;
        return {
            dispose: () => {
            }
        };
    }

}
/**
 * Bridges a Monaco editor to the KSON language server running in a Web Worker.
 *
 * Registers Monaco language providers that proxy to the LSP server via JSON-RPC,
 * and keeps the server's document state in sync with editor changes.
 */

import * as monaco from 'monaco-editor';
import { KSON_LANGUAGE_ID } from '../language/ksonLanguage.js';
import { JsonRpcConnection } from './JsonRpcConnection.js';
import {
    toLspPosition,
    toMonacoRange,
    toMonacoMarkers,
    toMonacoCompletions,
    toMonacoHover,
    toMonacoDocumentSymbols,
    toMonacoDocumentHighlights,
    toMonacoTextEdits,
    toMonacoCodeLenses,
    toMonacoDefinition,
    type LspTextEdit,
    type LspCompletionList,
    type LspCompletionItem,
    type LspHover,
    type LspDocumentSymbol,
    type LspDocumentHighlight,
    type LspCodeLens,
    type LspLocation,
    type LspDefinitionLink,
    type LspSemanticTokens,
    type LspSemanticTokensLegend,
    type LspDiagnostic,
} from './lspToMonaco.js';

const DIAGNOSTIC_DEBOUNCE_MS = 300;

export interface KsonLspBridgeOptions {
    /** LSP initialization options forwarded to the server. */
    initializationOptions?: {
        bundledSchemas?: Array<{ fileExtension: string; schemaContent: string }>;
        bundledMetaSchemas?: Array<{ schemaId: string; name: string; schemaContent: string }>;
        enableBundledSchemas?: boolean;
    };
}

export interface ServerCapabilities {
    semanticTokensProvider?: { legend: LspSemanticTokensLegend };
    completionProvider?: { triggerCharacters?: string[] };
    executeCommandProvider?: { commands: string[] };
    [key: string]: unknown;
}

interface InitializeResult {
    capabilities: ServerCapabilities;
}

interface DocumentDiagnosticReport {
    kind: string;
    items: LspDiagnostic[];
}

interface ApplyWorkspaceEditParams {
    edit: {
        changes?: Record<string, LspTextEdit[]>;
    };
}

export class KsonLspBridge {
    private readonly connection: JsonRpcConnection;
    private readonly disposables: monaco.IDisposable[] = [];
    private readonly trackedDocuments = new Map<string, {
        model: monaco.editor.ITextModel;
        version: number;
        changeDisposable: monaco.IDisposable;
    }>();
    private readonly readOnlyDocumentUris = new Set<string>();
    private diagnosticTimer?: ReturnType<typeof setTimeout>;
    private providersRegistered = false;

    constructor(worker: Worker) {
        this.connection = new JsonRpcConnection(worker);

        // Forward server log messages to the browser console
        this.connection.onNotification('window/logMessage', (params: unknown) => {
            const { type, message } = params as { type: number; message: string };
            if (type === 1) console.error('[LSP]', message);
            else if (type === 2) console.warn('[LSP]', message);
            else console.log('[LSP]', message);
        });
    }

    /**
     * Send LSP initialize/initialized handshake.
     * Must be called before attaching to an editor.
     */
    async initialize(options?: KsonLspBridgeOptions): Promise<InitializeResult> {
        const result = await this.connection.sendRequest<InitializeResult>('initialize', {
            processId: null,
            capabilities: {
                textDocument: {
                    completion: { completionItem: { snippetSupport: true } },
                    hover: { contentFormat: ['markdown', 'plaintext'] },
                    semanticTokens: { requests: { full: true }, tokenTypes: [], tokenModifiers: [] },
                    diagnostic: { dynamicRegistration: false },
                },
            },
            rootUri: null,
            workspaceFolders: null,
            initializationOptions: options?.initializationOptions,
        });

        this.connection.sendNotification('initialized', {});

        // Handle server-initiated diagnostic refresh
        this.connection.onRequest('workspace/diagnostic/refresh', () => {
            this.pullDiagnostics();
            return null;
        });

        // Handle server-initiated workspace edits (e.g. from command execution)
        this.connection.onRequest('workspace/applyEdit', (params: unknown) => {
            const { edit } = params as ApplyWorkspaceEditParams;
            if (edit.changes) {
                for (const [uri, edits] of Object.entries(edit.changes)) {
                    const tracked = this.trackedDocuments.get(uri);
                    if (!tracked) continue;
                    const monacoEdits = (edits as LspTextEdit[]).map((e) => ({
                        range: toMonacoRange(e.range),
                        text: e.newText,
                    }));
                    tracked.model.pushEditOperations([], monacoEdits, () => null);
                }
            }
            return { applied: true };
        });

        return result;
    }

    /**
     * Connect this bridge to a Monaco editor. Registers language providers (once)
     * and starts syncing the editor's document state with the server.
     */
    attachToEditor(
        editor: monaco.editor.IStandaloneCodeEditor,
        languageId: string,
        capabilities: ServerCapabilities,
    ): monaco.IDisposable {
        const model = editor.getModel();
        if (!model) throw new Error('Editor has no model');

        const trackingDisposable = this.trackModel(model);

        if (!this.providersRegistered) {
            this.registerProviders(languageId, capabilities);
            this.providersRegistered = true;
        }

        return trackingDisposable;
    }

    /** Open a read-only document in the LSP (e.g. a bundled schema) so it gets semantic tokens. */
    openReadOnlyDocument(uri: string, text: string): void {
        this.readOnlyDocumentUris.add(uri);
        this.connection.sendNotification('textDocument/didOpen', {
            textDocument: { uri, languageId: KSON_LANGUAGE_ID, version: 1, text },
        });
    }

    /**
     * Begin tracking a model's content with the language server.
     * Called automatically by `attachToEditor`, but may also be called directly
     * for models that aren't attached to a visible editor yet.
     *
     * Returns a disposable that stops tracking (sends didClose and removes the
     * change listener).  Call this when disposing a shared editor.
     */
    trackModel(model: monaco.editor.ITextModel): monaco.IDisposable {
        const uri = model.uri.toString();
        if (this.trackedDocuments.has(uri)) return { dispose: () => {} };

        // didOpen
        this.connection.sendNotification('textDocument/didOpen', {
            textDocument: {
                uri,
                languageId: KSON_LANGUAGE_ID,
                version: 1,
                text: model.getValue(),
            },
        });

        // didChange (full sync — matches server's TextDocumentSyncKind.Full)
        const doc = {
            model,
            version: 1,
            changeDisposable: model.onDidChangeContent(() => {
                this.connection.sendNotification('textDocument/didChange', {
                    textDocument: { uri, version: ++doc.version },
                    contentChanges: [{ text: model.getValue() }],
                });
                this.scheduleDiagnostics();
            }),
        };
        this.trackedDocuments.set(uri, doc);

        // Initial diagnostics
        this.scheduleDiagnostics();

        return {
            dispose: () => {
                doc.changeDisposable.dispose();
                this.trackedDocuments.delete(uri);
                this.connection.sendNotification('textDocument/didClose', {
                    textDocument: { uri },
                });
            },
        };
    }

    private scheduleDiagnostics(): void {
        clearTimeout(this.diagnosticTimer);
        this.diagnosticTimer = setTimeout(() => this.pullDiagnostics(), DIAGNOSTIC_DEBOUNCE_MS);
    }

    private async pullDiagnostics(): Promise<void> {
        for (const [uri, { model }] of this.trackedDocuments) {
            try {
                const report = await this.connection.sendRequest<DocumentDiagnosticReport>(
                    'textDocument/diagnostic',
                    { textDocument: { uri } },
                );
                if (report?.items) {
                    monaco.editor.setModelMarkers(model, 'kson', toMonacoMarkers(report.items));
                }
            } catch (err) {
                console.warn('[LSP] Pull diagnostics failed for', uri, err);
            }
        }
    }

    private registerProviders(
        languageId: string,
        capabilities: ServerCapabilities,
    ): void {
        this.registerLspCommands(capabilities.executeCommandProvider);
        this.registerCompletionProvider(languageId, capabilities.completionProvider);
        this.registerHoverProvider(languageId);
        this.registerDefinitionProvider(languageId);
        this.registerDocumentSymbolProvider(languageId);
        this.registerDocumentHighlightProvider(languageId);
        this.registerFormattingProvider(languageId);
        this.registerCodeLensProvider(languageId);

        if (capabilities.semanticTokensProvider) {
            this.registerSemanticTokensProvider(languageId, capabilities.semanticTokensProvider.legend);
        }
    }

    private registerLspCommands(
        config?: { commands: string[] },
    ): void {
        for (const commandId of config?.commands ?? []) {
            this.disposables.push(
                monaco.editor.registerCommand(commandId, async (_accessor, ...args: unknown[]) => {
                    try {
                        await this.connection.sendRequest('workspace/executeCommand', {
                            command: commandId,
                            arguments: args,
                        });
                    } catch (err) {
                        console.error(`[LSP] Command '${commandId}' failed:`, err);
                    }
                }),
            );
        }
    }

    private registerCompletionProvider(
        languageId: string,
        config?: { triggerCharacters?: string[] },
    ): void {
        this.disposables.push(
            monaco.languages.registerCompletionItemProvider(languageId, {
                triggerCharacters: config?.triggerCharacters,
                provideCompletionItems: async (model, position) => {
                    const result = await this.connection.sendRequest<
                        LspCompletionList | LspCompletionItem[] | null
                    >('textDocument/completion', {
                        textDocument: { uri: model.uri.toString() },
                        position: toLspPosition(position),
                    });
                    const wordAtPos = model.getWordAtPosition(position);
                    const range = wordAtPos
                        ? new monaco.Range(
                            position.lineNumber, wordAtPos.startColumn,
                            position.lineNumber, wordAtPos.endColumn,
                        )
                        : new monaco.Range(
                            position.lineNumber, position.column,
                            position.lineNumber, position.column,
                        );
                    return toMonacoCompletions(result, range);
                },
            }),
        );
    }

    private registerHoverProvider(languageId: string): void {
        this.disposables.push(
            monaco.languages.registerHoverProvider(languageId, {
                provideHover: async (model, position) => {
                    const result = await this.connection.sendRequest<LspHover | null>(
                        'textDocument/hover',
                        {
                            textDocument: { uri: model.uri.toString() },
                            position: toLspPosition(position),
                        },
                    );
                    return toMonacoHover(result);
                },
            }),
        );
    }

    private registerDefinitionProvider(languageId: string): void {
        this.disposables.push(
            monaco.languages.registerDefinitionProvider(languageId, {
                provideDefinition: async (model, position) => {
                    const result = await this.connection.sendRequest<
                        LspLocation | LspLocation[] | LspDefinitionLink[] | null
                    >(
                        'textDocument/definition',
                        {
                            textDocument: { uri: model.uri.toString() },
                            position: toLspPosition(position),
                        },
                    );
                    return toMonacoDefinition(result);
                },
            }),
        );
    }

    private registerDocumentSymbolProvider(languageId: string): void {
        this.disposables.push(
            monaco.languages.registerDocumentSymbolProvider(languageId, {
                provideDocumentSymbols: async (model) => {
                    const result = await this.connection.sendRequest<LspDocumentSymbol[] | null>(
                        'textDocument/documentSymbol',
                        { textDocument: { uri: model.uri.toString() } },
                    );
                    return toMonacoDocumentSymbols(result);
                },
            }),
        );
    }

    private registerDocumentHighlightProvider(languageId: string): void {
        this.disposables.push(
            monaco.languages.registerDocumentHighlightProvider(languageId, {
                provideDocumentHighlights: async (model, position) => {
                    const result = await this.connection.sendRequest<LspDocumentHighlight[] | null>(
                        'textDocument/documentHighlight',
                        {
                            textDocument: { uri: model.uri.toString() },
                            position: toLspPosition(position),
                        },
                    );
                    return toMonacoDocumentHighlights(result);
                },
            }),
        );
    }

    private registerFormattingProvider(languageId: string): void {
        this.disposables.push(
            monaco.languages.registerDocumentFormattingEditProvider(languageId, {
                provideDocumentFormattingEdits: async (model, options) => {
                    const result = await this.connection.sendRequest<LspTextEdit[] | null>(
                        'textDocument/formatting',
                        {
                            textDocument: { uri: model.uri.toString() },
                            options: {
                                tabSize: options.tabSize,
                                insertSpaces: options.insertSpaces,
                            },
                        },
                    );
                    return toMonacoTextEdits(result);
                },
            }),
        );
    }

    private registerCodeLensProvider(languageId: string): void {
        this.disposables.push(
            monaco.languages.registerCodeLensProvider(languageId, {
                provideCodeLenses: async (model) => {
                    const result = await this.connection.sendRequest<LspCodeLens[] | null>(
                        'textDocument/codeLens',
                        { textDocument: { uri: model.uri.toString() } },
                    );
                    return { lenses: toMonacoCodeLenses(result), dispose: () => {} };
                },
            }),
        );
    }

    private registerSemanticTokensProvider(
        languageId: string,
        legend: LspSemanticTokensLegend,
    ): void {
        this.disposables.push(
            monaco.languages.registerDocumentSemanticTokensProvider(languageId, {
                getLegend: () => legend,
                provideDocumentSemanticTokens: async (model) => {
                    const result = await this.connection.sendRequest<LspSemanticTokens | null>(
                        'textDocument/semanticTokens/full',
                        { textDocument: { uri: model.uri.toString() } },
                    );
                    return result ? { data: new Uint32Array(result.data) } : { data: new Uint32Array() };
                },
                releaseDocumentSemanticTokens: () => {},
            }),
        );
    }

    dispose(): void {
        clearTimeout(this.diagnosticTimer);
        for (const [uri, doc] of this.trackedDocuments) {
            doc.changeDisposable.dispose();
            this.connection.sendNotification('textDocument/didClose', {
                textDocument: { uri },
            });
        }
        this.trackedDocuments.clear();
        for (const uri of this.readOnlyDocumentUris) {
            this.connection.sendNotification('textDocument/didClose', {
                textDocument: { uri },
            });
        }
        this.readOnlyDocumentUris.clear();
        // LSP spec: shutdown is a request; exit is the follow-up notification.
        // We fire-and-forget since the worker will be terminated momentarily.
        this.connection.sendRequest('shutdown').then(
            () => this.connection.sendNotification('exit'),
            () => {},
        );
        for (const d of this.disposables) d.dispose();
        this.disposables.length = 0;
        this.connection.dispose();
    }
}

import * as monaco from 'monaco-editor';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import { KsonLspBridge, type KsonLspBridgeOptions, type ServerCapabilities } from './bridge/index.js';
import { registerKsonLanguage, KSON_LANGUAGE_ID } from './language/ksonLanguage.js';
import workerUrl from './worker/ksonServer?worker&url';

// Monaco needs a worker factory for its built-in editor features (tokenization, etc.).
// Only set one if the consumer hasn't already configured their own.
if (!self.MonacoEnvironment) {
    self.MonacoEnvironment = {
        getWorker(): Worker {
            return new editorWorker();
        },
    };
}

export interface KsonEditorOptions {
    /** Initial editor content. */
    value?: string;

    /** Document URI used for LSP identification. Defaults to 'inmemory://kson/document.kson'. */
    uri?: string;

    /** Monaco editor construction options (language is always set to 'kson'). */
    editorOptions?: monaco.editor.IStandaloneEditorConstructionOptions;

    /** Options forwarded to the LSP server during initialization. */
    lspOptions?: KsonLspBridgeOptions['initializationOptions'];

    /**
     * Share an existing language server by passing the bridge and worker from
     * a previously created KsonEditor.  When set, `lspOptions` is ignored
     * (the server is already initialized).
     */
    shared?: Pick<KsonEditor, 'bridge' | 'worker' | 'serverCapabilities'>;
}

export interface KsonEditor {
    /** The underlying Monaco editor instance. */
    readonly editor: monaco.editor.IStandaloneCodeEditor;

    /** The LSP bridge (exposed so additional editors can share it). */
    readonly bridge: KsonLspBridge;

    /** The language server worker (exposed so additional editors can share it). */
    readonly worker: Worker;

    /** Server capabilities from initialization (needed when sharing). */
    readonly serverCapabilities: ServerCapabilities;

    /** Dispose the editor, bridge, and language server worker. */
    dispose(): void;
}

/**
 * Creates a KSON editor backed by a full language server running in a Web Worker.
 *
 * The language server is the same one used by the VSCode extension — completions,
 * diagnostics, hover, go-to-definition, formatting, semantic tokens, etc. all work
 * via a lightweight JSON-RPC bridge (no @codingame or monaco-languageclient dependency).
 */
export async function createKsonEditor(
    container: HTMLElement,
    options?: KsonEditorOptions,
): Promise<KsonEditor> {
    registerKsonLanguage();

    const uri = monaco.Uri.parse(options?.uri ?? 'inmemory://kson/document.kson');

    // Either reuse a shared bridge/worker or create new ones.
    const isShared = !!options?.shared;
    let worker: Worker;
    let bridge: KsonLspBridge;
    let capabilities: ServerCapabilities;

    if (options?.shared) {
        worker = options.shared.worker;
        bridge = options.shared.bridge;
        capabilities = options.shared.serverCapabilities;
    } else {
        worker = new Worker(workerUrl, {
            type: 'module',
            name: 'KsonLanguageServer',
        });

        bridge = new KsonLspBridge(worker);
        const result = await bridge.initialize({ initializationOptions: options?.lspOptions });
        capabilities = result.capabilities;
    }

    // Create model and editor
    const model = monaco.editor.createModel(
        options?.value ?? '',
        KSON_LANGUAGE_ID,
        uri,
    );

    const editor = monaco.editor.create(container, {
        model,
        automaticLayout: true,
        minimap: { enabled: false },
        'semanticHighlighting.enabled': true,
        ...options?.editorOptions,
    });

    // Create read-only models for bundled schemas so go-to-definition can navigate to them.
    // The LSP server uses URIs like bundled://schema/kson.schema.kson for these.
    // Skip when sharing — the primary editor already created these.
    const schemaModels: monaco.editor.ITextModel[] = [];
    if (!isShared) {
        for (const schema of options?.lspOptions?.bundledSchemas ?? []) {
            const schemaUri = monaco.Uri.parse(`bundled://schema/${schema.fileExtension}.schema.kson`);
            if (!monaco.editor.getModel(schemaUri)) {
                schemaModels.push(monaco.editor.createModel(schema.schemaContent, KSON_LANGUAGE_ID, schemaUri));
            }
        }
        for (const meta of options?.lspOptions?.bundledMetaSchemas ?? []) {
            const metaUri = monaco.Uri.parse(`bundled://metaschema/${meta.name}.schema.kson`);
            if (!monaco.editor.getModel(metaUri)) {
                schemaModels.push(monaco.editor.createModel(meta.schemaContent, KSON_LANGUAGE_ID, metaUri));
            }
        }

        // Open schema models in the LSP so they get semantic tokens
        for (const m of schemaModels) {
            bridge.openReadOnlyDocument(m.uri.toString(), m.getValue());
        }
    }

    // Navigation stack for go-to-definition / go-back across models.
    const navigationStack: Array<{ uri: monaco.Uri; selection: monaco.Selection }> = [];

    // Standalone Monaco doesn't know how to open a different model (no IEditorService).
    // Register a global opener so go-to-definition can navigate to schema models.
    const openerDisposable = monaco.editor.registerEditorOpener({
        openCodeEditor(_source, resource, selectionOrPosition) {
            const targetModel = monaco.editor.getModel(resource);
            if (!targetModel) return false;

            // Save current position before navigating
            const currentModel = editor.getModel();
            const currentSelection = editor.getSelection();
            if (currentModel && currentSelection) {
                navigationStack.push({
                    uri: currentModel.uri,
                    selection: currentSelection,
                });
            }

            editor.setModel(targetModel);
            if (selectionOrPosition) {
                if ('startLineNumber' in selectionOrPosition) {
                    editor.setSelection(selectionOrPosition);
                    editor.revealRangeInCenter(selectionOrPosition);
                } else {
                    editor.setPosition(selectionOrPosition);
                    editor.revealPositionInCenter(selectionOrPosition);
                }
            }
            return true;
        },
    });

    // Register Alt+Left (Go Back) to pop the navigation stack
    editor.addAction({
        id: 'kson.goBack',
        label: 'Go Back',
        keybindings: [monaco.KeyMod.Alt | monaco.KeyCode.LeftArrow],
        run: () => {
            const prev = navigationStack.pop();
            if (!prev) return;
            const prevModel = monaco.editor.getModel(prev.uri);
            if (!prevModel) return;
            editor.setModel(prevModel);
            editor.setSelection(prev.selection);
            editor.revealRangeInCenter(prev.selection);
        },
    });

    // Wire up the bridge
    const trackingDisposable = bridge.attachToEditor(editor, KSON_LANGUAGE_ID, capabilities);

    return {
        editor,
        bridge,
        worker,
        serverCapabilities: capabilities,
        dispose() {
            openerDisposable.dispose();
            trackingDisposable.dispose();
            if (!isShared) {
                bridge.dispose();
                worker.terminate();
                for (const m of schemaModels) m.dispose();
            }
            editor.dispose();
            model.dispose();
        },
    };
}

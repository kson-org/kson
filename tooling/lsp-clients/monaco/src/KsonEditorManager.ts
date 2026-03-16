import * as monaco from 'monaco-editor';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import { KsonLspBridge, type KsonLspBridgeOptions, type ServerCapabilities } from './bridge/index.js';
import { registerKsonLanguage, KSON_LANGUAGE_ID } from './language/ksonLanguage.js';
import { TabBar } from './TabBar.js';
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

/** Tracks whether a primary (non-shared) bridge has been created. */
let activeBridge: KsonLspBridge | null = null;

/** Extracts a human-readable label from a document URI (e.g. "project.kson"). */
function labelFromUri(uri: monaco.Uri): string {
    const path = uri.path || uri.toString();
    return path.split('/').pop() || path;
}

/**
 * Creates a KSON editor backed by a full language server running in a Web Worker.
 *
 * The language server is the same one used by the VSCode extension — completions,
 * diagnostics, hover, go-to-definition, formatting, semantic tokens, etc. all work
 * via a lightweight JSON-RPC bridge (no @codingame or monaco-languageclient dependency).
 *
 * The first call creates the language server.  Additional editors must share it by
 * passing `shared: firstEditor` — creating a second independent server would cause
 * duplicate language providers in Monaco, leading to doubled completions and other
 * confusing behavior.
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
        if (activeBridge) {
            throw new Error(
                'A KSON language server is already running. ' +
                'Pass { shared: existingEditor } to share it instead of creating a second one.',
            );
        }

        worker = new Worker(workerUrl, {
            type: 'module',
            name: 'KsonLanguageServer',
        });

        bridge = new KsonLspBridge(worker);
        const result = await bridge.initialize({ initializationOptions: options?.lspOptions });
        capabilities = result.capabilities;
        activeBridge = bridge;
    }

    // Wrap the container in a flex column so the tab bar and editor stack vertically.
    const wrapper = document.createElement('div');
    wrapper.style.cssText = 'display:flex; flex-direction:column; width:100%; height:100%;';
    container.appendChild(wrapper);

    const editorContainer = document.createElement('div');
    editorContainer.style.cssText = 'flex:1; min-height:0;';

    // Saved editor view states (scroll position, cursor, etc.) per document URI,
    // so switching tabs restores exactly where the user left off.
    const viewStates = new Map<string, monaco.editor.ICodeEditorViewState>();

    // URIs of bundled schema models, used to style their tabs as read-only.
    const schemaUris = new Set<string>();

    /** Save current view state, switch to the target model, and restore its view state. */
    function switchToModel(targetModel: monaco.editor.ITextModel): void {
        const currentModel = editor.getModel();
        if (currentModel) {
            const vs = editor.saveViewState();
            if (vs) viewStates.set(currentModel.uri.toString(), vs);
        }

        editor.setModel(targetModel);
        editor.updateOptions({ readOnly: schemaUris.has(targetModel.uri.toString()) });
        const vs = viewStates.get(targetModel.uri.toString());
        if (vs) editor.restoreViewState(vs);
    }

    const tabBar = new TabBar({
        onActivate(targetUri) {
            const targetModel = monaco.editor.getModel(monaco.Uri.parse(targetUri));
            if (targetModel) switchToModel(targetModel);
        },
        onClose(closedUri) {
            viewStates.delete(closedUri);
        },
    });

    wrapper.appendChild(tabBar.element);
    wrapper.appendChild(editorContainer);

    // Create model and editor
    const model = monaco.editor.createModel(
        options?.value ?? '',
        KSON_LANGUAGE_ID,
        uri,
    );

    const editor = monaco.editor.create(editorContainer, {
        model,
        automaticLayout: true,
        minimap: { enabled: false },
        'semanticHighlighting.enabled': true,
        ...options?.editorOptions,
    });

    // Open the initial document as a non-closeable tab.
    tabBar.open(uri.toString(), labelFromUri(uri), false);

    // Create read-only models for bundled schemas so go-to-definition can navigate to them.
    // The LSP server uses URIs like bundled://schema/kson.schema.kson for these.
    // Skip when sharing — the primary editor already created these.
    const schemaModels: monaco.editor.ITextModel[] = [];
    if (!isShared) {
        for (const schema of options?.lspOptions?.bundledSchemas ?? []) {
            const schemaUri = monaco.Uri.parse(`bundled://schema/${schema.fileExtension}.schema.kson`);
            if (!monaco.editor.getModel(schemaUri)) {
                schemaModels.push(monaco.editor.createModel(schema.schemaContent, KSON_LANGUAGE_ID, schemaUri));
                schemaUris.add(schemaUri.toString());
            }
        }
        for (const meta of options?.lspOptions?.bundledMetaSchemas ?? []) {
            const metaUri = monaco.Uri.parse(`bundled://metaschema/${meta.name}.schema.kson`);
            if (!monaco.editor.getModel(metaUri)) {
                schemaModels.push(monaco.editor.createModel(meta.schemaContent, KSON_LANGUAGE_ID, metaUri));
                schemaUris.add(metaUri.toString());
            }
        }

        // Open schema models in the LSP so they get semantic tokens
        for (const m of schemaModels) {
            bridge.openReadOnlyDocument(m.uri.toString(), m.getValue());
        }
    }

    // Standalone Monaco doesn't know how to open a different model (no IEditorService).
    // Register a global opener so go-to-definition can navigate to schema models,
    // opening a tab for each navigated document.
    const openerDisposable = monaco.editor.registerEditorOpener({
        openCodeEditor(source, resource, selectionOrPosition) {
            // Only handle navigations originating from this editor instance.
            // registerEditorOpener is global, so without this guard a second
            // editor's opener could intercept navigations from the first.
            if (source !== editor) return false;

            const targetModel = monaco.editor.getModel(resource);
            if (!targetModel) return false;

            // Open (or activate) a tab for the target document
            tabBar.open(resource.toString(), labelFromUri(resource), true, schemaUris.has(resource.toString()));

            switchToModel(targetModel);
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

    // Wire up the bridge
    const trackingDisposable = bridge.attachToEditor(editor, KSON_LANGUAGE_ID, capabilities);

    return {
        editor,
        bridge,
        worker,
        serverCapabilities: capabilities,
        dispose() {
            tabBar.dispose();
            openerDisposable.dispose();
            trackingDisposable.dispose();
            if (!isShared) {
                bridge.dispose();
                worker.terminate();
                for (const m of schemaModels) m.dispose();
                activeBridge = null;
            }
            editor.dispose();
            model.dispose();
            wrapper.remove();
        },
    };
}

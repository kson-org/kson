import * as monaco from 'monaco-editor';
import { type KsonLspBridge, type KsonLspBridgeOptions, type ServerCapabilities } from './bridge/index.js';
import { createBundledSchemaModels, isBundledSchemaUri } from './bundledSchemas.js';
import { registerKsonLanguage, KSON_LANGUAGE_ID } from './language/ksonLanguage.js';
import { acquireLsp, releaseLsp } from './lspRegistry.js';
import { TabBar } from './TabBar.js';

export interface KsonEditorOptions {
    /** Initial editor content. */
    value?: string;

    /** Document URI used for LSP identification. Defaults to 'inmemory://kson/document.kson'. */
    uri?: string;

    /** Monaco editor construction options (language is always set to 'kson'). */
    editorOptions?: monaco.editor.IStandaloneEditorConstructionOptions;

    /** Options forwarded to the LSP server during initialization. */
    lspOptions?: KsonLspBridgeOptions['initializationOptions'];
}

export interface KsonEditor {
    /** The underlying Monaco editor instance. */
    readonly editor: monaco.editor.IStandaloneCodeEditor;

    /** The LSP bridge — exposed for advanced use; most consumers don't need this. */
    readonly bridge: KsonLspBridge;

    /** The language server worker — exposed for advanced use; most consumers don't need this. */
    readonly worker: Worker;

    /** Capabilities reported by the language server during initialization. */
    readonly serverCapabilities: ServerCapabilities;

    /** Dispose the editor and release this instance's reference to the shared LSP. Idempotent. */
    dispose(): void;
}

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
 * The language server is shared across all editors on the page (refcounted via
 * lspRegistry) — the first editor spins it up, the last one to dispose tears it
 * down.
 */
export async function createKsonEditor(
    container: HTMLElement,
    options?: KsonEditorOptions,
): Promise<KsonEditor> {
    registerKsonLanguage();

    const uri = monaco.Uri.parse(options?.uri ?? 'inmemory://kson/document.kson');

    const { bridge, worker, serverCapabilities: capabilities } = await acquireLsp(options?.lspOptions);

    // Single disposal stack shared between the partial-init catch and the
    // public dispose() — pushed in construction order, popped in reverse.
    const disposables: { dispose(): void }[] = [];
    const unwind = (): void => {
        while (disposables.length) disposables.pop()!.dispose();
        releaseLsp();
    };

    try {
        // Wrap the container in a flex column so the tab bar and editor stack vertically.
        const wrapper = document.createElement('div');
        wrapper.style.cssText = 'display:flex; flex-direction:column; width:100%; height:100%;';
        container.appendChild(wrapper);
        disposables.push({ dispose: () => wrapper.remove() });

        const editorContainer = document.createElement('div');
        editorContainer.style.cssText = 'flex:1; min-height:0;';

        // Saved editor view states (scroll position, cursor, etc.) per document URI,
        // so switching tabs restores exactly where the user left off.
        const viewStates = new Map<string, monaco.editor.ICodeEditorViewState>();

        const model = monaco.editor.createModel(
            options?.value ?? '',
            KSON_LANGUAGE_ID,
            uri,
        );
        disposables.push(model);

        const editor = monaco.editor.create(editorContainer, {
            model,
            automaticLayout: true,
            minimap: { enabled: false },
            'semanticHighlighting.enabled': true,
            ...options?.editorOptions,
        });
        disposables.push(editor);

        /** Save current view state, switch to the target model, and restore its view state. */
        const switchToModel = (targetModel: monaco.editor.ITextModel): void => {
            const currentModel = editor.getModel();
            if (currentModel) {
                const vs = editor.saveViewState();
                if (vs) viewStates.set(currentModel.uri.toString(), vs);
            }

            editor.setModel(targetModel);
            editor.updateOptions({ readOnly: isBundledSchemaUri(targetModel.uri) });
            const vs = viewStates.get(targetModel.uri.toString());
            if (vs) editor.restoreViewState(vs);
        };

        const tabBar = new TabBar({
            onActivate(targetUri) {
                const targetModel = monaco.editor.getModel(monaco.Uri.parse(targetUri));
                if (targetModel) switchToModel(targetModel);
            },
            onClose(closedUri) {
                viewStates.delete(closedUri);
            },
        });
        disposables.push(tabBar);

        wrapper.appendChild(tabBar.element);
        wrapper.appendChild(editorContainer);

        // Open the initial document as a non-closeable tab.
        tabBar.open(uri.toString(), labelFromUri(uri), false);

        // Create read-only models for bundled schemas so go-to-definition can navigate to them.
        const schemaModels = createBundledSchemaModels(options?.lspOptions, bridge);
        disposables.push(schemaModels);

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
                tabBar.open(resource.toString(), labelFromUri(resource), true, isBundledSchemaUri(resource));

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
        disposables.push(openerDisposable);

        // Wire up the bridge
        disposables.push(bridge.attachToEditor(editor, KSON_LANGUAGE_ID, capabilities));

        let disposed = false;
        return {
            editor,
            bridge,
            worker,
            serverCapabilities: capabilities,
            dispose() {
                if (disposed) return;
                disposed = true;
                unwind();
            },
        };
    } catch (err) {
        unwind();
        throw err;
    }
}

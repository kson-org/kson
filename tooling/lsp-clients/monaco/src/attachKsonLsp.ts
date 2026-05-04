/**
 * Attach KSON LSP support to an existing Monaco editor.
 *
 * For consumers that already create the Monaco editor themselves (e.g.
 * `@monaco-editor/react`), this hooks the language server up to the
 * pre-existing instance instead of taking over the lifecycle.
 *
 * Lazy + refcounted: the first call spins up the worker, later calls
 * share it; the worker is torn down when the last attachment is disposed.
 *
 * Module-identity invariant: the consumer's `monaco-editor` import must
 * resolve to the same module instance as this library's.  For
 * `@monaco-editor/react`, call `loader.config({ monaco })` at module init
 * before any `<Editor>` mounts — otherwise `@monaco-editor/react` fetches
 * a second copy of monaco from a CDN and our providers register against
 * a different module than the one the editor is using.
 */

import type * as monaco from 'monaco-editor';
import { type KsonLspBridgeOptions } from './bridge/index.js';
import { createBundledSchemaModels } from './bundledSchemas.js';
import { KSON_LANGUAGE_ID, registerKsonLanguage } from './language/ksonLanguage.js';
import { acquireLsp, releaseLsp } from './lspRegistry.js';

export interface AttachKsonLspOptions {
    /** Forwarded to the language server on first acquire (ignored on later calls). */
    lspOptions?: KsonLspBridgeOptions['initializationOptions'];
}

export interface AttachKsonLspHandle {
    /** Detach from this editor and decrement the shared LSP refcount. */
    dispose(): void;
}

/**
 * Wire LSP completions/diagnostics/hover/etc. into an editor whose
 * lifecycle is owned elsewhere.  Does NOT dispose the editor.
 */
export async function attachKsonLsp(
    editor: monaco.editor.IStandaloneCodeEditor,
    options?: AttachKsonLspOptions,
): Promise<AttachKsonLspHandle> {
    registerKsonLanguage();

    // Balance the refcount if the init promise rejects — otherwise the
    // registry slot stays pinned and a retry can never re-init.
    const handle = await acquireLsp(options?.lspOptions).catch((err) => {
        releaseLsp();
        throw err;
    });

    // Single disposal stack shared between the partial-init catch and the
    // public dispose() — pushed in construction order, popped in reverse.
    const disposables: { dispose(): void }[] = [];
    const unwind = (): void => {
        while (disposables.length) disposables.pop()!.dispose();
        releaseLsp();
    };

    try {
        disposables.push(createBundledSchemaModels(options?.lspOptions, handle.bridge));
        disposables.push(handle.bridge.attachToEditor(
            editor,
            KSON_LANGUAGE_ID,
            handle.serverCapabilities,
        ));
    } catch (err) {
        unwind();
        throw err;
    }

    let disposed = false;
    return {
        dispose() {
            if (disposed) return;
            disposed = true;
            unwind();
        },
    };
}

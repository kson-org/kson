/**
 * React hook: attach the KSON language server to an editor created by
 * `@monaco-editor/react` (or any other React-managed Monaco instance).
 *
 * Capture the editor returned by `<Editor onMount>` via `useState`, then
 * pass it here.  The hook handles the async-attach race that React
 * StrictMode (and any unmount-before-resolve path) exposes: if the
 * component unmounts before `attachKsonLsp` resolves, the eventual handle
 * is disposed instead of leaking a refcount on the shared LSP worker.
 *
 * Options are read once at attach time; subsequent changes are ignored.
 * Re-mount the editor if you need to change schemas or other LSP options.
 */
import { useEffect } from 'react';
import type * as monaco from 'monaco-editor';
import {
    attachKsonLsp,
    type AttachKsonLspHandle,
    type AttachKsonLspOptions,
} from '../attachKsonLsp.js';

export function useKsonLsp(
    editor: monaco.editor.IStandaloneCodeEditor | null,
    options?: AttachKsonLspOptions,
): void {
    useEffect(() => {
        if (!editor) return;
        let cancelled = false;
        let handle: AttachKsonLspHandle | undefined;
        attachKsonLsp(editor, options).then(
            (h) => {
                if (cancelled) h.dispose();
                else handle = h;
            },
            (err) => {
                if (!cancelled) console.error('[kson] LSP attach failed:', err);
            },
        );
        return () => {
            cancelled = true;
            handle?.dispose();
        };
        // options snapshot at attach time — see JSDoc; intentionally not a dep.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [editor]);
}

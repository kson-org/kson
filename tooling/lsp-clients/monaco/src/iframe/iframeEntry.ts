/**
 * Iframe-side entry point for the KSON editor.
 *
 * This script runs inside the iframe created by KsonEditorClient.  It posts
 * `kson:ready` to the parent, waits for `kson:init` with the initial value
 * and options, creates the editor, and relays content changes back via
 * `kson:change`.
 */

import { createKsonEditor, type KsonEditor } from '../index.js';
import type { ParentMessage, InitMessage } from './iframeMessages.js';

let initializing = false;
let editor: KsonEditor | null = null;

const container = document.getElementById('editor');
if (!container) throw new Error('Missing #editor element in iframe HTML');
container.style.cssText = 'position:absolute; inset:0;';

/** Handle messages from the parent window. */
window.addEventListener('message', async (event: MessageEvent) => {
    const data = event.data as ParentMessage;
    if (!data || typeof data.type !== 'string' || !data.type.startsWith('kson:')) return;

    switch (data.type) {
        case 'kson:init':
            if (initializing || editor) return;
            initializing = true;
            editor = await init(data);
            break;

        case 'kson:setValue':
            if (editor) {
                editor.editor.getModel()?.setValue(data.value);
            }
            break;
    }
});

async function init(msg: InitMessage): Promise<KsonEditor> {
    const lspOptions = msg.schema
        ? { bundledSchemas: [msg.schema], enableBundledSchemas: true }
        : undefined;

    const ksonEditor = await createKsonEditor(container, {
        value: msg.value,
        uri: msg.uri,
        lspOptions,
        editorOptions: msg.editorOptions as Record<string, unknown> | undefined,
    });

    // Relay content changes to the parent.
    const model = ksonEditor.editor.getModel()!;
    model.onDidChangeContent(() => {
        window.parent.postMessage(
            { type: 'kson:change', value: model.getValue() },
            '*',
        );
    });

    return ksonEditor;
}

// Signal to the parent that we're ready to receive `kson:init`.
window.parent.postMessage({ type: 'kson:ready' }, '*');

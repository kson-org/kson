/**
 * Parent-side proxy for the KSON editor iframe.
 *
 * Consumers load a single `<script src="kson-editor.js">` tag, then call
 * `KsonEditor.create(container, options)`.  This module handles iframe
 * creation, the postMessage handshake, and exposes a simple synchronous
 * `getValue()` / `setValue()` / `dispose()` API.
 *
 * No npm, no bundler — works from a plain `<script>` tag.
 */

import type { InitMessage, IframeMessage } from './iframeMessages.js';

export interface KsonEditorClientOptions {
    /** Initial KSON content. */
    value?: string;

    /** Document URI for LSP identification. Defaults to 'inmemory://kson/document.kson'. */
    uri?: string;

    /** Optional JSON Schema for validation and completions. */
    schema?: { fileExtension: string; schemaContent: string };

    /** Monaco editor construction options forwarded to the iframe. */
    editorOptions?: Record<string, unknown>;

    /** Called whenever the editor content changes. */
    onChange?: (value: string) => void;

    /**
     * Base URL where `kson-editor.html` lives, without trailing slash.
     * Defaults to the directory containing the currently executing script.
     */
    baseUrl?: string;
}

export interface KsonEditorClientHandle {
    /** Returns the current editor content (synchronous — no round-trip). */
    getValue(): string;

    /** Replace the editor content. */
    setValue(value: string): void;

    /** Remove the iframe and clean up event listeners. */
    dispose(): void;
}

/**
 * Resolves the base URL from the currently executing script tag, so that
 * the iframe HTML can be located relative to `kson-editor.js`.
 */
function resolveBaseUrl(): string {
    // In an IIFE loaded via <script src="...">, `document.currentScript`
    // points to that <script> element.  We grab its `src` and strip the
    // filename to get the directory.
    if (document.currentScript && (document.currentScript as HTMLScriptElement).src) {
        const src = (document.currentScript as HTMLScriptElement).src;
        return src.substring(0, src.lastIndexOf('/'));
    }
    // Fallback: assume kson-editor.html is served from the same origin root.
    return window.location.origin;
}

// Capture at parse time (before any async code runs), because
// `document.currentScript` is only available during synchronous execution
// of the <script> tag.
const detectedBaseUrl = resolveBaseUrl();

/**
 * Create a KSON editor inside `container`.
 *
 * Returns a promise that resolves once the iframe is ready and the
 * editor is initialized.
 */
export function create(
    container: HTMLElement,
    options: KsonEditorClientOptions = {},
): Promise<KsonEditorClientHandle> {
    const baseUrl = options.baseUrl ?? detectedBaseUrl;

    return new Promise<KsonEditorClientHandle>((resolve) => {
        let currentValue = options.value ?? '';

        const iframe = document.createElement('iframe');
        iframe.style.cssText = 'width:100%; height:100%; border:none;';

        function onMessage(event: MessageEvent): void {
            // Only accept messages from our iframe.
            if (event.source !== iframe.contentWindow) return;

            const data = event.data as IframeMessage;
            if (!data || typeof data.type !== 'string' || !data.type.startsWith('kson:')) return;

            switch (data.type) {
                case 'kson:ready': {
                    const initMsg: InitMessage = {
                        type: 'kson:init',
                        value: currentValue,
                        uri: options.uri ?? 'inmemory://kson/document.kson',
                        schema: options.schema,
                        editorOptions: options.editorOptions,
                    };
                    // Safe to assert: we're inside the kson:ready handler,
                    // so the iframe is loaded and contentWindow is available.
                    iframe.contentWindow!.postMessage(initMsg, '*');

                    resolve({
                        getValue: () => currentValue,
                        setValue(value: string) {
                            currentValue = value;
                            // Optional chain: contentWindow may be null if
                            // dispose() was called or the iframe navigated away.
                            iframe.contentWindow?.postMessage(
                                { type: 'kson:setValue', value },
                                '*',
                            );
                        },
                        dispose() {
                            window.removeEventListener('message', onMessage);
                            iframe.remove();
                        },
                    });
                    break;
                }

                case 'kson:change':
                    currentValue = data.value;
                    options.onChange?.(data.value);
                    break;
            }
        }

        window.addEventListener('message', onMessage);

        // Set src *after* the listener is attached so we can't miss kson:ready.
        // Browsers won't start loading until the iframe is in the DOM, but
        // setting src last makes the safety explicit rather than implicit.
        iframe.src = `${baseUrl}/kson-editor.html`;
        container.appendChild(iframe);
    });
}

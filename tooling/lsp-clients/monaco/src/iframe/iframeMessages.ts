/**
 * Message types for the parent ↔ iframe communication protocol.
 *
 * All messages are prefixed with `kson:` to avoid collisions with other
 * postMessage traffic on the page.  These types are used by both sides
 * (iframe entry point and parent-side proxy) but carry no runtime weight —
 * they're erased at build time.
 */

// ── Parent → Iframe ─────────────────────────────────────────────────

export interface InitMessage {
    type: 'kson:init';
    value: string;
    uri: string;
    schema?: { fileExtension: string; schemaContent: string };
    editorOptions?: Record<string, unknown>;
}

export interface SetValueMessage {
    type: 'kson:setValue';
    value: string;
}

export type ParentMessage = InitMessage | SetValueMessage;

// ── Iframe → Parent ─────────────────────────────────────────────────

export interface ReadyMessage {
    type: 'kson:ready';
}

export interface ChangeMessage {
    type: 'kson:change';
    value: string;
}

export type IframeMessage = ReadyMessage | ChangeMessage;

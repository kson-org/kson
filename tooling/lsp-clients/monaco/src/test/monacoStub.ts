/**
 * Minimal stub of the monaco-editor runtime values used by lspToMonaco.
 *
 * Only the enums, Uri.parse, and Range constructor are needed — the conversion
 * functions don't use the full Monaco API.
 */

export const MarkerSeverity = { Error: 8, Warning: 4, Info: 2, Hint: 1 } as const;

export class Range {
    constructor(
        public startLineNumber: number,
        public startColumn: number,
        public endLineNumber: number,
        public endColumn: number,
    ) {}
}

export const Uri = {
    parse: (uri: string) => ({ toString: () => uri, scheme: 'file', path: uri }),
};

// Test seams: the bridge registers server commands and resolves models by uri.
// These registries let tests invoke a registered command and control the
// indentation a model reports. The `__` helpers are not part of the real
// monaco-editor API.
const registeredCommands = new Map<string, (...args: unknown[]) => unknown>();
const modelsByUri = new Map<string, unknown>();

export const editor = {
    registerCommand: (id: string, handler: (...args: unknown[]) => unknown) => {
        registeredCommands.set(id, handler);
        return { dispose: () => registeredCommands.delete(id) };
    },
    registerEditorOpener: () => ({ dispose: () => {} }),
    createModel: () => ({}),
    getModel: (uri?: { toString(): string }) =>
        uri ? modelsByUri.get(uri.toString()) ?? null : null,
    setModelMarkers: () => {},
    /** Test-only: retrieve a handler registered via registerCommand. */
    __getCommand: (id: string) => registeredCommands.get(id),
    /** Test-only: register a model so getModel(uri) resolves it. */
    __setModel: (uri: string, model: unknown) => { modelsByUri.set(uri, model); },
    /** Test-only: clear all registered commands and models. */
    __reset: () => { registeredCommands.clear(); modelsByUri.clear(); },
};

export const languages = {
    register: () => {},
    setLanguageConfiguration: () => {},
    setMonarchTokensProvider: () => {},
    registerCompletionItemProvider: () => ({ dispose: () => {} }),
    registerHoverProvider: () => ({ dispose: () => {} }),
    registerDefinitionProvider: () => ({ dispose: () => {} }),
    registerDocumentSymbolProvider: () => ({ dispose: () => {} }),
    registerDocumentHighlightProvider: () => ({ dispose: () => {} }),
    registerDocumentFormattingEditProvider: () => ({ dispose: () => {} }),
    registerCodeLensProvider: () => ({ dispose: () => {} }),
    CompletionItemKind: {
        Text: 0, Method: 1, Function: 2, Constructor: 3, Field: 4,
        Variable: 5, Class: 6, Interface: 7, Module: 8, Property: 9,
        Unit: 10, Value: 11, Enum: 12, Keyword: 13, Snippet: 14,
        Color: 15, File: 16, Reference: 17, Folder: 18, EnumMember: 19,
        Constant: 20, Struct: 21, Event: 22, Operator: 23, TypeParameter: 24,
    },
    CompletionItemInsertTextRule: { InsertAsSnippet: 4 },
    SymbolKind: {
        File: 0, Module: 1, Namespace: 2, Package: 3, Class: 4,
        Method: 5, Property: 6, Field: 7, Constructor: 8, Enum: 9,
        Interface: 10, Function: 11, Variable: 12, Constant: 13,
        String: 14, Number: 15, Boolean: 16, Array: 17, Object: 18,
        Key: 19, Null: 20, EnumMember: 21, Struct: 22, Event: 23,
        Operator: 24, TypeParameter: 25,
    },
    DocumentHighlightKind: { Text: 0, Read: 1, Write: 2 },
};

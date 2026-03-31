/**
 * Conversions between LSP protocol types and Monaco editor types.
 *
 * LSP type definitions are inlined here (rather than importing from
 * vscode-languageserver-types) to keep the main-thread bundle free
 * of any vscode-languageserver dependency.
 */

import * as monaco from 'monaco-editor';

export interface LspPosition { line: number; character: number }
export interface LspRange { start: LspPosition; end: LspPosition }
export interface LspLocation { uri: string; range: LspRange }
export interface LspTextEdit { range: LspRange; newText: string }

export interface LspDiagnostic {
    range: LspRange;
    message: string;
    severity?: number;
    code?: number | string;
    source?: string;
}

export interface LspCompletionItem {
    label: string;
    kind?: number;
    detail?: string;
    documentation?: string | { kind: string; value: string };
    insertText?: string;
    insertTextFormat?: number;
    textEdit?: LspTextEdit & { insert?: LspRange; replace?: LspRange };
    filterText?: string;
    sortText?: string;
    additionalTextEdits?: LspTextEdit[];
}

export interface LspCompletionList {
    isIncomplete: boolean;
    items: LspCompletionItem[];
}

export interface LspHover {
    contents: string | { kind: string; value: string } | Array<string | { language: string; value: string }>;
    range?: LspRange;
}

export interface LspDocumentSymbol {
    name: string;
    detail?: string;
    kind: number;
    range: LspRange;
    selectionRange: LspRange;
    children?: LspDocumentSymbol[];
}

export interface LspDocumentHighlight {
    range: LspRange;
    kind?: number;
}

export interface LspCodeLens {
    range: LspRange;
    command?: { title: string; command: string; arguments?: unknown[] };
}

export interface LspSemanticTokens {
    data: number[];
}

export interface LspSemanticTokensLegend {
    tokenTypes: string[];
    tokenModifiers: string[];
}

/** LSP (0-based line/char) → Monaco (1-based line/column) */
export function toMonacoPosition(pos: LspPosition): monaco.IPosition {
    return { lineNumber: pos.line + 1, column: pos.character + 1 };
}

/** Monaco (1-based) → LSP (0-based) */
export function toLspPosition(pos: monaco.IPosition): LspPosition {
    return { line: pos.lineNumber - 1, character: pos.column - 1 };
}

export function toMonacoRange(range: LspRange): monaco.IRange {
    return {
        startLineNumber: range.start.line + 1,
        startColumn: range.start.character + 1,
        endLineNumber: range.end.line + 1,
        endColumn: range.end.character + 1,
    };
}

const SEVERITY_MAP: Record<number, monaco.MarkerSeverity> = {
    1: monaco.MarkerSeverity.Error,
    2: monaco.MarkerSeverity.Warning,
    3: monaco.MarkerSeverity.Info,
    4: monaco.MarkerSeverity.Hint,
};

export function toMonacoMarkers(diagnostics: LspDiagnostic[]): monaco.editor.IMarkerData[] {
    return diagnostics.map((d) => ({
        ...toMonacoRange(d.range),
        message: d.message,
        severity: SEVERITY_MAP[d.severity ?? 1] ?? monaco.MarkerSeverity.Error,
        source: d.source,
        code: d.code != null ? String(d.code) : undefined,
    }));
}

// LSP CompletionItemKind → Monaco CompletionItemKind
const COMPLETION_KIND_MAP: Record<number, monaco.languages.CompletionItemKind> = {
    1:  monaco.languages.CompletionItemKind.Text,
    2:  monaco.languages.CompletionItemKind.Method,
    3:  monaco.languages.CompletionItemKind.Function,
    4:  monaco.languages.CompletionItemKind.Constructor,
    5:  monaco.languages.CompletionItemKind.Field,
    6:  monaco.languages.CompletionItemKind.Variable,
    7:  monaco.languages.CompletionItemKind.Class,
    8:  monaco.languages.CompletionItemKind.Interface,
    9:  monaco.languages.CompletionItemKind.Module,
    10: monaco.languages.CompletionItemKind.Property,
    11: monaco.languages.CompletionItemKind.Unit,
    12: monaco.languages.CompletionItemKind.Value,
    13: monaco.languages.CompletionItemKind.Enum,
    14: monaco.languages.CompletionItemKind.Keyword,
    15: monaco.languages.CompletionItemKind.Snippet,
    16: monaco.languages.CompletionItemKind.Color,
    17: monaco.languages.CompletionItemKind.File,
    18: monaco.languages.CompletionItemKind.Reference,
    19: monaco.languages.CompletionItemKind.Folder,
    20: monaco.languages.CompletionItemKind.EnumMember,
    21: monaco.languages.CompletionItemKind.Constant,
    22: monaco.languages.CompletionItemKind.Struct,
    23: monaco.languages.CompletionItemKind.Event,
    24: monaco.languages.CompletionItemKind.Operator,
    25: monaco.languages.CompletionItemKind.TypeParameter,
};

function toMonacoCompletionItem(
    item: LspCompletionItem,
    range: monaco.IRange,
): monaco.languages.CompletionItem {
    let documentation: monaco.languages.CompletionItem['documentation'];
    if (typeof item.documentation === 'string') {
        documentation = item.documentation;
    } else if (item.documentation && typeof item.documentation === 'object') {
        documentation = {
            value: item.documentation.value,
            isTrusted: true,
        };
    }

    let insertTextRules: monaco.languages.CompletionItemInsertTextRule | undefined;
    if (item.insertTextFormat === 2) {
        insertTextRules = monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet;
    }

    return {
        label: item.label,
        kind: COMPLETION_KIND_MAP[item.kind ?? 1] ?? monaco.languages.CompletionItemKind.Text,
        detail: item.detail,
        documentation,
        insertText: item.insertText ?? item.label,
        insertTextRules,
        filterText: item.filterText,
        sortText: item.sortText,
        range,
    };
}

export function toMonacoCompletions(
    result: LspCompletionList | LspCompletionItem[] | null,
    range: monaco.IRange,
): monaco.languages.CompletionList {
    if (!result) return { suggestions: [] };

    const items = Array.isArray(result) ? result : result.items;
    return {
        incomplete: Array.isArray(result) ? false : result.isIncomplete,
        suggestions: items.map((item) => toMonacoCompletionItem(item, range)),
    };
}

export function toMonacoHover(hover: LspHover | null): monaco.languages.Hover | null {
    if (!hover) return null;

    let contents: monaco.IMarkdownString[];
    if (typeof hover.contents === 'string') {
        contents = [{ value: hover.contents }];
    } else if (Array.isArray(hover.contents)) {
        contents = hover.contents.map((c) =>
            typeof c === 'string' ? { value: c } : { value: `\`\`\`${c.language}\n${c.value}\n\`\`\`` },
        );
    } else {
        contents = [{ value: hover.contents.value }];
    }

    return {
        contents,
        range: hover.range ? toMonacoRange(hover.range) : undefined,
    };
}

const SYMBOL_KIND_MAP: Record<number, monaco.languages.SymbolKind> = {
    1:  monaco.languages.SymbolKind.File,
    2:  monaco.languages.SymbolKind.Module,
    3:  monaco.languages.SymbolKind.Namespace,
    4:  monaco.languages.SymbolKind.Package,
    5:  monaco.languages.SymbolKind.Class,
    6:  monaco.languages.SymbolKind.Method,
    7:  monaco.languages.SymbolKind.Property,
    8:  monaco.languages.SymbolKind.Field,
    9:  monaco.languages.SymbolKind.Constructor,
    10: monaco.languages.SymbolKind.Enum,
    11: monaco.languages.SymbolKind.Interface,
    12: monaco.languages.SymbolKind.Function,
    13: monaco.languages.SymbolKind.Variable,
    14: monaco.languages.SymbolKind.Constant,
    15: monaco.languages.SymbolKind.String,
    16: monaco.languages.SymbolKind.Number,
    17: monaco.languages.SymbolKind.Boolean,
    18: monaco.languages.SymbolKind.Array,
    19: monaco.languages.SymbolKind.Object,
    20: monaco.languages.SymbolKind.Key,
    21: monaco.languages.SymbolKind.Null,
    22: monaco.languages.SymbolKind.EnumMember,
    23: monaco.languages.SymbolKind.Struct,
    24: monaco.languages.SymbolKind.Event,
    25: monaco.languages.SymbolKind.Operator,
    26: monaco.languages.SymbolKind.TypeParameter,
};

function toMonacoDocumentSymbol(sym: LspDocumentSymbol): monaco.languages.DocumentSymbol {
    return {
        name: sym.name,
        detail: sym.detail ?? '',
        kind: SYMBOL_KIND_MAP[sym.kind] ?? monaco.languages.SymbolKind.Variable,
        range: toMonacoRange(sym.range),
        selectionRange: toMonacoRange(sym.selectionRange),
        children: sym.children?.map(toMonacoDocumentSymbol),
        tags: [],
    };
}

export function toMonacoDocumentSymbols(
    symbols: LspDocumentSymbol[] | null,
): monaco.languages.DocumentSymbol[] {
    return symbols?.map(toMonacoDocumentSymbol) ?? [];
}

const HIGHLIGHT_KIND_MAP: Record<number, monaco.languages.DocumentHighlightKind> = {
    1: monaco.languages.DocumentHighlightKind.Text,
    2: monaco.languages.DocumentHighlightKind.Read,
    3: monaco.languages.DocumentHighlightKind.Write,
};

export function toMonacoDocumentHighlights(
    highlights: LspDocumentHighlight[] | null,
): monaco.languages.DocumentHighlight[] {
    return (highlights ?? []).map((h) => ({
        range: toMonacoRange(h.range),
        kind: HIGHLIGHT_KIND_MAP[h.kind ?? 1] ?? monaco.languages.DocumentHighlightKind.Text,
    }));
}

export function toMonacoTextEdits(edits: LspTextEdit[] | null): monaco.languages.TextEdit[] {
    return (edits ?? []).map((e) => ({
        range: toMonacoRange(e.range),
        text: e.newText,
    }));
}

export function toMonacoCodeLenses(lenses: LspCodeLens[] | null): monaco.languages.CodeLens[] {
    return (lenses ?? []).map((lens) => ({
        range: toMonacoRange(lens.range),
        command: lens.command ? {
            id: lens.command.command,
            title: lens.command.title,
            arguments: lens.command.arguments,
        } : undefined,
    }));
}

export interface LspDefinitionLink {
    targetUri: string;
    targetRange: LspRange;
    targetSelectionRange: LspRange;
    originSelectionRange?: LspRange;
}

type LspDefinitionResult = LspLocation | LspLocation[] | LspDefinitionLink[] | null;

function isDefinitionLink(item: LspLocation | LspDefinitionLink): item is LspDefinitionLink {
    return 'targetUri' in item;
}

export function toMonacoDefinition(
    result: LspDefinitionResult,
): monaco.languages.LocationLink[] | null {
    if (!result) return null;

    const items = Array.isArray(result) ? result : [result];
    if (items.length === 0) return null;

    return items.map((item) => {
        if (isDefinitionLink(item)) {
            return {
                uri: monaco.Uri.parse(item.targetUri),
                range: toMonacoRange(item.targetRange),
                originSelectionRange: item.originSelectionRange
                    ? toMonacoRange(item.originSelectionRange)
                    : undefined,
                targetSelectionRange: toMonacoRange(item.targetSelectionRange),
            };
        }
        const range = toMonacoRange(item.range);
        return {
            uri: monaco.Uri.parse(item.uri),
            range,
            targetSelectionRange: range,
        };
    });
}

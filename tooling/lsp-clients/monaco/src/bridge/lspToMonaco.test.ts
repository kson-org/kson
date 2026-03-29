import { describe, it, expect } from 'vitest';
import {
    toMonacoPosition,
    toLspPosition,
    toMonacoRange,
    toMonacoMarkers,
    toMonacoCompletions,
    toMonacoHover,
    toMonacoDocumentSymbols,
    toMonacoDocumentHighlights,
    toMonacoTextEdits,
    toMonacoCodeLenses,
    toMonacoDefinition,
} from './lspToMonaco.js';

describe('position conversions', () => {
    it('converts LSP (0-based) to Monaco (1-based)', () => {
        expect(toMonacoPosition({ line: 0, character: 0 })).toEqual({ lineNumber: 1, column: 1 });
        expect(toMonacoPosition({ line: 5, character: 10 })).toEqual({ lineNumber: 6, column: 11 });
    });

    it('converts Monaco (1-based) to LSP (0-based)', () => {
        expect(toLspPosition({ lineNumber: 1, column: 1 })).toEqual({ line: 0, character: 0 });
        expect(toLspPosition({ lineNumber: 6, column: 11 })).toEqual({ line: 5, character: 10 });
    });

    it('round-trips positions', () => {
        const lsp = { line: 3, character: 7 };
        expect(toLspPosition(toMonacoPosition(lsp))).toEqual(lsp);
    });
});

describe('toMonacoRange', () => {
    it('converts an LSP range to a Monaco range', () => {
        const result = toMonacoRange({
            start: { line: 0, character: 0 },
            end: { line: 2, character: 5 },
        });
        expect(result).toEqual({
            startLineNumber: 1, startColumn: 1,
            endLineNumber: 3, endColumn: 6,
        });
    });
});

describe('toMonacoMarkers', () => {
    it('converts LSP diagnostics to Monaco markers', () => {
        const markers = toMonacoMarkers([
            {
                range: { start: { line: 0, character: 0 }, end: { line: 0, character: 5 } },
                message: 'error here',
                severity: 1,
                source: 'kson',
            },
        ]);
        expect(markers).toHaveLength(1);
        expect(markers[0].message).toBe('error here');
        expect(markers[0].severity).toBe(8); // MarkerSeverity.Error
        expect(markers[0].source).toBe('kson');
    });

    it('maps all four LSP severities', () => {
        const markers = toMonacoMarkers([1, 2, 3, 4].map((severity) => ({
            range: { start: { line: 0, character: 0 }, end: { line: 0, character: 1 } },
            message: `sev-${severity}`,
            severity,
        })));
        expect(markers.map((m) => m.severity)).toEqual([8, 4, 2, 1]);
    });

    it('defaults to Error when severity is missing', () => {
        const markers = toMonacoMarkers([{
            range: { start: { line: 0, character: 0 }, end: { line: 0, character: 1 } },
            message: 'no severity',
        }]);
        expect(markers[0].severity).toBe(8);
    });
});

describe('toMonacoCompletions', () => {
    const range = { startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: 5 };

    it('returns empty suggestions for null', () => {
        expect(toMonacoCompletions(null, range)).toEqual({ suggestions: [] });
    });

    it('converts a CompletionList', () => {
        const result = toMonacoCompletions({
            isIncomplete: true,
            items: [{ label: 'name', kind: 10, insertText: '"name"' }],
        }, range);
        expect(result.incomplete).toBe(true);
        expect(result.suggestions).toHaveLength(1);
        expect(result.suggestions[0].label).toBe('name');
        expect(result.suggestions[0].insertText).toBe('"name"');
    });

    it('converts a plain array of CompletionItems', () => {
        const result = toMonacoCompletions(
            [{ label: 'true' }, { label: 'false' }],
            range,
        );
        expect(result.incomplete).toBe(false);
        expect(result.suggestions).toHaveLength(2);
    });

    it('uses label as insertText when insertText is absent', () => {
        const result = toMonacoCompletions([{ label: 'hello' }], range);
        expect(result.suggestions[0].insertText).toBe('hello');
    });

    it('sets snippet insert rule for insertTextFormat 2', () => {
        const result = toMonacoCompletions(
            [{ label: 'snip', insertText: '${1:value}', insertTextFormat: 2 }],
            range,
        );
        expect(result.suggestions[0].insertTextRules).toBe(4); // InsertAsSnippet
    });
});

describe('toMonacoHover', () => {
    it('returns null for null', () => {
        expect(toMonacoHover(null)).toBeNull();
    });

    it('converts a string hover', () => {
        const result = toMonacoHover({ contents: 'hello' });
        expect(result?.contents).toEqual([{ value: 'hello' }]);
    });

    it('converts a MarkupContent hover', () => {
        const result = toMonacoHover({ contents: { kind: 'markdown', value: '**bold**' } });
        expect(result?.contents).toEqual([{ value: '**bold**' }]);
    });

    it('converts an array of MarkedString values', () => {
        const result = toMonacoHover({
            contents: [
                'plain text',
                { language: 'json', value: '{}' },
            ],
        });
        expect(result?.contents).toHaveLength(2);
        expect(result?.contents[0]).toEqual({ value: 'plain text' });
        expect(result?.contents[1]).toEqual({ value: '```json\n{}\n```' });
    });

    it('includes range when present', () => {
        const result = toMonacoHover({
            contents: 'hi',
            range: { start: { line: 1, character: 0 }, end: { line: 1, character: 3 } },
        });
        expect(result?.range).toEqual({
            startLineNumber: 2, startColumn: 1,
            endLineNumber: 2, endColumn: 4,
        });
    });
});

describe('toMonacoDocumentSymbols', () => {
    it('returns empty array for null', () => {
        expect(toMonacoDocumentSymbols(null)).toEqual([]);
    });

    it('converts symbols with children', () => {
        const range = { start: { line: 0, character: 0 }, end: { line: 5, character: 1 } };
        const result = toMonacoDocumentSymbols([{
            name: 'root',
            kind: 19, // Object
            range,
            selectionRange: range,
            children: [{
                name: 'child',
                kind: 14, // Constant
                range,
                selectionRange: range,
            }],
        }]);
        expect(result).toHaveLength(1);
        expect(result[0].name).toBe('root');
        expect(result[0].children).toHaveLength(1);
        expect(result[0].children![0].name).toBe('child');
    });
});

describe('toMonacoDocumentHighlights', () => {
    it('returns empty array for null', () => {
        expect(toMonacoDocumentHighlights(null)).toEqual([]);
    });

    it('maps highlight kinds', () => {
        const range = { start: { line: 0, character: 0 }, end: { line: 0, character: 3 } };
        const result = toMonacoDocumentHighlights([
            { range, kind: 2 }, // Read
            { range, kind: 3 }, // Write
        ]);
        expect(result[0].kind).toBe(1); // DocumentHighlightKind.Read
        expect(result[1].kind).toBe(2); // DocumentHighlightKind.Write
    });
});

describe('toMonacoTextEdits', () => {
    it('returns empty array for null', () => {
        expect(toMonacoTextEdits(null)).toEqual([]);
    });

    it('converts text edits', () => {
        const result = toMonacoTextEdits([{
            range: { start: { line: 0, character: 0 }, end: { line: 0, character: 5 } },
            newText: 'hello',
        }]);
        expect(result).toHaveLength(1);
        expect(result[0].text).toBe('hello');
        expect(result[0].range).toEqual({
            startLineNumber: 1, startColumn: 1,
            endLineNumber: 1, endColumn: 6,
        });
    });
});

describe('toMonacoCodeLenses', () => {
    it('returns empty array for null', () => {
        expect(toMonacoCodeLenses(null)).toEqual([]);
    });

    it('converts code lenses with commands', () => {
        const result = toMonacoCodeLenses([{
            range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
            command: { title: 'plain', command: 'kson.plainFormat', arguments: [{ uri: 'test' }] },
        }]);
        expect(result).toHaveLength(1);
        expect(result[0].command?.id).toBe('kson.plainFormat');
        expect(result[0].command?.title).toBe('plain');
        expect(result[0].command?.arguments).toEqual([{ uri: 'test' }]);
    });
});

describe('toMonacoDefinition', () => {
    it('returns null for null', () => {
        expect(toMonacoDefinition(null)).toBeNull();
    });

    it('returns null for empty array', () => {
        expect(toMonacoDefinition([])).toBeNull();
    });

    it('converts a single Location', () => {
        const result = toMonacoDefinition({
            uri: 'file:///test.kson',
            range: { start: { line: 1, character: 0 }, end: { line: 1, character: 5 } },
        });
        expect(result).toHaveLength(1);
        expect(result![0].uri.toString()).toBe('file:///test.kson');
    });

    it('converts DefinitionLinks', () => {
        const result = toMonacoDefinition([{
            targetUri: 'file:///schema.kson',
            targetRange: { start: { line: 0, character: 0 }, end: { line: 10, character: 0 } },
            targetSelectionRange: { start: { line: 2, character: 0 }, end: { line: 2, character: 5 } },
            originSelectionRange: { start: { line: 0, character: 0 }, end: { line: 0, character: 3 } },
        }]);
        expect(result).toHaveLength(1);
        expect(result![0].uri.toString()).toBe('file:///schema.kson');
        expect(result![0].originSelectionRange).toBeDefined();
    });
});

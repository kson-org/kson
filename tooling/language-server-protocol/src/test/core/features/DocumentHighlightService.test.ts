import {describe, it} from 'mocha';
import assert from 'assert';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentHighlightKind, Position} from 'vscode-languageserver';
import {Kson} from 'kson';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {DocumentHighlightService} from '../../../core/features/DocumentHighlightService.js';
import {DocumentSymbolService} from "../../../core/features/DocumentSymbolService";

describe('DocumentHighlightService', () => {
    const highlightService = new DocumentHighlightService();
    const symbolService = new DocumentSymbolService();

    // Helper function to check if array includes an item with deep equality
    function assertDeepIncludes(array: any[], expected: any, message?: string) {
        const found = array.some(item => {
            try {
                assert.deepStrictEqual(item, expected);
                return true;
            } catch {
                return false;
            }
        });
        assert(found, message || `Array does not include expected item: ${JSON.stringify(expected)}`);
    }

    function createKsonDocument(content: string): KsonDocument {
        const textDocument = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysisResult = Kson.getInstance().analyze(content);
        const document = new KsonDocument(textDocument, analysisResult);
        symbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue);
        return document;
    }

    function position(line: number, character: number): Position {
        return {line, character};
    }

    it('should highlight all sibling property keys when cursor is on a property key', () => {
        const content = [
            'name: John',
            'age: 30',
            'city: "New York"',
        ].join('\n')
        const document = createKsonDocument(content);

        // Position on "name" key
        const highlights = highlightService.getDocumentHighlights(document, position(0, 3));

        assert.strictEqual(highlights.length, 3);
        assert.strictEqual(highlights.every(h => h.kind === DocumentHighlightKind.Read), true);

        // Check that all property keys are highlighted
        const ranges = highlights.map(h => h.range);
        assertDeepIncludes(ranges, {start: {line: 0, character: 0}, end: {line: 0, character: 4}}); // "name"
        assertDeepIncludes(ranges, {start: {line: 1, character: 0}, end: {line: 1, character: 3}}); // "age"
        assertDeepIncludes(ranges, {start: {line: 2, character: 0}, end: {line: 2, character: 4}}); // "city"
    });

    describe('Property key highlighting', () => {
        it('should return empty array when cursor is on the object itself', () => {
            const content = [
                '{',
                'name: John',
                'age: 30',
                '}'
            ].join('\n')
            const document = createKsonDocument(content);

            // Position after opening brace
            const highlights = highlightService.getDocumentHighlights(document, position(0, 1));

            // Should return empty array since we only highlight property keys
            assert.strictEqual(highlights.length, 0);
        });

        it('should return empty array when cursor is on a property value', () => {
            const content = [
                'name: John',
                'age: 30',
            ].join('\n')
            const document = createKsonDocument(content);

            // Position on "John" value
            const highlights = highlightService.getDocumentHighlights(document, position(1, 7));

            // Should return empty array since we only highlight property keys
            assert.strictEqual(highlights.length, 0);
        });
    });

    describe('Nested object highlighting', () => {
        it('should highlight only sibling keys at the same level', () => {
            const content = [
                "outer: value",
                "nested: {",
                "  inner1: value1",
                "  inner2: value2",
                "}"
            ].join('\n')
            const document = createKsonDocument(content);

            // Position on "inner1" key
            const highlights = highlightService.getDocumentHighlights(document, position(3, 4));

            assert.strictEqual(highlights.length, 2);

            // Should highlight inner1 and inner2, not outer and nested
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 2, character: 2}, end: {line: 2, character: 8}}); // "inner2"
            assertDeepIncludes(ranges, {start: {line: 3, character: 2}, end: {line: 3, character: 8}}); // "inner1"
        });

        it('should highlight outer keys when cursor is on outer property key', () => {
            const content = [
                "outer: value",
                "nested: ",
                "  inner: value",
                ].join('\n');
            const document = createKsonDocument(content);

            // Position on "outer" key
            const highlights = highlightService.getDocumentHighlights(document, position(1, 1));

            assert.strictEqual(highlights.length, 2);

            // Should highlight outer and nested, not inner
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 0, character: 0}, end: {line: 0, character: 5}}); // "outer"
            assertDeepIncludes(ranges, {start: {line: 1, character: 0}, end: {line: 1, character: 6}}); // "nested"
        });

        it('should handle deeply nested structures', () => {
            const content = [
                '{',
                '    "level1": {',
                '        "level2": {',
                '            "prop1": "value1",',
                '            "prop2": "value2",',
                '            "prop3": "value3"',
                '        }',
                '    }',
                '}'
            ].join('\n');
            const document = createKsonDocument(content);

            // Position on "prop2" key
            const highlights = highlightService.getDocumentHighlights(document, position(4, 14));

            assert.strictEqual(highlights.length, 3);

            // Should highlight all three properties at the same level
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 3, character: 12}, end: {line: 3, character: 19}}); // "prop1"
            assertDeepIncludes(ranges, {start: {line: 4, character: 12}, end: {line: 4, character: 19}}); // "prop2"
            assertDeepIncludes(ranges, {start: {line: 5, character: 12}, end: {line: 5, character: 19}}); // "prop3"
        });
    });

    describe('Array of objects', () => {
        it('should highlight object keys within the same object in array', () => {
            const content = [
                '[',
                '    {',
                '        "id": 1,',
                '        "name": "Item 1"',
                '    },',
                '    {',
                '        "id": 2,',
                '        "name": "Item 2"',
                '    }',
                ']'
            ].join('\n');
            const document = createKsonDocument(content);

            // Position on "id" in first object
            const highlights = highlightService.getDocumentHighlights(document, position(2, 9));

            assert.strictEqual(highlights.length, 2);

            // Should highlight keys from first object only
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 2, character: 8}, end: {line: 2, character: 12}}); // "id"
            assertDeepIncludes(ranges, {start: {line: 3, character: 8}, end: {line: 3, character: 14}}); // "name"
        });

        it('should not highlight keys from different objects in array', () => {
            const content = [
                '[',
                '    {',
                '        "id": 1,',
                '        "name": "Item 1"',
                '    },',
                '    {',
                '        "id": 2,',
                '        "name": "Item 2"',
                '    }',
                ']'
            ].join('\n');
            const document = createKsonDocument(content);

            // Position on "id" in second object
            const highlights = highlightService.getDocumentHighlights(document, position(6, 9));

            assert.strictEqual(highlights.length, 2);

            // Should highlight keys from second object only
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 6, character: 8}, end: {line: 6, character: 12}}); // "id"
            assertDeepIncludes(ranges, {start: {line: 7, character: 8}, end: {line: 7, character: 14}}); // "name"
        });
    });

    describe('Complex property scenarios', () => {
        it('should handle properties with object values', () => {
            const content = [
                '{',
                '    "simple": "value",',
                '    "complex": {',
                '        "nested": "data"',
                '    },',
                '    "another": "value"',
                '}'
            ].join('\n');
            const document = createKsonDocument(content);

            // Position on "complex" key (which has an object value)
            const highlights = highlightService.getDocumentHighlights(document, position(2, 6));

            assert.strictEqual(highlights.length, 3);

            // Should highlight all three top-level keys
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 1, character: 4}, end: {line: 1, character: 12}}); // "simple"
            assertDeepIncludes(ranges, {start: {line: 2, character: 4}, end: {line: 2, character: 13}}); // "complex"
            assertDeepIncludes(ranges, {start: {line: 5, character: 4}, end: {line: 5, character: 13}}); // "another"
        });

        it('should handle duplicate property keys', () => {
            const content = [
                '{',
                '    "value": "first",',
                '    "value": "second",',
                '    "value": "third",',
                '    "other": "data"',
                '}'
            ].join('\n');
            const document = createKsonDocument(content);

            // Position on the last "value" key
            const highlights = highlightService.getDocumentHighlights(document, position(3, 6));

            // KSON may deduplicate keys, so we check for at least 2 highlights
            assert.ok(highlights.length == 2, `Expected at least 2 highlights, got ${highlights.length}`);

            // Should at least highlight "value" and "other"
            const ranges = highlights.map(h => h.range);

            // Check that at least one "value" key is highlighted
            const valueHighlights = ranges.filter(r =>
                r.start.character === 4 && r.end.character === 11
            );
            assert.ok(valueHighlights.length >= 1, 'Should highlight at least one "value" key');

            // Check that "other" is highlighted
            assertDeepIncludes(ranges, {start: {line: 4, character: 4}, end: {line: 4, character: 11}}); // "other"
        });
    });

    describe('Edge cases', () => {
        it('should return empty array for empty object', () => {
            const content = '{}';
            const document = createKsonDocument(content);

            const highlights = highlightService.getDocumentHighlights(document, position(0, 1));

            // Should return empty array since there are no property keys to highlight
            assert.strictEqual(highlights.length, 0);
        });

        it('should return empty array when cursor is outside any symbol', () => {
            const content = [
                '{',
                '    "key": "value"',
                '}',
                ''
            ].join('\n');
            const document = createKsonDocument(content);

            // Position after closing brace on empty line
            const highlights = highlightService.getDocumentHighlights(document, position(3, 0));

            assert.strictEqual(highlights.length, 0);
        });

        it('should handle invalid JSON gracefully', () => {
            const content = '{ invalid }';
            const document = createKsonDocument(content);

            const highlights = highlightService.getDocumentHighlights(document, position(0, 5));

            assert.strictEqual(highlights.length, 0);
        });

        it('should handle unquoted keys in KSON', () => {
            const content = [
                '{',
                '    key1: "value1",',
                '    key2: "value2"',
                '}'
            ].join('\n');
            const document = createKsonDocument(content);

            // Position on unquoted key
            const highlights = highlightService.getDocumentHighlights(document, position(1, 5));

            assert.strictEqual(highlights.length, 2);

            // Should highlight both unquoted keys
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 1, character: 4}, end: {line: 1, character: 8}}); // key1
            assertDeepIncludes(ranges, {start: {line: 2, character: 4}, end: {line: 2, character: 8}}); // key2
        });

        it('should return empty array when cursor is on array element', () => {
            const content = [
                '{',
                '    "items": ["a", "b", "c"]',
                '}'
            ].join('\n');
            const document = createKsonDocument(content);

            // Position in array element "b"
            const highlights = highlightService.getDocumentHighlights(document, position(1, 20));

            // Should return empty array since we only highlight property keys
            assert.strictEqual(highlights.length, 0);
        });

        it('should handle single property object', () => {
            const content = [
                '{',
                '    "onlyKey": "onlyValue"',
                '}'
            ].join('\n');
            const document = createKsonDocument(content);

            // Position on the only key
            const highlights = highlightService.getDocumentHighlights(document, position(1, 6));

            // Should still highlight the single key
            assert.strictEqual(highlights.length, 1);
            assert.deepStrictEqual(highlights[0].range, {
                start: {line: 1, character: 4},
                end: {line: 1, character: 13}
            });
        });
    });
});
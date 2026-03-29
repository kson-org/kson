import {describe, it} from 'mocha';
import assert from 'assert';
import {DocumentHighlightKind} from 'vscode-languageserver';
import {DocumentHighlightService} from '../../../core/features/DocumentHighlightService.js';
import {pos} from '../../TestHelpers.js';
import {KsonTooling} from 'kson-tooling';

describe('DocumentHighlightService', () => {
    const highlightService = new DocumentHighlightService();

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

    it('should highlight all sibling property keys when cursor is on a property key', () => {
        const content = [
            'name: John',
            'age: 30',
            'city: "New York"',
        ].join('\n');

        // Position on "name" key
        const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(0, 3));

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
            ].join('\n');

            // Position after opening brace
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(0, 1));

            // Should return empty array since we only highlight property keys
            assert.strictEqual(highlights.length, 0);
        });

        it('should return empty array when cursor is on a property value', () => {
            const content = [
                'name: John',
                'age: 30',
            ].join('\n');

            // Position on "John" value
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(1, 7));

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
            ].join('\n');

            // Position on "inner2" key
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(3, 4));

            assert.strictEqual(highlights.length, 2);

            // Should highlight inner1 and inner2, not outer and nested
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 2, character: 2}, end: {line: 2, character: 8}}); // "inner1"
            assertDeepIncludes(ranges, {start: {line: 3, character: 2}, end: {line: 3, character: 8}}); // "inner2"
        });

        it('should highlight outer keys when cursor is on outer property key', () => {
            const content = [
                "outer: value",
                "nested: ",
                "  inner: value",
            ].join('\n');

            // Position on "nested" key
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(1, 1));

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

            // Position on "prop2" key
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(4, 14));

            assert.strictEqual(highlights.length, 3);

            // Should highlight all three properties at the same level
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 3, character: 13}, end: {line: 3, character: 18}}); // "prop1"
            assertDeepIncludes(ranges, {start: {line: 4, character: 13}, end: {line: 4, character: 18}}); // "prop2"
            assertDeepIncludes(ranges, {start: {line: 5, character: 13}, end: {line: 5, character: 18}}); // "prop3"
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

            // Position on "id" in first object
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(2, 9));

            assert.strictEqual(highlights.length, 2);

            // Should highlight keys from first object only
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 2, character: 9}, end: {line: 2, character: 11}}); // "id"
            assertDeepIncludes(ranges, {start: {line: 3, character: 9}, end: {line: 3, character: 13}}); // "name"
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

            // Position on "id" in second object
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(6, 9));

            assert.strictEqual(highlights.length, 2);

            // Should highlight keys from second object only
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 6, character: 9}, end: {line: 6, character: 11}}); // "id"
            assertDeepIncludes(ranges, {start: {line: 7, character: 9}, end: {line: 7, character: 13}}); // "name"
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

            // Position on "complex" key (which has an object value)
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(2, 6));

            assert.strictEqual(highlights.length, 3);

            // Should highlight all three top-level keys
            const ranges = highlights.map(h => h.range);
            assertDeepIncludes(ranges, {start: {line: 1, character: 5}, end: {line: 1, character: 11}}); // "simple"
            assertDeepIncludes(ranges, {start: {line: 2, character: 5}, end: {line: 2, character: 12}}); // "complex"
            assertDeepIncludes(ranges, {start: {line: 5, character: 5}, end: {line: 5, character: 12}}); // "another"
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

            // Position on the last "value" key
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(3, 6));

            // KSON may deduplicate keys, so we check for at least 2 highlights
            assert.ok(highlights.length == 2, `Expected at least 2 highlights, got ${highlights.length}`);

            // Should at least highlight "value" and "other"
            const ranges = highlights.map(h => h.range);

            // Check that at least one "value" key is highlighted
            const valueHighlights = ranges.filter(r =>
                r.start.character === 5 && r.end.character === 10
            );
            assert.ok(valueHighlights.length >= 1, 'Should highlight at least one "value" key');

            // Check that "other" is highlighted
            assertDeepIncludes(ranges, {start: {line: 4, character: 5}, end: {line: 4, character: 10}}); // "other"
        });
    });

    describe('Edge cases', () => {
        it('should return empty array for empty object', () => {
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse('{}'), pos(0, 1));

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

            // Position after closing brace on empty line
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(3, 0));

            assert.strictEqual(highlights.length, 0);
        });

        it('should handle invalid JSON gracefully', () => {
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse('{ invalid }'), pos(0, 5));

            assert.strictEqual(highlights.length, 0);
        });

        it('should handle unquoted keys in KSON', () => {
            const content = [
                '{',
                '    key1: "value1",',
                '    key2: "value2"',
                '}'
            ].join('\n');

            // Position on unquoted key
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(1, 5));

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

            // Position in array element "b"
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(1, 20));

            // Should return empty array since we only highlight property keys
            assert.strictEqual(highlights.length, 0);
        });

        it('should handle single property object', () => {
            const content = [
                '{',
                '    "onlyKey": "onlyValue"',
                '}'
            ].join('\n');

            // Position on the only key
            const highlights = highlightService.getDocumentHighlights(KsonTooling.getInstance().parse(content), pos(1, 6));

            // Should still highlight the single key
            assert.strictEqual(highlights.length, 1);
            assert.deepStrictEqual(highlights[0].range, {
                start: {line: 1, character: 5},
                end: {line: 1, character: 12}
            });
        });
    });
});

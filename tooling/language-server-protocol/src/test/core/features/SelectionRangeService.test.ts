import {describe, it} from 'mocha';
import assert from 'assert';
import {SelectionRangeService} from '../../../core/features/SelectionRangeService.js';
import {createKsonDocument} from '../../TestHelpers.js';
import {Position, SelectionRange} from 'vscode-languageserver';

/**
 * Flatten a SelectionRange parent chain into an array of ranges
 * from innermost to outermost.
 */
function flattenChain(sr: SelectionRange): Array<{startLine: number, startChar: number, endLine: number, endChar: number}> {
    const result = [];
    let current: SelectionRange | undefined = sr;
    while (current) {
        result.push({
            startLine: current.range.start.line,
            startChar: current.range.start.character,
            endLine: current.range.end.line,
            endChar: current.range.end.character
        });
        current = current.parent;
    }
    return result;
}

describe('SelectionRangeService', () => {
    const service = new SelectionRangeService();

    it('should return document range for empty document', () => {
        const doc = createKsonDocument('');
        const results = service.getSelectionRanges(doc, [{line: 0, character: 0}]);
        assert.strictEqual(results.length, 1);
        assert.ok(results[0].range);
    });

    it('should return a chain for simple string value', () => {
        const doc = createKsonDocument('"hello"');
        const results = service.getSelectionRanges(doc, [{line: 0, character: 2}]);
        assert.strictEqual(results.length, 1);
        const chain = flattenChain(results[0]);
        // innermost: the string value (0:1-0:6), outermost: document (0:0-0:7)
        assert.strictEqual(chain.length, 2);
        assert.deepStrictEqual(chain[0], {startLine: 0, startChar: 1, endLine: 0, endChar: 6});
    });

    it('should return nested chain for object property value', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '  age: 30',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "Alice" string value (line 1, inside the string)
        const results = service.getSelectionRanges(doc, [{line: 1, character: 10}]);
        assert.strictEqual(results.length, 1);

        const chain = flattenChain(results[0]);
        // string -> property -> object -> document
        assert.strictEqual(chain.length, 4);
        assert.deepStrictEqual(chain[0], {startLine: 1, startChar: 9, endLine: 1, endChar: 14});
        assert.deepStrictEqual(chain[1], {startLine: 1, startChar: 2, endLine: 1, endChar: 14});
        assert.deepStrictEqual(chain[2], {startLine: 0, startChar: 0, endLine: 3, endChar: 1});
    });

    it('should return chain for cursor on property key', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "name" key (line 1, character 3)
        const results = service.getSelectionRanges(doc, [{line: 1, character: 3}]);
        assert.strictEqual(results.length, 1);

        const chain = flattenChain(results[0]);
        // key -> property -> object -> document
        assert.strictEqual(chain.length, 4);
        assert.deepStrictEqual(chain[0], {startLine: 1, startChar: 2, endLine: 1, endChar: 6});
        assert.deepStrictEqual(chain[1], {startLine: 1, startChar: 2, endLine: 1, endChar: 14});
        assert.deepStrictEqual(chain[2], {startLine: 0, startChar: 0, endLine: 2, endChar: 1});
    });

    it('should handle multiple positions', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '  age: 30',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        const positions: Position[] = [
            {line: 1, character: 10},  // on "Alice"
            {line: 2, character: 8}    // on 30
        ];
        const results = service.getSelectionRanges(doc, positions);
        assert.strictEqual(results.length, 2);

        assert.strictEqual(flattenChain(results[0]).length, 4);
        assert.strictEqual(flattenChain(results[1]).length, 4);
    });

    it('should handle nested objects', () => {
        const content = [
            '{',
            '  person: {',
            '    name: "Alice"',
            '  }',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "Alice" (line 2, character 12)
        const results = service.getSelectionRanges(doc, [{line: 2, character: 12}]);
        const chain = flattenChain(results[0]);

        // string -> property (name:Alice) -> inner object -> property (person:{...}) -> outer object -> document
        assert.strictEqual(chain.length, 6);
        assert.deepStrictEqual(chain[0], {startLine: 2, startChar: 11, endLine: 2, endChar: 16});
    });

    it('should handle arrays', () => {
        const content = [
            '[',
            '  "one"',
            '  "two"',
            '  "three"',
            ']'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "two" (line 2, character 3)
        const results = service.getSelectionRanges(doc, [{line: 2, character: 3}]);
        const chain = flattenChain(results[0]);

        // string -> array -> document
        assert.strictEqual(chain.length, 3);
        assert.deepStrictEqual(chain[0], {startLine: 2, startChar: 3, endLine: 2, endChar: 6});
        assert.deepStrictEqual(chain[1], {startLine: 0, startChar: 0, endLine: 4, endChar: 1});
    });

    it('should handle cursor on container delimiter', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on opening brace (line 0, character 0)
        const results = service.getSelectionRanges(doc, [{line: 0, character: 0}]);
        const chain = flattenChain(results[0]);

        // object range -> document range
        assert.strictEqual(chain.length, 2);
        assert.deepStrictEqual(chain[0], {startLine: 0, startChar: 0, endLine: 2, endChar: 1});
    });

    it('should produce a strictly expanding chain (each parent contains child)', () => {
        const content = [
            '{',
            '  items: [',
            '    "hello"',
            '  ]',
            '}'
        ].join('\n');
        const doc = createKsonDocument(content);

        // Cursor on "hello" (line 2, character 6)
        const results = service.getSelectionRanges(doc, [{line: 2, character: 6}]);
        const chain = flattenChain(results[0]);

        // Verify each range is contained within (or equal to) the next
        for (let i = 0; i < chain.length - 1; i++) {
            const inner = chain[i];
            const outer = chain[i + 1];

            const innerStartsBefore = inner.startLine < outer.startLine ||
                (inner.startLine === outer.startLine && inner.startChar < outer.startChar);
            const innerEndsAfter = inner.endLine > outer.endLine ||
                (inner.endLine === outer.endLine && inner.endChar > outer.endChar);

            assert.ok(!innerStartsBefore,
                `Level ${i} starts before level ${i + 1}: (${inner.startLine}:${inner.startChar}) vs (${outer.startLine}:${outer.startChar})`);
            assert.ok(!innerEndsAfter,
                `Level ${i} ends after level ${i + 1}: (${inner.endLine}:${inner.endChar}) vs (${outer.endLine}:${outer.endChar})`);
        }
    });
});

import {describe, it} from 'mocha';
import assert from 'assert';
import {FoldingRangeKind} from 'vscode-languageserver';
import {FoldingRangeService} from '../../../core/features/FoldingRangeService.js';
import {KsonTooling} from 'kson-tooling';

describe('FoldingRangeService', () => {
    const service = new FoldingRangeService();

    it('should return no folding ranges for single-line document', () => {
        const ranges = service.getFoldingRanges(KsonTooling.getInstance().parse('key: value'));
        assert.strictEqual(ranges.length, 0);
    });

    it('should fold a multi-line object', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '  age: 30',
            '}'
        ].join('\n');
        const ranges = service.getFoldingRanges(KsonTooling.getInstance().parse(content));

        assert.strictEqual(ranges.length, 1);
        assert.strictEqual(ranges[0].startLine, 0);
        assert.strictEqual(ranges[0].endLine, 3);
        assert.strictEqual(ranges[0].kind, FoldingRangeKind.Region);
    });

    it('should fold nested objects', () => {
        const content = [
            '{',
            '  person: {',
            '    name: "Alice"',
            '  }',
            '}'
        ].join('\n');
        const ranges = service.getFoldingRanges(KsonTooling.getInstance().parse(content));

        assert.strictEqual(ranges.length, 2);
        // Inner object folds from line 1 to line 3
        const innerRange = ranges.find(r => r.startLine === 1);
        assert.ok(innerRange);
        assert.strictEqual(innerRange!.endLine, 3);
        // Outer object folds from line 0 to line 4
        const outerRange = ranges.find(r => r.startLine === 0);
        assert.ok(outerRange);
        assert.strictEqual(outerRange!.endLine, 4);
    });

    it('should fold a multi-line array', () => {
        const content = [
            '[',
            '  1',
            '  2',
            '  3',
            ']'
        ].join('\n');
        const ranges = service.getFoldingRanges(KsonTooling.getInstance().parse(content));

        assert.strictEqual(ranges.length, 1);
        assert.strictEqual(ranges[0].startLine, 0);
        assert.strictEqual(ranges[0].endLine, 4);
    });

    it('should fold an embed block', () => {
        const content = [
            'query: $sql',
            '  SELECT *',
            '  FROM users',
            '  $$'
        ].join('\n');
        const ranges = service.getFoldingRanges(KsonTooling.getInstance().parse(content));

        assert.strictEqual(ranges.length, 1);
        assert.strictEqual(ranges[0].startLine, 0);
        assert.strictEqual(ranges[0].endLine, 3);
    });

    it('should not fold single-line objects', () => {
        const ranges = service.getFoldingRanges(KsonTooling.getInstance().parse('{ name: "Alice", age: 30 }'));
        assert.strictEqual(ranges.length, 0);
    });

    it('should handle empty document', () => {
        const ranges = service.getFoldingRanges(KsonTooling.getInstance().parse(''));
        assert.strictEqual(ranges.length, 0);
    });

    it('should handle mixed foldable regions', () => {
        const content = [
            '{',
            '  items: [',
            '    "one"',
            '    "two"',
            '  ]',
            '  code: $js',
            '    console.log("hi")',
            '    $$',
            '}'
        ].join('\n');
        const ranges = service.getFoldingRanges(KsonTooling.getInstance().parse(content));

        assert.strictEqual(ranges.length, 3);

        const arrayRange = ranges.find(r => r.startLine === 1);
        assert.ok(arrayRange);
        assert.strictEqual(arrayRange!.endLine, 4);

        const embedRange = ranges.find(r => r.startLine === 5);
        assert.ok(embedRange);
        assert.strictEqual(embedRange!.endLine, 7);

        const objectRange = ranges.find(r => r.startLine === 0);
        assert.ok(objectRange);
        assert.strictEqual(objectRange!.endLine, 8);
    });
});

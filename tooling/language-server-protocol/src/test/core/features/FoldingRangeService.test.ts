import {describe, it} from 'mocha';
import assert from 'assert';
import {FoldingRangeKind} from 'vscode-languageserver';
import {FoldingRangeService} from '../../../core/features/FoldingRangeService.js';
import {KsonTooling} from 'kson-tooling';

describe('FoldingRangeService', () => {
    const service = new FoldingRangeService();

    function parse(content: string) {
        return KsonTooling.getInstance().parse(content);
    }

    it('should return no folding ranges for single-line document', () => {
        const ranges = service.getFoldingRanges(parse('key: value'));
        assert.strictEqual(ranges.length, 0);
    });

    it('should fold a multi-line delimited object', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '  age: 30',
            '}'
        ].join('\n');
        const ranges = service.getFoldingRanges(parse(content));

        const objectRange = ranges.find(r => r.kind === FoldingRangeKind.Region && r.startLine === 0);
        assert.ok(objectRange);
        assert.strictEqual(objectRange!.endLine, 3);
    });

    it('should fold nested objects with property ranges', () => {
        const content = [
            '{',
            '  person: {',
            '    name: "Alice"',
            '  }',
            '}'
        ].join('\n');
        const ranges = service.getFoldingRanges(parse(content));

        // Outer object
        const outerRange = ranges.find(r => r.startLine === 0 && r.endLine === 4);
        assert.ok(outerRange);

        // Inner object and property both at lines 1-3
        const innerRanges = ranges.filter(r => r.startLine === 1 && r.endLine === 3);
        assert.strictEqual(innerRanges.length, 2);
    });

    it('should fold a multi-line array', () => {
        const content = [
            '[',
            '  1',
            '  2',
            '  3',
            ']'
        ].join('\n');
        const ranges = service.getFoldingRanges(parse(content));

        const arrayRange = ranges.find(r => r.startLine === 0 && r.endLine === 4);
        assert.ok(arrayRange);
        assert.strictEqual(arrayRange!.kind, FoldingRangeKind.Region);
    });

    it('should fold an embed block', () => {
        const content = [
            'query: $sql',
            '  SELECT *',
            '  FROM users',
            '  $$'
        ].join('\n');
        const ranges = service.getFoldingRanges(parse(content));

        const embedRange = ranges.find(r => r.startLine === 0 && r.endLine === 3);
        assert.ok(embedRange);
        assert.strictEqual(embedRange!.kind, FoldingRangeKind.Region);
    });

    it('should not fold single-line objects', () => {
        const ranges = service.getFoldingRanges(parse('{ name: "Alice", age: 30 }'));
        assert.strictEqual(ranges.length, 0);
    });

    it('should handle empty document', () => {
        const ranges = service.getFoldingRanges(parse(''));
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
        const ranges = service.getFoldingRanges(parse(content));

        const arrayRange = ranges.find(r => r.startLine === 1 && r.endLine === 4);
        assert.ok(arrayRange);

        const embedRange = ranges.find(r => r.startLine === 5 && r.endLine === 7);
        assert.ok(embedRange);

        const objectRange = ranges.find(r => r.startLine === 0 && r.endLine === 8);
        assert.ok(objectRange);
    });

    it('should fold a dash list', () => {
        const content = [
            '- one',
            '- two',
            '- three'
        ].join('\n');
        const ranges = service.getFoldingRanges(parse(content));

        const listRange = ranges.find(r => r.startLine === 0 && r.endLine === 2);
        assert.ok(listRange);
        assert.strictEqual(listRange!.kind, FoldingRangeKind.Region);
    });

    it('should fold a multi-line object property', () => {
        const content = [
            'person:',
            '  name: "Alice"',
            '  age: 30'
        ].join('\n');
        const ranges = service.getFoldingRanges(parse(content));

        const propertyRange = ranges.find(r => r.startLine === 0 && r.endLine === 2);
        assert.ok(propertyRange);
        assert.strictEqual(propertyRange!.kind, FoldingRangeKind.Region);
    });

    it('should fold comment blocks with Comment kind', () => {
        const content = [
            '# This is a comment',
            '# that spans multiple lines',
            'name: "Alice"'
        ].join('\n');
        const ranges = service.getFoldingRanges(parse(content));

        const commentRange = ranges.find(r => r.kind === FoldingRangeKind.Comment);
        assert.ok(commentRange);
        assert.strictEqual(commentRange!.startLine, 0);
        assert.strictEqual(commentRange!.endLine, 1);
    });

    it('should not fold a single comment line', () => {
        const content = [
            '# Just one comment',
            'name: "Alice"'
        ].join('\n');
        const ranges = service.getFoldingRanges(parse(content));

        const commentRanges = ranges.filter(r => r.kind === FoldingRangeKind.Comment);
        assert.strictEqual(commentRanges.length, 0);
    });
});

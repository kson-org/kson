import {FormattingService} from '../../../core/features/FormattingService';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {FormattingOptions} from 'vscode-languageserver';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {KsonCore} from 'kson';
import {describe, it} from 'mocha';
import assert from "assert";

/**
 * Tests for testing the JSON document formatting functionality.
 */
const applyEdits = TextDocument.applyEdits;

describe('KSON Formatter', () => {
    const formattingService = new FormattingService();

    function assertFormatting(unformatted: string, expected: string, insertSpaces: boolean = true): void {
        const uri = 'test://test.kson';
        const document = TextDocument.create(uri, 'kson', 0, unformatted);
        const ksonDocument: KsonDocument = new KsonDocument(
            document,
            KsonCore.getInstance().parseToAst(unformatted),
        );

        const options: FormattingOptions = {
            tabSize: 2,
            insertSpaces
        };

        const edits = formattingService.formatDocument(ksonDocument, options);
        const formatted = applyEdits(document, edits);

        assert.strictEqual(formatted, expected, 'should have a matching formatted document');
    }

    // Object formatting tests
    it('should format an object with a single property', () => {
        const content = '{"x" : 1}';
        const expected = [
            'x: 1'
        ].join('\n');
        assertFormatting(content, expected);
    });

    it('should format an object with multiple properties', () => {
        const content = '{"x" : 1,  "y" : "foo", "z"  : true}';
        const expected = [
            'x: 1',
            'y: foo',
            'z: true'
        ].join('\n');
        assertFormatting(content, expected);
    });

    it('should format an object with no properties', () => {
        const content = '{"x" : {    },  "y" : {}}';
        const expected = [
            'x:',
            '  {}',
            'y:',
            '  {}'
        ].join('\n');
        assertFormatting(content, expected);
    });

    it('should handle nested objects', () => {
        const content = '{"x" : {  "y" : { "z"  : { }}, "a": true}}';
        const expected = [
            'x:',
            '  y:',
            '    z:',
            '      {}',
            '    .',
            '  a: true'
        ].join('\n');
        assertFormatting(content, expected);
    });

    // Array formatting tests
    it('should format an array with a single item', () => {
        const content = '["[]"]';
        const expected = [
            "- '[]'"
        ].join('\n');
        assertFormatting(content, expected);
    });

    it('should format an array with multiple items', () => {
        const content = '[true,null,1.2]';
        const expected = [
            '- true',
            '- null',
            '- 1.2',
        ].join('\n');
        assertFormatting(content, expected);
    });

    it('should format an array with no items', () => {
        const content = '[      ]';
        const expected = '<>';
        assertFormatting(content, expected);
    });

    it('should handle nested arrays', () => {
        const content = '[ [], [ [ {} ], "a" ]  ]';
        const expected = [
            '- ',
            '  <>',
            '- ',
            '  - ',
            '    - {}',
            '    =',
            '  - a'
        ].join('\n');
        assertFormatting(content, expected);
    });

    // Indentation options tests
    it('should be configurable to indent with tabs - nested objects', () => {
        const content = '{"outer":{"inner":"value"}}';
        const expected = [
            'outer:',
            '\tinner: value'
        ].join('\n');
        let insertSpaces = false;
        assertFormatting(content, expected, insertSpaces);
    });

    it('should be configurable to indent with tabs - list ', () => {
        const content = [
            `list:[1,2,3]`
        ].join('\n');

        const expected = [
            `list:`,
            `\t- 1`,
            `\t- 2`,
            `\t- 3`,
        ].join('\n');
        let insertSpaces = false;
        assertFormatting(content, expected, insertSpaces);
    });

    // Edge cases
    it('should handle an empty object', () => {
        const content = '{}';
        const expected = '{}';
        assertFormatting(content, expected);
    });

    it('should turn the string into an unquoted string', () => {
        const content = '"hello"';
        const expected = 'hello';
        assertFormatting(content, expected);
    });

    it('should handle a single number value', () => {
        const content = '42';
        const expected = '42';
        assertFormatting(content, expected);
    });

    it('should handle a single boolean value', () => {
        const content = 'true';
        const expected = 'true';
        assertFormatting(content, expected);
    });

    it('should handle a single null value', () => {
        const content = 'null';
        const expected = 'null';
        assertFormatting(content, expected);
    });

    // Whitespace handling
    it('should preserve whitespace', () => {
        const content = '{"text":"  spaces  and\ttabs\t"}';
        const expected = [
            'text: \'  spaces  and\ttabs\t\''
        ].join('\n');
        assertFormatting(content, expected);
    });

    it('should handle an object with a trailing comma', () => {
        const content = '{"a": 1, "b": 2,}';
        const expected = [
            'a: 1',
            'b: 2'
        ].join('\n');
        assertFormatting(content, expected);
    });

    it('should handle an array with a trailing comma', () => {
        const content = '[1, 2, 3,]';
        const expected = [
            '- 1',
            '- 2',
            '- 3'
        ].join('\n');
        assertFormatting(content, expected);
    });

    // Special characters and escapes
    it('should handle a string with escapes', () => {
        const content = '{"text":"Hello\\nWorld\\t\\"quoted\\""}';
        const expected = [
            'text: \'Hello\\nWorld\\t\"quoted\"\''
        ].join('\n');
        assertFormatting(content, expected);
    });

    it('should handle unicode characters', () => {
        const content = '{"emoji":"🚀","chinese":"你好","math":"∑"}';
        const expected = [
            'emoji: \'🚀\'',
            'chinese: 你好',
            'math: \'∑\''
        ].join('\n');
        assertFormatting(content, expected);
    });

    // Large numbers and scientific notation
    it('should handle large numbers', () => {
        const content = '{"big":1234567890,"small":0.000001,"scientific":1.23e-10}';
        const expected = [
            'big: 1234567890',
            'small: 0.000001',
            'scientific: 1.23e-10'
        ].join('\n');
        assertFormatting(content, expected);
    });

    // Embed blocks
    it('should correctly format embed blocks', () => {
        const content = [
            `embedBlock: $kotlin`,
            `min indent of 2`,
            `$$`
        ].join('\n')
        const expected = [
            `embedBlock: %kotlin`,
            `  min indent of 2`,
            `  %%`
        ].join('\n')
        assertFormatting(content, expected);
    })

    it('should handle embed block with tab indentation', () => {
        const content = [
            `embedBlock: $kotlin`,
            `min indent of 2`,
            `$$`
        ].join('\n')
        const expected = [
            `embedBlock: %kotlin`,
            `\tmin indent of 2`,
            `\t%%`
        ].join('\n')
        let insertSpaces = false;
        assertFormatting(content, expected, insertSpaces);
    })
});

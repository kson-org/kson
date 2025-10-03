import {CodeLensService} from '../../../core/features/CodeLensService.js';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {CommandType} from '../../../core/commands/CommandType.js';
import {Kson} from 'kson';
import {describe, it} from 'mocha';
import assert from 'assert';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {FormattingStyle} from 'kson'

describe('CodeLensService', () => {
    const service = new CodeLensService();

    it('should return code lenses at the top of the document', () => {
        const content = '{ "name": "test" }';
        const textDocument = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysisResult = Kson.getInstance().analyze(content);
        const document = new KsonDocument(textDocument, analysisResult);

        const lenses = service.getCodeLenses(document);

        assert.strictEqual(lenses.length, 4);

        // First lens should be "plain"
        assert.strictEqual(lenses[0].command?.title, 'plain');
        assert.strictEqual(lenses[0].command?.command, CommandType.PLAIN_FORMAT);
        assert.deepStrictEqual(lenses[0].command?.arguments, [{
            documentUri: 'file:///test.kson',
            formattingStyle: FormattingStyle.PLAIN
        }]);
        assert.deepStrictEqual(lenses[0].range.start, { line: 0, character: 0 });
        assert.deepStrictEqual(lenses[0].range.end, { line: 0, character: 0 });

        // Second lens should be "delimited"
        assert.strictEqual(lenses[1].command?.title, 'delimited');
        assert.strictEqual(lenses[1].command?.command, CommandType.DELIMITED_FORMAT);
        assert.deepStrictEqual(lenses[1].command?.arguments, [{
            documentUri: 'file:///test.kson',
            formattingStyle: FormattingStyle.DELIMITED
        }]);
        assert.deepStrictEqual(lenses[1].range.start, { line: 0, character: 0 });
        assert.deepStrictEqual(lenses[1].range.end, { line: 0, character: 0 });

        // Third lens should be "classic"
        assert.strictEqual(lenses[2].command?.title, 'classic');
        assert.strictEqual(lenses[2].command?.command, CommandType.CLASSIC_FORMAT);
        assert.deepStrictEqual(lenses[2].command?.arguments, [{
            documentUri: 'file:///test.kson',
            formattingStyle: FormattingStyle.CLASSIC
        }]);
        assert.deepStrictEqual(lenses[2].range.start, { line: 0, character: 0 });
        assert.deepStrictEqual(lenses[2].range.end, { line: 0, character: 0 });

        // Fourth lens should be "compact"
        assert.strictEqual(lenses[3].command?.title, 'compact');
        assert.strictEqual(lenses[3].command?.command, CommandType.COMPACT_FORMAT);
        assert.deepStrictEqual(lenses[3].command?.arguments, [{
            documentUri: 'file:///test.kson',
            formattingStyle: FormattingStyle.COMPACT
        }]);
        assert.deepStrictEqual(lenses[3].range.start, { line: 0, character: 0 });
        assert.deepStrictEqual(lenses[3].range.end, { line: 0, character: 0 });
    });
});
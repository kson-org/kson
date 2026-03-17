import {CodeLensService} from '../../../core/features/CodeLensService.js';
import {CommandType} from '../../../core/commands/CommandType.js';
import {describe, it} from 'mocha';
import assert from 'assert';
import {FormattingStyle} from 'kson'
import {createKsonDocument, TEST_URI} from '../../TestHelpers.js';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {Kson} from 'kson';
import {KsonDocument} from '../../../core/document/KsonDocument.js';

describe('CodeLensService', () => {
    const service = new CodeLensService();

    it('should return exactly 4 code lenses', () => {
        const document = createKsonDocument('{ "name": "test" }');
        const lenses = service.getCodeLenses(document);
        assert.strictEqual(lenses.length, 4);
    });

    it('should have correct command IDs for all formatting styles', () => {
        const document = createKsonDocument('key: value');
        const lenses = service.getCodeLenses(document);

        const commandIds = lenses.map(l => l.command?.command);
        assert.deepStrictEqual(commandIds, [
            CommandType.PLAIN_FORMAT,
            CommandType.DELIMITED_FORMAT,
            CommandType.CLASSIC_FORMAT,
            CommandType.COMPACT_FORMAT
        ]);
    });

    it('should include document URI in each lens argument', () => {
        const document = createKsonDocument('{ "name": "test" }');
        const lenses = service.getCodeLenses(document);

        for (const lens of lenses) {
            const args = lens.command?.arguments;
            assert.ok(args && args.length === 1);
            assert.strictEqual(args[0].documentUri, TEST_URI);
        }
    });

    it('should include the correct FormattingStyle in each lens argument', () => {
        const document = createKsonDocument('key: value');
        const lenses = service.getCodeLenses(document);

        assert.strictEqual(lenses[0].command?.arguments?.[0].formattingStyle, FormattingStyle.PLAIN);
        assert.strictEqual(lenses[1].command?.arguments?.[0].formattingStyle, FormattingStyle.DELIMITED);
        assert.strictEqual(lenses[2].command?.arguments?.[0].formattingStyle, FormattingStyle.CLASSIC);
        assert.strictEqual(lenses[3].command?.arguments?.[0].formattingStyle, FormattingStyle.COMPACT);
    });

    it('should place all lenses at line 0, character 0', () => {
        const document = createKsonDocument('key: value');
        const lenses = service.getCodeLenses(document);

        for (const lens of lenses) {
            assert.deepStrictEqual(lens.range.start, { line: 0, character: 0 });
            assert.deepStrictEqual(lens.range.end, { line: 0, character: 0 });
        }
    });

    it('should return lenses for an empty document', () => {
        const document = createKsonDocument('');
        const lenses = service.getCodeLenses(document);
        assert.strictEqual(lenses.length, 4);
    });

    it('should return lenses for an invalid document', () => {
        const document = createKsonDocument('{ broken {{{{');
        const lenses = service.getCodeLenses(document);
        assert.strictEqual(lenses.length, 4);
    });

    it('should use the document URI from the provided document', () => {
        const customUri = 'file:///custom/path/doc.kson';
        const textDoc = TextDocument.create(customUri, 'kson', 1, 'key: value');
        const analysis = Kson.getInstance().analyze('key: value');
        const document = new KsonDocument(textDoc, analysis);

        const lenses = service.getCodeLenses(document);

        for (const lens of lenses) {
            assert.strictEqual(lens.command?.arguments?.[0].documentUri, customUri);
        }
    });
});

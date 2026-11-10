import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import * as monaco from 'monaco-editor';
import { KSON_LANGUAGE_ID, registerKsonLanguage } from './ksonLanguage.js';

beforeAll(() => registerKsonLanguage());

describe('Enter in an editor', () => {
    let editor: monaco.editor.IStandaloneCodeEditor;

    function openEditor(content: string) {
        editor = monaco.editor.create(document.body.appendChild(document.createElement('div')), {
            value: content,
            language: KSON_LANGUAGE_ID,
            // The minimap needs canvas pixels; occurrence highlighting's timer rejects on dispose
            minimap: { enabled: false },
            occurrencesHighlight: 'off',
        });
    }

    function typeAtEndOfLine(lineNumber: number, text: string) {
        const model = editor.getModel()!;
        // Monaco ignores Enter rules on untokenized lines; colorizing a line tokenizes it
        monaco.editor.colorizeModelLine(model, lineNumber);
        editor.setPosition({ lineNumber, column: model.getLineMaxColumn(lineNumber) });
        editor.trigger('keyboard', 'type', { text });
    }

    afterEach(() => {
        const model = editor.getModel();
        editor.dispose();
        model?.dispose();
    });

    it('outdents after an end-dot', () => {
        openEditor('key:\n    x: 1\n    .');

        typeAtEndOfLine(3, '\ny: 2');

        expect(editor.getValue()).toBe('key:\n    x: 1\n    .\ny: 2');
    });

    const embedBlocksWithoutKnownLanguage = [
        { opening: '%plain', description: 'an embed block with an unrecognized tag' },
        { opening: '%', description: 'an untagged embed block' },
    ];

    it.each(embedBlocksWithoutKnownLanguage)('keeps the indentation after a sentence in $description', ({ opening }) => {
        openEditor(`text: ${opening}\n    something something.\n%%`);

        typeAtEndOfLine(2, '\nmore');

        expect(editor.getValue()).toBe(`text: ${opening}\n    something something.\n    more\n%%`);
    });
});

describe('embed blocks', () => {
    function tokenize(text: string) {
        const lines = text.split('\n');
        return monaco.editor.tokenize(text, KSON_LANGUAGE_ID).map((tokens, line) =>
            tokens.map(({ offset, type, language }, i) => ({
                text: lines[line].slice(offset, tokens[i + 1]?.offset),
                type,
                language,
            })),
        );
    }

    describe.each([
        { opening: '%', closing: '%%' },
        { opening: '$', closing: '$$' },
    ])('opened with $opening', ({ opening, closing }) => {
        // Quotes must not open KSON strings that swallow the closing delimiter
        const content = `it's "content"`;

        it.each([
            { tag: 'tag', description: 'a tag' },
            { tag: '', description: 'no tag' },
            { tag: `a ${closing} b`, description: 'the closing delimiter in its tag' },
        ])('hands the content to plain text after $description', ({ tag }) => {
            const [, contentLine] = tokenize(`key: ${opening}${tag}\n    ${content}\n${closing}`);

            expect(contentLine.map(({ language }) => language)).toEqual(['plaintext']);
        });

        it('highlights the delimiters', () => {
            const [openingLine, , closingLine] = tokenize(`key: ${opening}tag\n    ${content}\n${closing}`);

            expect(openingLine).toContainEqual({ text: opening, type: 'metatag.kson', language: 'kson' });
            expect(closingLine).toEqual([{ text: closing, type: 'metatag.kson', language: 'kson' }]);
        });

        it('hands the rest of the closing line back to KSON', () => {
            const [, , closingLine] = tokenize(`key: ${opening}\n    ${content}\n${closing} after: 1`);

            expect(closingLine).toContainEqual({ text: '1', type: 'number.kson', language: 'kson' });
        });
    });
});

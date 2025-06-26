import {TextDocument} from 'vscode-languageserver-textdocument';
import {describe, it} from 'mocha';
import assert from "assert"
import {SemanticTokenTypes} from 'vscode-languageserver';
import {Kson} from 'kson';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {KSON_LEGEND, SemanticTokensService} from '../../../core/features/SemanticTokensService';

describe('KSON Semantic Tokens', () => {
    interface DecodedToken {
        deltaLine: number;
        deltaStart: number;
        length: number;
        type: string;
        modifiers: string[];
    }

    const semanticTokensService = new SemanticTokensService();

    function assertSemanticTokens(text: string, expectedTokens: DecodedToken[]): void {
        const uri = 'test://test.kson';
        const document = TextDocument.create(uri, 'kson', 0, text);
        const parseResult = Kson.getInstance().parseToKson(text);

        // Create a mock document entry for the semantic tokens
        const documentEntry: KsonDocument = new KsonDocument(
            document,
            parseResult,
        );

        // Get the unprocessed tokens for debugging
        const result = semanticTokensService.getSemanticTokens(documentEntry);

        // Convert the encoded tokens to decoded format for comparison
        const tokens = decodeTokens(result.data);

        assert.deepStrictEqual(tokens, expectedTokens, "should have matching semantic tokens.");
    }

    function decodeTokens(data: number[]): DecodedToken[] {
        const tokens: DecodedToken[] = [];

        for (let i = 0; i < data.length; i += 5) {
            const deltaLine = data[i];
            const deltaStart = data[i + 1];
            const length = data[i + 2];
            const tokenTypeIndex = data[i + 3];

            const tokenType = KSON_LEGEND.tokenTypes[tokenTypeIndex];

            if (tokenType) {
                tokens.push(
                    {
                        deltaLine: deltaLine,
                        deltaStart: deltaStart,
                        length: length,
                        type: tokenType,
                        modifiers: [],
                    }
                )
            }
        }

        return tokens;
    }

    it('should distinguish between a key and a string', () => {
        const content = 'key: string';
        const expectedTokens: DecodedToken[] = [
            {deltaLine: 0, deltaStart: 0, length: 3, type: SemanticTokenTypes.variable, modifiers: []}, // key
            {deltaLine: 0, deltaStart: 3, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // :
            {deltaLine: 0, deltaStart: 2, length: 6, type: SemanticTokenTypes.string, modifiers: []}, // string
            {deltaLine: 0, deltaStart: 6, length: 0, type: SemanticTokenTypes.modifier, modifiers: []} // EOF
        ]
        assertSemanticTokens(content, expectedTokens);
    })

    it('should handle a number value', () => {
        const content = 'number: 42';

        const expectedTokens: DecodedToken[] = [
            {deltaLine: 0, deltaStart: 0, length: 6, type: SemanticTokenTypes.variable, modifiers: []},
            {deltaLine: 0, deltaStart: 6, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 2, type: SemanticTokenTypes.number, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 0, type: SemanticTokenTypes.modifier, modifiers: []} // EOF
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    it('should handle a boolean value', () => {
        const content = 'flag: true';

        const expectedTokens: DecodedToken[] = [
            // line 0
            {deltaLine: 0, deltaStart: 0, length: 4, type: SemanticTokenTypes.variable, modifiers: []},
            {deltaLine: 0, deltaStart: 4, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 4, type: SemanticTokenTypes.keyword, modifiers: []},
            {deltaLine: 0, deltaStart: 4, length: 0, type: SemanticTokenTypes.modifier, modifiers: []} // EOF
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    // Array token tests
    it('should handle a simple array', () => {
        const content = [
            'items:',
            '  - first',
            '  - second'
        ].join('\n');

        const expectedTokens: DecodedToken[] = [
            // line 0
            {deltaLine: 0, deltaStart: 0, length: 5, type: SemanticTokenTypes.variable, modifiers: []},
            {deltaLine: 0, deltaStart: 5, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            // line 1
            {deltaLine: 1, deltaStart: 2, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 5, type: SemanticTokenTypes.string, modifiers: []},
            // line 2
            {deltaLine: 1, deltaStart: 2, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 6, type: SemanticTokenTypes.string, modifiers: []},
            {deltaLine: 0, deltaStart: 6, length: 0, type: SemanticTokenTypes.modifier, modifiers: []} // EOF
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    it('should handle an embed block', () => {
        const content = [
            'embedBlock: $$tag',
            '  multi-line',
            '  embed content',
            '  $$'
        ].join('\n');

        const expectedTokens: DecodedToken[] = [
            // line 0
            {deltaLine: 0, deltaStart: 0, length: 10, type: SemanticTokenTypes.variable, modifiers: []}, // embedBlock
            {deltaLine: 0, deltaStart: 10, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // :
            {deltaLine: 0, deltaStart: 2, length: 2, type: SemanticTokenTypes.function, modifiers: []}, // $$
            {deltaLine: 0, deltaStart: 2, length: 3, type: SemanticTokenTypes.decorator, modifiers: []}, // tag
            {deltaLine: 0, deltaStart: 3, length: 1, type: SemanticTokenTypes.function, modifiers: []}, // \n
            // line 1
            {deltaLine: 1, deltaStart: 0, length: 31, type: SemanticTokenTypes.macro, modifiers: []}, // indented embed content
            // line 2
            {deltaLine: 2, deltaStart: 2, length: 2, type: SemanticTokenTypes.function, modifiers: []}, // $$
            {deltaLine: 0, deltaStart: 2, length: 0, type: SemanticTokenTypes.modifier, modifiers: []} // EOF
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    it('should handle all punctuation', () => {
        const content = '{ "key": [ "v1", "v2" ] }';

        const expectedTokens: DecodedToken[] = [
            {deltaLine: 0, deltaStart: 0, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // {
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.variable, modifiers: []}, // "
            {deltaLine: 0, deltaStart: 1, length: 3, type: SemanticTokenTypes.variable, modifiers: []}, // key
            {deltaLine: 0, deltaStart: 3, length: 1, type: SemanticTokenTypes.variable, modifiers: []}, // "
            {deltaLine: 0, deltaStart: 1, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // :
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // [
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.string, modifiers: []},  // "
            {deltaLine: 0, deltaStart: 1, length: 2, type: SemanticTokenTypes.string, modifiers: []},  // v1
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.string, modifiers: []},  // "
            {deltaLine: 0, deltaStart: 1, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // ,
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.string, modifiers: []},  // "
            {deltaLine: 0, deltaStart: 1, length: 2, type: SemanticTokenTypes.string, modifiers: []},  // v2
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.string, modifiers: []},  // "
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // ]
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // }
            {deltaLine: 0, deltaStart: 1, length: 0, type: SemanticTokenTypes.modifier, modifiers: []} // EOF

        ];

        assertSemanticTokens(content, expectedTokens);
    });
});

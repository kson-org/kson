import {describe, it} from 'mocha';
import assert from "assert"
import {SemanticTokenTypes} from 'vscode-languageserver';
import {KSON_LEGEND, SemanticTokensService} from '../../../core/features/SemanticTokensService';
import {createKsonDocument} from '../../TestHelpers.js';

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
        const documentEntry = createKsonDocument(text);
        const result = semanticTokensService.getSemanticTokens(documentEntry);

        // Convert the encoded tokens to decoded format for comparison
        const tokens = decodeTokens(result.data);

        assert.deepStrictEqual(tokens, expectedTokens, "should have matching semantic tokens.");
    }

    function getTokens(text: string): DecodedToken[] {
        const documentEntry = createKsonDocument(text);
        const result = semanticTokensService.getSemanticTokens(documentEntry);
        return decodeTokens(result.data);
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
        ]
        assertSemanticTokens(content, expectedTokens);
    })

    it('should handle a number value', () => {
        const content = 'number: 42';

        const expectedTokens: DecodedToken[] = [
            {deltaLine: 0, deltaStart: 0, length: 6, type: SemanticTokenTypes.variable, modifiers: []},
            {deltaLine: 0, deltaStart: 6, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 2, type: SemanticTokenTypes.number, modifiers: []},
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
        ];

        assertSemanticTokens(content, expectedTokens);
    });

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
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    it('should handle an embed block', () => {
        const content = [
            'embedBlock: $tag',
            '    multi-line',
            '   embed content',
            '   $$'
        ].join('\n');

        const expectedTokens: DecodedToken[] = [
            // line 0
            {deltaLine: 0, deltaStart: 0, length: 10, type: SemanticTokenTypes.variable, modifiers: []}, // embedBlock
            {deltaLine: 0, deltaStart: 10, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // :
            {deltaLine: 0, deltaStart: 2, length: 1, type: SemanticTokenTypes.function, modifiers: []}, // $
            {deltaLine: 0, deltaStart: 1, length: 3, type: SemanticTokenTypes.decorator, modifiers: []}, // tag
            {deltaLine: 0, deltaStart: 3, length: 1, type: SemanticTokenTypes.function, modifiers: []}, // \n
            {deltaLine: 3, deltaStart: 3, length: 2, type: SemanticTokenTypes.function, modifiers: []}, // $$
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    it('should handle a decimal number', () => {
        assertSemanticTokens('value: 3.14', [
            {deltaLine: 0, deltaStart: 0, length: 5, type: SemanticTokenTypes.variable, modifiers: []},
            {deltaLine: 0, deltaStart: 5, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 4, type: SemanticTokenTypes.number, modifiers: []},
        ]);
    });

    it('should handle a negative number', () => {
        assertSemanticTokens('value: -42', [
            {deltaLine: 0, deltaStart: 0, length: 5, type: SemanticTokenTypes.variable, modifiers: []},
            {deltaLine: 0, deltaStart: 5, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 3, type: SemanticTokenTypes.number, modifiers: []},
        ]);
    });

    it('should handle null value', () => {
        assertSemanticTokens('value: null', [
            {deltaLine: 0, deltaStart: 0, length: 5, type: SemanticTokenTypes.variable, modifiers: []},
            {deltaLine: 0, deltaStart: 5, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 4, type: SemanticTokenTypes.keyword, modifiers: []},
        ]);
    });

    it('should handle false value', () => {
        assertSemanticTokens('value: false', [
            {deltaLine: 0, deltaStart: 0, length: 5, type: SemanticTokenTypes.variable, modifiers: []},
            {deltaLine: 0, deltaStart: 5, length: 1, type: SemanticTokenTypes.operator, modifiers: []},
            {deltaLine: 0, deltaStart: 2, length: 5, type: SemanticTokenTypes.keyword, modifiers: []},
        ]);
    });

    it('should handle empty document', () => {
        assertSemanticTokens('', []);
    });

    it('should handle nested objects with keys at different levels', () => {
        assertSemanticTokens('outer:\n  inner: value', [
            {deltaLine: 0, deltaStart: 0, length: 5, type: SemanticTokenTypes.variable, modifiers: []}, // outer
            {deltaLine: 0, deltaStart: 5, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // :
            {deltaLine: 1, deltaStart: 2, length: 5, type: SemanticTokenTypes.variable, modifiers: []}, // inner
            {deltaLine: 0, deltaStart: 5, length: 1, type: SemanticTokenTypes.operator, modifiers: []}, // :
            {deltaLine: 0, deltaStart: 2, length: 5, type: SemanticTokenTypes.string, modifiers: []},   // value
        ]);
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

        ];

        assertSemanticTokens(content, expectedTokens);
    });

    it('should not produce tokens for whitespace or EOF', () => {
        const content = 'key: value';
        const tokens = getTokens(content);

        // 'key: value' has 3 meaningful tokens: key, colon, value.
        // Whitespace between them and the trailing EOF must be skipped.
        assert.strictEqual(tokens.length, 3, 'Should have exactly 3 tokens (no whitespace or EOF)');
    });

    it('should handle mixed content with multiple token types', () => {
        const content = [
            '{',
            '  name: "Alice"',
            '  age: 30',
            '  active: true',
            '  role: null',
            '  tags:',
            '    - admin',
            '}'
        ].join('\n');
        const tokens = getTokens(content);

        const types = new Set(tokens.map(t => t.type));
        assert.ok(types.has(SemanticTokenTypes.variable), 'Should have variable (keys)');
        assert.ok(types.has(SemanticTokenTypes.string), 'Should have string');
        assert.ok(types.has(SemanticTokenTypes.number), 'Should have number');
        assert.ok(types.has(SemanticTokenTypes.keyword), 'Should have keyword (true/null)');
        assert.ok(types.has(SemanticTokenTypes.operator), 'Should have operator');
    });
});

import {describe, it} from 'mocha';
import assert from "assert"
import {SemanticTokenTypes} from 'vscode-languageserver';
import {KSON_LEGEND, SemanticTokensService} from '../../../core/features/SemanticTokensService';
import {createKsonDocument} from '../../TestHelpers.js';
import {KsonTooling} from 'kson-tooling';

describe('KSON Semantic Tokens', () => {
    interface DecodedToken {
        deltaLine: number;
        deltaStart: number;
        length: number;
        type: string;
        modifiers: string[];
    }

    const semanticTokensService = new SemanticTokensService();

    function token(deltaLine: number, deltaStart: number, length: number, type: string): DecodedToken {
        return {deltaLine, deltaStart, length, type, modifiers: []};
    }

    function assertSemanticTokens(text: string, expectedTokens: DecodedToken[]): void {
        const documentEntry = createKsonDocument(text);
        const result = semanticTokensService.getSemanticTokens(documentEntry.getToolingDocument());

        // Convert the encoded tokens to decoded format for comparison
        const tokens = decodeTokens(result.data);

        assert.deepStrictEqual(tokens, expectedTokens, "should have matching semantic tokens.");
    }

    function getTokens(text: string): DecodedToken[] {
        const documentEntry = createKsonDocument(text);
        const result = semanticTokensService.getSemanticTokens(documentEntry.getToolingDocument());
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
            token(0, 0, 3, SemanticTokenTypes.variable), // key
            token(0, 3, 1, SemanticTokenTypes.operator), // :
            token(0, 2, 6, SemanticTokenTypes.string),   // string
        ]
        assertSemanticTokens(content, expectedTokens);
    })

    it('should handle a number value', () => {
        const content = 'number: 42';

        const expectedTokens: DecodedToken[] = [
            token(0, 0, 6, SemanticTokenTypes.variable),
            token(0, 6, 1, SemanticTokenTypes.operator),
            token(0, 2, 2, SemanticTokenTypes.number),
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    it('should handle a boolean value', () => {
        const content = 'flag: true';

        const expectedTokens: DecodedToken[] = [
            // line 0
            token(0, 0, 4, SemanticTokenTypes.variable),
            token(0, 4, 1, SemanticTokenTypes.operator),
            token(0, 2, 4, SemanticTokenTypes.keyword),
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
            token(0, 0, 5, SemanticTokenTypes.variable),
            token(0, 5, 1, SemanticTokenTypes.operator),
            // line 1
            token(1, 2, 1, SemanticTokenTypes.operator),
            token(0, 2, 5, SemanticTokenTypes.string),
            // line 2
            token(1, 2, 1, SemanticTokenTypes.operator),
            token(0, 2, 6, SemanticTokenTypes.string),
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
            token(0, 0, 10, SemanticTokenTypes.variable),  // embedBlock
            token(0, 10, 1, SemanticTokenTypes.operator),   // :
            token(0, 2, 1, SemanticTokenTypes.function),    // $
            token(0, 1, 3, SemanticTokenTypes.decorator),   // tag
            token(0, 3, 1, SemanticTokenTypes.function),    // \n
            token(3, 3, 2, SemanticTokenTypes.function),    // $$
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    it('should handle a decimal number', () => {
        assertSemanticTokens('value: 3.14', [
            token(0, 0, 5, SemanticTokenTypes.variable),
            token(0, 5, 1, SemanticTokenTypes.operator),
            token(0, 2, 4, SemanticTokenTypes.number),
        ]);
    });

    it('should handle a negative number', () => {
        assertSemanticTokens('value: -42', [
            token(0, 0, 5, SemanticTokenTypes.variable),
            token(0, 5, 1, SemanticTokenTypes.operator),
            token(0, 2, 3, SemanticTokenTypes.number),
        ]);
    });

    it('should handle null value', () => {
        assertSemanticTokens('value: null', [
            token(0, 0, 5, SemanticTokenTypes.variable),
            token(0, 5, 1, SemanticTokenTypes.operator),
            token(0, 2, 4, SemanticTokenTypes.keyword),
        ]);
    });

    it('should handle false value', () => {
        assertSemanticTokens('value: false', [
            token(0, 0, 5, SemanticTokenTypes.variable),
            token(0, 5, 1, SemanticTokenTypes.operator),
            token(0, 2, 5, SemanticTokenTypes.keyword),
        ]);
    });

    it('should handle empty document', () => {
        assertSemanticTokens('', []);
    });

    it('should handle nested objects with keys at different levels', () => {
        assertSemanticTokens('outer:\n  inner: value', [
            token(0, 0, 5, SemanticTokenTypes.variable), // outer
            token(0, 5, 1, SemanticTokenTypes.operator), // :
            token(1, 2, 5, SemanticTokenTypes.variable), // inner
            token(0, 5, 1, SemanticTokenTypes.operator), // :
            token(0, 2, 5, SemanticTokenTypes.string),   // value
        ]);
    });

    it('should handle all punctuation', () => {
        const content = '{ "key": [ "v1", "v2" ] }';

        const expectedTokens: DecodedToken[] = [
            token(0, 0, 1, SemanticTokenTypes.operator), // {
            token(0, 2, 1, SemanticTokenTypes.variable), // "
            token(0, 1, 3, SemanticTokenTypes.variable), // key
            token(0, 3, 1, SemanticTokenTypes.variable), // "
            token(0, 1, 1, SemanticTokenTypes.operator), // :
            token(0, 2, 1, SemanticTokenTypes.operator), // [
            token(0, 2, 1, SemanticTokenTypes.string),   // "
            token(0, 1, 2, SemanticTokenTypes.string),   // v1
            token(0, 2, 1, SemanticTokenTypes.string),   // "
            token(0, 1, 1, SemanticTokenTypes.operator), // ,
            token(0, 2, 1, SemanticTokenTypes.string),   // "
            token(0, 1, 2, SemanticTokenTypes.string),   // v2
            token(0, 2, 1, SemanticTokenTypes.string),   // "
            token(0, 2, 1, SemanticTokenTypes.operator), // ]
            token(0, 2, 1, SemanticTokenTypes.operator), // }
        ];

        assertSemanticTokens(content, expectedTokens);
    });

    describe('token presence', () => {
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
});

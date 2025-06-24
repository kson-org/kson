import {SemanticTokens, SemanticTokensBuilder, SemanticTokensLegend, SemanticTokenTypes} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument';
import {Token, TokenType} from 'kson';


export const KSON_LEGEND: SemanticTokensLegend = {
    tokenTypes: [
        // Strings and quotes
        SemanticTokenTypes.string,
        // Object Identifier
        SemanticTokenTypes.variable,
        // Numbers
        SemanticTokenTypes.number,
        // True, False, Null
        SemanticTokenTypes.keyword,
        // Interpunction: [ ] < > { } , : . =
        SemanticTokenTypes.operator,
        // Comment
        SemanticTokenTypes.comment,
        // Embed Delimiters
        SemanticTokenTypes.method,
        SemanticTokenTypes.decorator,
        SemanticTokenTypes.macro,
        SemanticTokenTypes.function,

        // EOF, Whitespace, IllegalChar
        SemanticTokenTypes.modifier,
    ],
    tokenModifiers: []
};

/**
 * Service responsible for providing semantic token information for Kson documents.
 */
export class SemanticTokensService {

    /**
     * Return the {@link SemanticTokens} of a {@link KsonDocument}.
     * The tokens we return are provided in {@link KSON_LEGEND}, and mapped in {@link this.mapTokenToSemantic}
     * @param document
     */
    getSemanticTokens(document: KsonDocument): SemanticTokens {
        return this.tokenizeKsonDocument(document);
    }

    private tokenizeKsonDocument(document: KsonDocument): SemanticTokens {
        const semanticTokenBuilder = new SemanticTokensBuilder()

        const parsedTokens = document.getParseResult().lexedTokens.asJsReadonlyArrayView();
        if (parsedTokens) {
            for (let i = 0; i < parsedTokens.length; i++) {
                const token = parsedTokens[i];

                // We pass next tokens to determine if the current token is a keyword or just a string/identifier.
                // Note: we only need the next 3 tokens. A colon is always within 3 tokens of a STRING_OPEN_QUOTE.
                const nextTokens = parsedTokens.slice(i + 1, i + 4);
                const tokenType = this.mapTokenToSemantic(token, nextTokens);

                const tokenLocation = token.lexeme.location;
                semanticTokenBuilder.push(
                    tokenLocation.start.line,
                    tokenLocation.start.column,
                    tokenLocation.endOffset - tokenLocation.startOffset,
                    KSON_LEGEND.tokenTypes.indexOf(tokenType),
                    0
                )
            }
        }
        return semanticTokenBuilder.build();
    }


    /**
     * Map the {@link Token}s to vscode supported {@link SemanticTokenTypes}.
     * @param token the Kson token to map
     * @param nextTokens context for the {@link token}, thereby enabling the distinction between
     * keywords and mere identifiers/strings.
     * @private
     */
    private mapTokenToSemantic(token: Token, nextTokens?: Token[]): string {
        let semanticType: string | undefined;

        switch (token.tokenType) {
            // Strings and Identifiers
            case TokenType.UNQUOTED_STRING:
            case TokenType.STRING_CONTENT:
            case TokenType.STRING_OPEN_QUOTE:
            case TokenType.STRING_CLOSE_QUOTE:
                if (this.isKeyword(token, nextTokens || [])) {
                    semanticType = SemanticTokenTypes.variable
                } else {
                    semanticType = SemanticTokenTypes.string;
                }
                break;
            case TokenType.STRING_ESCAPE:
            case TokenType.STRING_UNICODE_ESCAPE:
            case TokenType.STRING_ILLEGAL_CONTROL_CHARACTER:
                semanticType = SemanticTokenTypes.string;
                break;

            // Numbers
            case TokenType.NUMBER:
                semanticType = SemanticTokenTypes.number;
                break;

            // Keywords (booleans + null)
            case TokenType.TRUE:
            case TokenType.FALSE:
            case TokenType.NULL:
                semanticType = SemanticTokenTypes.keyword;
                break;

            // Operators and punctuation
            case TokenType.COLON:
            case TokenType.COMMA:
            case TokenType.DOT:
            case TokenType.LIST_DASH:
            case TokenType.CURLY_BRACE_L:
            case TokenType.CURLY_BRACE_R:
            case TokenType.SQUARE_BRACKET_L:
            case TokenType.SQUARE_BRACKET_R:
            case TokenType.ANGLE_BRACKET_L:
            case TokenType.ANGLE_BRACKET_R:
            case TokenType.END_DASH:
                semanticType = SemanticTokenTypes.operator;
                break;

            // Comments
            case TokenType.COMMENT:
                semanticType = SemanticTokenTypes.comment;
                break;

            // Embedded tags and content
            case TokenType.EMBED_TAG:
                semanticType = SemanticTokenTypes.decorator;
                break;
            case TokenType.EMBED_CONTENT:
                semanticType = SemanticTokenTypes.macro;
                break;
            case TokenType.EMBED_PREAMBLE_NEWLINE:
            case TokenType.EMBED_OPEN_DELIM:
            case TokenType.EMBED_CLOSE_DELIM:
                semanticType = SemanticTokenTypes.function;
                break;

            // Whitespace, illegal chars, EOF – not semantically relevant
            case TokenType.WHITESPACE:
            case TokenType.ILLEGAL_CHAR:
            case TokenType.EOF:
                semanticType = SemanticTokenTypes.modifier;
                break
        }

        return semanticType;
    }

    /**
     * Helper method to identify whether a string/identifier is a keyword.
     * @param currentToken
     * @param nextTokens
     * @private
     */
    private isKeyword(currentToken: Token, nextTokens: Token[]): boolean {
        switch (currentToken.tokenType) {
            // Look for the first STRING_CLOSE_QUOTE and see if the next token is a COLON
            case TokenType.STRING_OPEN_QUOTE:
            case TokenType.STRING_CONTENT: {
                for (let i = 0; i < nextTokens.length - 1; i++) {
                    if (nextTokens[i].tokenType === TokenType.STRING_CLOSE_QUOTE) {
                        return nextTokens[i + 1].tokenType === TokenType.COLON;
                    }
                }
                return false;
            }

            // If the token is a keyword, then the next token must be a COLON
            case TokenType.STRING_CLOSE_QUOTE:
            case TokenType.UNQUOTED_STRING: {
                return nextTokens.length > 0 && nextTokens[0].tokenType === TokenType.COLON;
            }

            default:
                return false;
        }
    }
}


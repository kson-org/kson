import {SemanticTokens, SemanticTokensBuilder, SemanticTokensLegend, SemanticTokenTypes} from 'vscode-languageserver';
import {KsonTooling, ToolingDocument, SemanticTokenKind} from 'kson-tooling';

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
    ],
    tokenModifiers: []
};

function mapTokenKind(kind: SemanticTokenKind): number | undefined {
    if (kind === SemanticTokenKind.STRING) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.string);
    if (kind === SemanticTokenKind.KEY) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.variable);
    if (kind === SemanticTokenKind.NUMBER) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.number);
    if (kind === SemanticTokenKind.KEYWORD) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.keyword);
    if (kind === SemanticTokenKind.OPERATOR) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.operator);
    if (kind === SemanticTokenKind.COMMENT) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.comment);
    if (kind === SemanticTokenKind.EMBED_TAG) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.decorator);
    if (kind === SemanticTokenKind.EMBED_CONTENT) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.macro);
    if (kind === SemanticTokenKind.EMBED_DELIM) return KSON_LEGEND.tokenTypes.indexOf(SemanticTokenTypes.function);
    return undefined;
}

/**
 * Service responsible for providing semantic token information for Kson documents.
 */
export class SemanticTokensService {

    /**
     * Return the {@link SemanticTokens} of a pre-parsed {@link ToolingDocument}.
     */
    getSemanticTokens(document: ToolingDocument): SemanticTokens {
        const tooling = KsonTooling.getInstance();
        const ktTokens = tooling.getSemanticTokens(document).asJsReadonlyArrayView();
        const builder = new SemanticTokensBuilder();

        for (const ktToken of ktTokens) {
            // Skip EMBED_CONTENT — handled by TextMate grammars
            if (ktToken.tokenType === SemanticTokenKind.EMBED_CONTENT) continue;

            const tokenTypeIndex = mapTokenKind(ktToken.tokenType);
            if (tokenTypeIndex === undefined) continue;

            builder.push(
                ktToken.line,
                ktToken.column,
                ktToken.length,
                tokenTypeIndex,
                0
            );
        }

        return builder.build();
    }
}

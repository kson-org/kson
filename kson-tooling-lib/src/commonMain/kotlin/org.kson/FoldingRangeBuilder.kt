package org.kson

import org.kson.parser.Token
import org.kson.parser.TokenType

/**
 * Builds [StructuralRange] lists from KSON source by matching open/close bracket pairs.
 *
 * Walks the token stream and uses a stack to pair opening delimiters
 * (`{`, `[`, embed open) with their closing counterparts. Only emits
 * ranges that span multiple lines (single-line constructs don't fold).
 */
internal object FoldingRangeBuilder {

    fun build(tokens: List<Token>): List<StructuralRange> {
        val ranges = mutableListOf<StructuralRange>()

        val stack = mutableListOf<OpenToken>()

        for (token in tokens) {
            when (token.tokenType) {
                TokenType.CURLY_BRACE_L ->
                    stack.add(OpenToken(OpenTokenType.BRACE, token.lexeme.location.start.line))

                TokenType.CURLY_BRACE_R -> {
                    val open = popMatching(stack, OpenTokenType.BRACE)
                    if (open != null && token.lexeme.location.start.line > open.startLine) {
                        ranges.add(StructuralRange(open.startLine, token.lexeme.location.start.line, StructuralRangeKind.OBJECT))
                    }
                }

                TokenType.SQUARE_BRACKET_L ->
                    stack.add(OpenToken(OpenTokenType.BRACKET, token.lexeme.location.start.line))

                TokenType.SQUARE_BRACKET_R -> {
                    val open = popMatching(stack, OpenTokenType.BRACKET)
                    if (open != null && token.lexeme.location.start.line > open.startLine) {
                        ranges.add(StructuralRange(open.startLine, token.lexeme.location.start.line, StructuralRangeKind.ARRAY))
                    }
                }

                TokenType.EMBED_OPEN_DELIM ->
                    stack.add(OpenToken(OpenTokenType.EMBED, token.lexeme.location.start.line))

                TokenType.EMBED_CLOSE_DELIM -> {
                    val open = popMatching(stack, OpenTokenType.EMBED)
                    if (open != null && token.lexeme.location.start.line > open.startLine) {
                        ranges.add(StructuralRange(open.startLine, token.lexeme.location.start.line, StructuralRangeKind.EMBED))
                    }
                }

                else -> {}
            }
        }

        return ranges
    }

    private fun popMatching(stack: MutableList<OpenToken>, type: OpenTokenType): OpenToken? {
        for (i in stack.indices.reversed()) {
            if (stack[i].type == type) {
                return stack.removeAt(i)
            }
        }
        return null
    }

    private data class OpenToken(val type: OpenTokenType, val startLine: Int)

    private enum class OpenTokenType { BRACE, BRACKET, EMBED }
}

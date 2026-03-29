package org.kson

import org.kson.ast.*
import org.kson.parser.Token
import org.kson.parser.TokenType

/**
 * Builds [SemanticToken] lists from KSON source using AST-aware key detection.
 *
 * Walks the AST to collect all tokens that belong to object keys, giving
 * reliable key-vs-value distinction regardless of surrounding token context.
 */
internal object SemanticTokenBuilder {

    fun build(tokens: List<Token>, ast: KsonRoot): List<SemanticToken> {
        val keyTokens = mutableSetOf<Token>()
        collectKeyTokens(ast, keyTokens)

        val result = mutableListOf<SemanticToken>()
        for (token in tokens) {
            val kind = classifyToken(token, keyTokens) ?: continue
            result.add(
                SemanticToken(
                    line = token.lexeme.location.start.line,
                    column = token.lexeme.location.start.column,
                    length = token.lexeme.text.length,
                    tokenType = kind
                )
            )
        }
        return result
    }

    private fun collectKeyTokens(node: AstNode, keyTokens: MutableSet<Token>) {
        when (node) {
            is KsonRootImpl -> collectKeyTokens(node.rootNode, keyTokens)
            is ObjectNode -> {
                for (prop in node.properties) {
                    collectKeyTokens(prop, keyTokens)
                }
            }
            is ObjectPropertyNodeImpl -> {
                // Collect all tokens up to the colon so that surrounding quote tokens
                // are classified as KEY alongside the key content itself.
                for (token in node.sourceTokens) {
                    if (token.tokenType == TokenType.COLON) break
                    keyTokens.add(token)
                }
                collectKeyTokens(node.value, keyTokens)
            }
            is ObjectKeyNodeImpl -> {
                // Key tokens are collected at the property level to include surrounding quotes
            }
            is ListNode -> {
                for (element in node.elements) {
                    collectKeyTokens(element, keyTokens)
                }
            }
            is ListElementNodeImpl -> collectKeyTokens(node.value, keyTokens)
            is EmbedBlockNode -> {} // leaf for our purposes
            is AstNodeError -> {} // skip error nodes
            else -> {} // other leaf nodes (strings, numbers, booleans, null)
        }
    }

    private fun classifyToken(token: Token, keyTokens: Set<Token>): SemanticTokenKind? {
        return when (token.tokenType) {
            // Strings and identifiers: key vs value
            TokenType.UNQUOTED_STRING,
            TokenType.STRING_CONTENT,
            TokenType.STRING_OPEN_QUOTE,
            TokenType.STRING_CLOSE_QUOTE -> {
                if (token in keyTokens) SemanticTokenKind.KEY else SemanticTokenKind.STRING
            }

            // Numbers
            TokenType.NUMBER -> SemanticTokenKind.NUMBER

            // Keywords (booleans + null)
            TokenType.TRUE,
            TokenType.FALSE,
            TokenType.NULL -> SemanticTokenKind.KEYWORD

            // Operators and punctuation
            TokenType.COLON,
            TokenType.COMMA,
            TokenType.DOT,
            TokenType.LIST_DASH,
            TokenType.CURLY_BRACE_L,
            TokenType.CURLY_BRACE_R,
            TokenType.SQUARE_BRACKET_L,
            TokenType.SQUARE_BRACKET_R,
            TokenType.ANGLE_BRACKET_L,
            TokenType.ANGLE_BRACKET_R,
            TokenType.END_DASH -> SemanticTokenKind.OPERATOR

            // Comments
            TokenType.COMMENT -> SemanticTokenKind.COMMENT

            // Embed tokens
            TokenType.EMBED_TAG -> SemanticTokenKind.EMBED_TAG
            TokenType.EMBED_CONTENT -> SemanticTokenKind.EMBED_CONTENT
            TokenType.EMBED_PREAMBLE_NEWLINE,
            TokenType.EMBED_OPEN_DELIM,
            TokenType.EMBED_CLOSE_DELIM -> SemanticTokenKind.EMBED_DELIM

            // Skip tokens without semantic meaning
            TokenType.WHITESPACE,
            TokenType.ILLEGAL_CHAR,
            TokenType.EOF -> null
        }
    }
}

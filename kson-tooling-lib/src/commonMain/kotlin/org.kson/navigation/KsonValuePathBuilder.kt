package org.kson.navigation

import org.kson.ToolingDocument
import org.kson.ast.AstNode
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.Token
import org.kson.parser.TokenType
import org.kson.parser.behavior.quotedstring.QuotedStringContentTransformer
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.walker.AstNodeWalker
import org.kson.walker.navigateToLocationWithPointer

/**
 * Context information about a token at a specific location.
 *
 * @param lastToken The last token that starts at or before the location
 * @param isInsideToken Whether the location falls within the token's bounds
 */
private data class TokenContext(
    val lastToken: Token?,
    val isInsideToken: Boolean
)

/**
 * Builds a JSON Pointer path from the document root to a specific cursor
 * location in a KSON document.
 *
 * Operates entirely on a pre-parsed [ToolingDocument]—no re-parsing is
 * performed. Token-context analysis uses the document's [ToolingDocument.meaningfulTokens]
 * (WHITESPACE and COMMENT filtered out), and tree navigation uses the document's
 * raw AST via [AstNodeWalker] and its navigation extensions.
 *
 * Using the AST directly (rather than [org.kson.value.KsonValue]) means path
 * building works even on broken documents — error nodes in the AST are treated
 * as leaves that navigation passes through, while the surrounding tree structure
 * remains intact.
 *
 * The builder handles several cursor-position scenarios:
 * - **Position after colon**: adds the property name to target the value being entered
 * - **Position on a property key**: includes the property name in the path (for definition lookups)
 * - **Position outside token**: removes the last path element to target the parent (for completions)
 *
 * @param document The pre-parsed KSON document
 * @param location The cursor position (zero-based line and column)
 */
class KsonValuePathBuilder(
    private val document: ToolingDocument,
    private val location: Coordinates
) {

    /**
     * Builds a JSON Pointer from the document root to the target location.
     *
     * @param includePropertyKeys If true, keeps the path to the current property even when
     *   the cursor is outside a token. This is useful for "jump to definition" where we
     *   want the property's schema definition, not its parent. Default is true.
     * @return A [JsonPointer] representing the path from root to target,
     *         or null if the document is completely unparseable
     */
    fun buildJsonPointerToPosition(includePropertyKeys: Boolean = true): JsonPointer? {
        val rootNode = document.rootAstNode ?: return if (document.content.isBlank()) JsonPointer.ROOT else null

        // Analyze token context using meaningful (non-whitespace) tokens
        val tokenContext = analyzeTokenContext(document.meaningfulTokens, location)

        // Determine the search position: use token start if available, otherwise location
        val searchPosition = tokenContext.lastToken?.lexeme?.location?.start ?: location

        // Navigate to the target node and build the path via the AST walker
        val navResult = AstNodeWalker.navigateToLocationWithPointer(
            rootNode, searchPosition
        ) ?: return JsonPointer.ROOT

        // Adjust the path based on token context (colon handling, boundary checks)
        return adjustPathForLocationContext(
            pointer = navResult.pointerFromRoot,
            lastToken = tokenContext.lastToken,
            targetNode = navResult.value,
            isLocationInsideToken = tokenContext.isInsideToken,
            includePropertyKeys = includePropertyKeys,
            meaningfulTokens = document.meaningfulTokens
        )
    }

    /**
     * Analyzes the token context at a specific location.
     *
     * Determines which token (if any) is at or before the location,
     * and whether the location falls within that token's bounds.
     */
    private fun analyzeTokenContext(
        tokens: List<Token>,
        location: Coordinates
    ): TokenContext {
        val lastToken = findLastTokenBeforeLocation(tokens, location)
        val isInsideToken = isPositionInsideToken(lastToken, location)
        return TokenContext(lastToken, isInsideToken)
    }

    /**
     * Finds the last token that starts at or before the location.
     * The EOF token is excluded from consideration.
     */
    private fun findLastTokenBeforeLocation(
        tokens: List<Token>,
        location: Coordinates
    ): Token? {
        return tokens
            .dropLast(1)  // Exclude EOF token
            .lastOrNull { token ->
                val tokenStart = token.lexeme.location.start
                tokenStart.line < location.line ||
                        (tokenStart.line == location.line && tokenStart.column <= location.column)
            }
    }

    /**
     * Checks if the given position falls within the bounds of a token.
     */
    private fun isPositionInsideToken(
        token: Token?,
        position: Coordinates
    ): Boolean {
        return token?.lexeme?.location?.let {
            Location.containsCoordinates(it, position)
        } ?: false
    }

    /**
     * Finds the property name from the token stream that precedes a COLON token.
     *
     * Walks backwards from the colon to find the nearest UNQUOTED_STRING or
     * STRING_CONTENT token, which contains the property key text.
     *
     * For STRING_CONTENT tokens (quoted keys), escape sequences are processed
     * to produce the logical property name.
     */
    private fun findPropertyNameBeforeColon(colonToken: Token, meaningfulTokens: List<Token>): String? {
        val colonIndex = meaningfulTokens.indexOf(colonToken)
        if (colonIndex <= 0) return null
        // Walk backwards to find the key token (skip STRING_CLOSE_QUOTE if present)
        for (i in (colonIndex - 1) downTo 0) {
            val token = meaningfulTokens[i]
            when (token.tokenType) {
                TokenType.UNQUOTED_STRING -> return token.lexeme.text
                TokenType.STRING_CONTENT -> return QuotedStringContentTransformer(
                    token.lexeme.text,
                    token.lexeme.location
                ).processedContent

                TokenType.STRING_CLOSE_QUOTE -> continue // skip quote, look for content
                else -> return null
            }
        }
        return null
    }

    /**
     * Finds the nearest COLON at or before [lastToken] in the meaningful token stream.
     * Returns the COLON token if [lastToken] is itself a COLON, or if the token
     * immediately preceding [lastToken] is a COLON (handles the case where the cursor
     * lands on a COMMA or other delimiter right after an empty value position).
     */
    private fun findNearestPrecedingColon(lastToken: Token, meaningfulTokens: List<Token>): Token? {
        if (lastToken.tokenType == TokenType.COLON) return lastToken
        val idx = meaningfulTokens.indexOf(lastToken)
        if (idx <= 0) return null
        val prev = meaningfulTokens[idx - 1]
        return if (prev.tokenType == TokenType.COLON) prev else null
    }

    /**
     * Adjusts the path based on token context.
     *
     * Handles special cases:
     * 1. Location after a colon: Add the property name to target the value being entered
     * 2. Location on a property key: Add the property name to the path (for definition lookups)
     * 3. Location outside token bounds: Remove the last path element to target the parent
     *    (unless includePropertyKeys is true, in which case keep the path to the property)
     */
    private fun adjustPathForLocationContext(
        pointer: JsonPointer,
        lastToken: Token?,
        targetNode: AstNode,
        isLocationInsideToken: Boolean,
        includePropertyKeys: Boolean,
        meaningfulTokens: List<Token>
    ): JsonPointer {
        // Check if the nearest preceding colon indicates we're entering a value.
        // Only apply when the colon falls INSIDE the targetNode's bounds — this
        // means targetNode is the parent object that owns the colon. If the colon
        // is outside targetNode, it means navigation already descended through the
        // property into its value object, and the pointer already contains the
        // property name. This structural check avoids false negatives from string
        // comparison of property names (which breaks with repeated names at
        // different levels, e.g. {"name": {"name": }}).
        val colonToken = lastToken?.let { findNearestPrecedingColon(it, meaningfulTokens) }
        val colonPropertyName = colonToken?.let { findPropertyNameBeforeColon(it, meaningfulTokens) }
        val isAfterColonAtParent = colonPropertyName != null &&
                AstNodeWalker.isObject(targetNode) &&
                Location.containsCoordinates(AstNodeWalker.getLocation(targetNode), colonToken.lexeme.location.start)
        val tokens = when {
            isAfterColonAtParent -> {
                pointer.tokens + colonPropertyName
            }
            // Location is on a property key (UNQUOTED_STRING, STRING_OPEN_QUOTE, or STRING_CONTENT token) and we're at the parent object
            // This happens when location is in the middle of a property name like "user<caret>name"
            isLocationInsideToken &&
                    (lastToken?.tokenType == TokenType.UNQUOTED_STRING ||
                            lastToken?.tokenType == TokenType.STRING_OPEN_QUOTE ||
                            lastToken?.tokenType == TokenType.STRING_CONTENT) &&
                    AstNodeWalker.isObject(targetNode) &&
                    includePropertyKeys -> {
                // Extract the property name from the token, processing escapes for quoted keys
                val propertyName = if (lastToken.tokenType == TokenType.STRING_CONTENT)
                    QuotedStringContentTransformer(lastToken.lexeme.text, lastToken.lexeme.location).processedContent
                else
                    lastToken.lexeme.text
                pointer.tokens + propertyName
            }
            // Location is outside the token - target the parent element (for completions)
            // But keep the path as-is for definition lookups
            !isLocationInsideToken && !includePropertyKeys -> {
                pointer.tokens.dropLast(1)
            }
            // Normal case - return path as-is
            else -> pointer.tokens
        }
        return JsonPointer.fromTokens(tokens)
    }
}

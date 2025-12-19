package org.kson.navigation

import org.kson.KsonCore
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.Token
import org.kson.parser.TokenType
import org.kson.schema.JsonPointer
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.value.KsonValueNavigation

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
 * Builds a path from the document root to a specific location in a KSON document.
 *
 * This class analyzes a KSON document and position to construct a path
 * (sequence of property names) from the root to the element at the given location.
 * The path is used for schema navigation, IDE auto-completion, and hover information.
 *
 * The builder handles several scenarios:
 * - **Invalid documents**: Attempts recovery by inserting empty quotes at the position
 * - **Position after colon**: Adds the property name to target the value being entered
 * - **Position outside token**: Removes the last path element to target the parent
 *
 * Example usage:
 * ```kotlin
 * val document = "name: John\nage: 30"
 * val location = Coordinates(0, 6)  // Position in "John"
 * val builder = KsonValuePathBuilder(document, location)
 * val path = builder.buildPathToPosition()  // Returns ["name"]
 * ```
 *
 * @param document The KSON document string to analyze
 * @param location The position (line and column, zero-based)
 *
 * @see buildJsonPointerToPosition Main method to build the path
 * @see org.kson.value.KsonValueNavigation For navigation within parsed KSON values
 */
class KsonValuePathBuilder(private val document: String, private val location: Coordinates) {

    /**
     * Builds a path from the document root to the target location.
     *
     * This method analyzes the document structure and position to determine
     * the JSON path (sequence of property names) from the root to the element at
     * the given location. This path is used for schema navigation and IDE features.
     *
     * The method handles several edge cases:
     * - Invalid documents (attempts recovery by inserting quotes)
     * - Positioned after a colon (targets the value being entered)
     * - Positioned outside a token (targets the parent element, unless includePropertyKeys is true)
     *
     * @param includePropertyKeys If true, keeps the path to the current property even when position is outside token.
     *                            This is useful for "jump to definition" where we want to navigate to the property's
     *                            schema definition, not its parent. Default is false for completion behavior.
     * @return A list of property names representing the path from root to target,
     *         or null if the path cannot be determined
     */
    fun buildJsonPointerToPosition(includePropertyKeys: Boolean = true): JsonPointer? {
        val parsedDocument = KsonCore.parseToAst(document)

        // Analyze token context at the target location
        val tokenContext = analyzeTokenContext(parsedDocument.lexedTokens, location)

        // Parse the document, or attempt recovery if it contains syntax errors
        val documentValue = parsedDocument.ksonValue
            ?: attemptDocumentRecovery(document, location)
            ?: return null

        // Determine the search position: use token start if available, otherwise location
        val searchPosition = tokenContext.lastToken?.lexeme?.location?.start ?: location

        // Navigate to the target node and build the path in a single traversal
        val navResult = KsonValueNavigation.navigateToLocationWithPointer(documentValue, searchPosition)
            ?: return JsonPointer.ROOT

        // Adjust the path based on token context (colon handling, boundary checks)
        return adjustPathForLocationContext(
            pointer = navResult.pointerFromRoot,
            lastToken = tokenContext.lastToken,
            targetNode = navResult.targetNode,
            isLocationInsideToken = tokenContext.isInsideToken,
            includePropertyKeys = includePropertyKeys
            )

    }

    /**
     * Analyzes the token context at a specific location.
     *
     * Determines which token (if any) is at or before the location,
     * and whether the location falls within that token's bounds.
     *
     * @param tokens The list of lexed tokens from the document
     * @param location The position (line and column, zero-based)
     * @return Token context information
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
     *
     * This helps determine what syntactic element is positioned at or after.
     * The EOF token is excluded from consideration.
     *
     * @param tokens The list of lexed tokens from the document
     * @param location The position (line and column, zero-based)
     * @return The last token before the location, or null if no such token exists
     */
    private fun findLastTokenBeforeLocation(
        tokens: List<Token>,
        location: Coordinates
    ): Token? {
        return tokens
            .dropLast(1)  // Exclude EOF token
            .lastOrNull { token ->
                val tokenStart = token.lexeme.location.start
                // Token starts before or at location
                tokenStart.line < location.line ||
                    (tokenStart.line == location.line && tokenStart.column <= location.column)
            }
    }

    /**
     * Checks if the given position falls within the bounds of a token.
     *
     * @param token The token to check, or null
     * @param position The position to test
     * @return true if the position is inside the token's location, false otherwise
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
     * Adjusts the path based on token context.
     *
     * Handles special cases:
     * 1. Location after a colon: Add the property name to target the value being entered
     * 2. Location on a property key: Add the property name to the path (for definition lookups)
     * 3. Location outside token bounds: Remove the last path element to target the parent
     *    (unless includePropertyKeys is true, in which case keep the path to the property)
     *
     * @param pointer The initial [JsonPointer] built from document navigation
     * @param lastToken The last token before the location
     * @param targetNode The KsonValue node found at the target location
     * @param isLocationInsideToken Whether the location is inside the token bounds
     * @param includePropertyKeys If true, don't drop the last element when location is outside token
     * @return The adjusted path
     */
    private fun adjustPathForLocationContext(
        pointer: JsonPointer,
        lastToken: Token?,
        targetNode: KsonValue,
        isLocationInsideToken: Boolean,
        includePropertyKeys: Boolean
    ): JsonPointer {
        val tokens = when {
            // Location is right after a colon - we're entering a value
            lastToken?.tokenType == TokenType.COLON -> {
                val propertyName = (targetNode as KsonObject).propertyLookup.keys.last()
                pointer.tokens + propertyName
            }
            // Location is on a property key (UNQUOTED_STRING, STRING_OPEN_QUOTE, or STRING_CONTENT token) and we're at the parent object
            // This happens when location is in the middle of a property name like "user<caret>name"
            isLocationInsideToken &&
            (lastToken?.tokenType == TokenType.UNQUOTED_STRING ||
             lastToken?.tokenType == TokenType.STRING_OPEN_QUOTE ||
             lastToken?.tokenType == TokenType.STRING_CONTENT) &&
            targetNode is KsonObject &&
            includePropertyKeys -> {
                // Extract the property name from the token
                val propertyName = lastToken.lexeme.text
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

    /**
     * Attempts to recover a parseable document from an invalid one.
     *
     * When a document contains syntax errors, this method tries to make it valid
     * by inserting an empty list, `[]`, at the location. This is useful for
     * providing IDE features even when the user is in the middle of typing.
     *
     * For example, if the location is at `{ "key": | }`, this would try parsing
     * `{ "key": [] }` to enable completions for the value.
     *
     * @param document The invalid document string
     * @param location The position where quotes should be inserted
     * @return A KsonValue from the recovered document, or null if recovery fails
     */
    private fun attemptDocumentRecovery(
        document: String,
        location: Coordinates
    ): KsonValue? {
        val lines = document.lines().toMutableList()

        // Validate that the target line exists
        if (location.line >= lines.size) {
            return null
        }

        val targetLine = lines[location.line]
        val safeColumn = (location.column).coerceAtMost(targetLine.length)

        // Insert empty list at the position
        val recoveredLine = buildString {
            append(targetLine.take(safeColumn))
            append("[]")  // Empty string literal
            append(targetLine.substring(safeColumn))
        }

        lines[location.line] = recoveredLine
        val recoveredDocument = lines.joinToString("\n")

        // Attempt to parse the recovered document
        return KsonCore.parseToAst(recoveredDocument).ksonValue
    }
}
package org.kson.navigation

import org.kson.KsonCore
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.TokenType
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.value.KsonValueNavigation
import org.kson.value.KsonValueNavigation.navigateByTokens

/**
 * Builds a path from the document root to a specific location in a KSON document.
 *
 * This class analyzes a KSON document and cursor position to construct a path
 * (sequence of property names) from the root to the element at the given location.
 * The path is used for schema navigation, IDE auto-completion, and hover information.
 *
 * The builder handles several scenarios:
 * - **Invalid documents**: Attempts recovery by inserting empty quotes at the cursor position
 * - **Cursor after colon**: Adds the property name to target the value being entered
 * - **Cursor outside token**: Removes the last path element to target the parent
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
 * @param location The cursor position (line and column, zero-based)
 *
 * @see buildPathToPosition Main method to build the path
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
     * - Cursor positioned after a colon (targets the value being entered)
     * - Cursor positioned outside a token (targets the parent element)
     *
     * @return A list of property names representing the path from root to target,
     *         or null if the path cannot be determined
     */
    fun buildPathToPosition(): List<String>? {
        val parsedDocument = KsonCore.parseToAst(document)

        // Find the token immediately before or at the cursor position
        val lastToken = findLastTokenBeforeCursor(parsedDocument.lexedTokens, location)

        // Check if the cursor is actually inside the token's bounds
        val isCursorInsideToken = isPositionInsideToken(lastToken, location)

        // Parse the document, or attempt recovery if it contains syntax errors
        val documentValue = parsedDocument.ksonValue
            ?: attemptDocumentRecovery(document, location)
            ?: return null

        // Determine the search position: use token start if available, otherwise cursor position
        val searchPosition = lastToken?.lexeme?.location?.start ?: location

        // Navigate to the KsonValue node at the search position
        val targetNode = KsonValueNavigation.findValueAtPosition(documentValue, searchPosition)
            ?: return null

        // Build the initial path from root to the target node
        val initialPath = buildPathTokens(documentValue, targetNode)
            ?: return emptyList()

        // Adjust the path based on cursor context (colon handling, boundary checks)
        return adjustPathForCursorContext(
            path = initialPath,
            lastToken = lastToken,
            targetNode = targetNode,
            isCursorInsideToken = isCursorInsideToken
        )
    }

    /**
     * Finds the last token that starts at or before the cursor location.
     *
     * This helps determine what syntactic element the cursor is positioned at or after.
     * The EOF token is excluded from consideration.
     *
     * @param tokens The list of lexed tokens from the document
     * @param location The cursor position
     * @return The last token before the cursor, or null if no such token exists
     */
    private fun findLastTokenBeforeCursor(
        tokens: List<org.kson.parser.Token>,
        location: Coordinates
    ): org.kson.parser.Token? {
        return tokens
            .dropLast(1)  // Exclude EOF token
            .lastOrNull { token ->
                val tokenStart = token.lexeme.location.start
                // Token starts before or at the cursor location
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
        token: org.kson.parser.Token?,
        position: Coordinates
    ): Boolean {
        return token?.lexeme?.location?.let {
            Location.containsCoordinates(it, position)
        } ?: false
    }

    /**
     * Adjusts the path based on cursor context.
     *
     * Handles two special cases:
     * 1. Cursor after a colon: Add the property name to target the value being entered
     * 2. Cursor outside token bounds: Remove the last path element to target the parent
     *
     * @param path The initial path built from document navigation
     * @param lastToken The last token before the cursor
     * @param targetNode The KsonValue node found at the target location
     * @param isCursorInsideToken Whether the cursor is inside the token bounds
     * @return The adjusted path
     */
    private fun adjustPathForCursorContext(
        path: List<String>,
        lastToken: org.kson.parser.Token?,
        targetNode: KsonValue,
        isCursorInsideToken: Boolean
    ): List<String> {
        return when {
            // Cursor is right after a colon - we're entering a value
            lastToken?.tokenType == TokenType.COLON -> {
                val propertyName = (targetNode as KsonObject).propertyLookup.keys.last()
                path + propertyName
            }
            // Cursor is outside the token - target the parent element
            !isCursorInsideToken -> {
                path.dropLast(1)
            }
            // Normal case - return path as-is
            else -> path
        }
    }

    /**
     * Attempts to recover a parseable document from an invalid one.
     *
     * When a document contains syntax errors, this method tries to make it valid
     * by inserting empty quotes at the cursor position. This is useful for
     * providing IDE features even when the user is in the middle of typing.
     *
     * For example, if the cursor is at `{ "key": | }`, this would try parsing
     * `{ "key": "" }` to enable completions for the value.
     *
     * @param document The invalid document string
     * @param location The cursor position where quotes should be inserted
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
        val safeColumn = location.column.coerceAtMost(targetLine.length)

        // Insert empty quotes at the cursor position
        val recoveredLine = buildString {
            append(targetLine.take(safeColumn))
            append("\"\"")  // Empty string literal
            append(targetLine.substring(safeColumn))
        }

        lines[location.line] = recoveredLine
        val recoveredDocument = lines.joinToString("\n")

        // Attempt to parse the recovered document
        return KsonCore.parseToAst(recoveredDocument).ksonValue
    }


    /**
     * Build a path from root to target as a list of string tokens.
     *
     * The returned tokens can be used with [navigateByTokens] to navigate
     * back to the target node.
     *
     * @param root The root of the tree
     * @param target The node to find the path to
     * @return List of tokens forming the path, or null if target is not in the tree
     *         Returns empty list if target is the root
     *
     * Example:
     * ```kotlin
     * val path = buildPathTokens(root, targetNode)
     * // path might be ["users", "0", "name"]
     *
     * // Verify we can navigate back
     * val found = navigateByTokens(root, path)
     * assert(found === targetNode)
     * ```
     */
    private fun buildPathTokens(root: KsonValue, target: KsonValue): List<String>? {
        if (root === target) return emptyList()

        val path = mutableListOf<String>()

        fun search(node: KsonValue): Boolean {
            if (node === target) return true

            when (node) {
                is KsonObject -> {
                    for ((key, property) in node.propertyMap) {
                        path.add(key)
                        if (search(property.propValue)) return true
                        path.removeLast()
                    }
                }

                is KsonList -> {
                    for ((index, element) in node.elements.withIndex()) {
                        path.add(index.toString())
                        if (search(element)) return true
                        path.removeLast()
                    }
                }

                else -> {
                    // Primitive types (KsonString, KsonNumber, KsonBoolean, KsonNull, etc.) have no children
                    // If we reach here and haven't found the target, return false
                }
            }
            return false
        }

        return if (search(root)) path else null
    }
}
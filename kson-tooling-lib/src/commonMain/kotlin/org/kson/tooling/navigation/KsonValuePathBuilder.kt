package org.kson.tooling.navigation

import org.kson.ast.AstNode
import org.kson.ast.ObjectNode
import org.kson.ast.ObjectPropertyNodeImpl
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.Token
import org.kson.parser.TokenType
import org.kson.parser.behavior.quotedstring.QuotedStringContentTransformer
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.walker.AstNodeWalker
import org.kson.walker.NodeChildren
import org.kson.walker.navigateToLocationWithPointer
import org.kson.walker.navigateWithJsonPointer
import org.kson.tooling.ToolingDocument

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
 * The result of resolving a caret position: the [JsonPointer] from the document root to the
 * target value, plus the span of the value the caret is currently authoring (the "placeholder").
 *
 * [placeholderLocation] is only populated for completion (where the half-typed value must not
 * disqualify the schema branches it selects among); it is null for definition/hover lookups,
 * which treat the committed value at the caret as authoritative.  During narrowing the navigator
 * forgives validation errors raised inside this span, so the half-typed value never disqualifies a branch.
 *
 * [caretPastValueToken] is true when the caret sits at or beyond a committed string value's
 * close-quote token — the "value is finished" position (`key: 'value'|`) used to stop offering
 * value completions once the caret has moved past a quoted value.  It is false while the caret is
 * still inside the value (including between a string's quotes, `'|'` / `'ac|'`) and for non-value
 * contexts.  An unquoted scalar's end-of-token position (`key: value|`) is deliberately treated as
 * still authoring — mid-typing is the common completion trigger and clients prefix-filter — so it
 * never sets this flag.
 */
data class CaretPath(
    val pointer: JsonPointer,
    val placeholderLocation: Location?,
    val caretPastValueToken: Boolean = false
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
    fun buildJsonPointerToPosition(includePropertyKeys: Boolean = true): JsonPointer? =
        buildCaretPath(includePropertyKeys)?.pointer

    /**
     * Resolves the caret position into a [CaretPath]: the [JsonPointer] to the target value plus
     * the placeholder span of the value the caret is authoring.
     *
     * The placeholder is derived from the same token context that drives path adjustment, so it is
     * known precisely at the point the pointer is built — no re-derivation from the parsed value by
     * caret coordinates is needed.  It is only populated for completion (`includePropertyKeys =
     * false`); definition/hover lookups leave it null and treat the committed value as authoritative.
     *
     * @return The resolved [CaretPath], or null if the document is completely unparseable
     */
    fun buildCaretPath(includePropertyKeys: Boolean = true): CaretPath? {
        val rootNode = document.rootAstNode
            ?: return if (document.content.isBlank()) CaretPath(JsonPointer.ROOT, null) else null

        // Analyze token context using meaningful (non-whitespace) tokens
        val tokenContext = analyzeTokenContext(document.meaningfulTokens, location)

        // Determine the search position: use token start if available, otherwise location
        val searchPosition = tokenContext.lastToken?.lexeme?.location?.start ?: location

        // Navigate to the target node and build the path via the AST walker
        val navResult = AstNodeWalker.navigateToLocationWithPointer(
            rootNode, searchPosition
        ) ?: return CaretPath(JsonPointer.ROOT, null)

        // Adjust the path based on token context (colon handling, boundary checks)
        return adjustPathForLocationContext(
            pointer = navResult.pointerFromRoot,
            lastToken = tokenContext.lastToken,
            targetNode = navResult.value,
            isLocationInsideToken = tokenContext.isInsideToken,
            includePropertyKeys = includePropertyKeys,
            meaningfulTokens = document.meaningfulTokens,
            rootNode = rootNode
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

    /** True when [caret] is at or beyond [boundary] in document (line, then column) order. */
    private fun isAtOrAfter(caret: Coordinates, boundary: Coordinates): Boolean =
        caret.line > boundary.line || (caret.line == boundary.line && caret.column >= boundary.column)

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
        meaningfulTokens: List<Token>,
        rootNode: AstNode
    ): CaretPath {
        val colonToken = lastToken?.let { findNearestPrecedingColon(it, meaningfulTokens) }
        val colonPropertyName = colonToken?.let { findPropertyNameBeforeColon(it, meaningfulTokens) }

        // Cases are tried in priority order; each helper returns null when its case does not apply,
        // and the final leaf/as-is case always produces a result.
        afterColonCaretPath(pointer, colonToken, colonPropertyName, targetNode)?.let { return it }
        propertyKeyCaretPath(pointer, lastToken, isLocationInsideToken, targetNode, includePropertyKeys)
            ?.let { return it }
        parentCaretPath(pointer, lastToken, isLocationInsideToken, includePropertyKeys, targetNode, rootNode)
            ?.let { return it }
        return leafOrAsIsCaretPath(pointer, lastToken, targetNode, includePropertyKeys)
    }

    /**
     * Caret sitting right after `key:` with no value committed yet: target the value being entered by
     * appending the colon's property name.  Applies only when the colon falls INSIDE [targetNode]'s
     * bounds — i.e. [targetNode] is the parent object that owns the colon.  If the colon is outside
     * [targetNode], navigation already descended through the property into its value and the pointer
     * already contains the name; this structural check avoids false negatives from comparing repeated
     * property names at different levels (e.g. `{"name": {"name": }}`).  There is no half-typed value to
     * exclude from narrowing here (a committed value would instead land the caret inside its token).
     * Returns null when the caret is not in this position.
     */
    private fun afterColonCaretPath(
        pointer: JsonPointer,
        colonToken: Token?,
        colonPropertyName: String?,
        targetNode: AstNode
    ): CaretPath? {
        if (colonToken == null || colonPropertyName == null) return null
        val atParentObject = AstNodeWalker.getChildren(targetNode) is NodeChildren.Object &&
                Location.containsCoordinates(AstNodeWalker.getLocation(targetNode), colonToken.lexeme.location.start)
        if (!atParentObject) return null
        return CaretPath(JsonPointer.fromTokens(pointer.tokens + colonPropertyName), placeholderLocation = null)
    }

    /**
     * Location on a property key (UNQUOTED_STRING, STRING_OPEN_QUOTE, or STRING_CONTENT token) at the
     * parent object — e.g. mid-name in `user<caret>name` — while keeping property keys (definition
     * lookups): add the property name to the path.  Returns null when the caret is not on a key.
     */
    private fun propertyKeyCaretPath(
        pointer: JsonPointer,
        lastToken: Token?,
        isLocationInsideToken: Boolean,
        targetNode: AstNode,
        includePropertyKeys: Boolean
    ): CaretPath? {
        val onPropertyKey = isLocationInsideToken &&
                (lastToken?.tokenType == TokenType.UNQUOTED_STRING ||
                        lastToken?.tokenType == TokenType.STRING_OPEN_QUOTE ||
                        lastToken?.tokenType == TokenType.STRING_CONTENT) &&
                AstNodeWalker.getChildren(targetNode) is NodeChildren.Object &&
                includePropertyKeys
        if (!onPropertyKey) return null
        // lastToken is non-null here: onPropertyKey can only be true when a key token matched.
        // Extract the property name from the token, processing escapes for quoted keys
        val propertyName = if (lastToken.tokenType == TokenType.STRING_CONTENT)
            QuotedStringContentTransformer(lastToken.lexeme.text, lastToken.lexeme.location).processedContent
        else
            lastToken.lexeme.text
        return CaretPath(JsonPointer.fromTokens(pointer.tokens + propertyName), placeholderLocation = null)
    }

    /**
     * Location outside the token while completing (not keeping property keys): target the parent element
     * by dropping the last path segment.  Excludes container-opening delimiters (`[`, `{`, `<`), where the
     * caret is inside an empty container the pointer already names and dropping would overshoot to the
     * grandparent.  A fresh dash-list item (`- `) additionally exposes its enclosing property as the
     * placeholder so its incomplete item never disqualifies the branches being completed; on a fresh
     * property-name line the caret follows a committed sibling's last token (not a dash), so there is no
     * placeholder and those committed siblings still narrow.  Returns null when the caret is not in this
     * position.
     */
    private fun parentCaretPath(
        pointer: JsonPointer,
        lastToken: Token?,
        isLocationInsideToken: Boolean,
        includePropertyKeys: Boolean,
        targetNode: AstNode,
        rootNode: AstNode
    ): CaretPath? {
        val targetsParent = !isLocationInsideToken && !includePropertyKeys &&
                lastToken?.tokenType != TokenType.SQUARE_BRACKET_L &&
                lastToken?.tokenType != TokenType.CURLY_BRACE_L &&
                lastToken?.tokenType != TokenType.ANGLE_BRACKET_L
        if (!targetsParent) return null
        val parentPointer = JsonPointer.fromTokens(pointer.tokens.dropLast(1))
        val placeholder = if (lastToken?.tokenType == TokenType.LIST_DASH)
            enclosingPropertyLocation(rootNode, parentPointer, targetNode)
        else null
        return CaretPath(parentPointer, placeholder)
    }

    /**
     * Fallback: return the path as-is.  A scalar value the caret is inside is the placeholder; an object
     * or array literal is a committed structural choice whose own type must still narrow (e.g. a list
     * literal where an object is expected), so it is never excluded.  [CaretPath.caretPastValueToken] is
     * set once the caret reaches the end of a committed string value's close-quote token.
     */
    private fun leafOrAsIsCaretPath(
        pointer: JsonPointer,
        lastToken: Token?,
        targetNode: AstNode,
        includePropertyKeys: Boolean
    ): CaretPath {
        val isLeafValue = !includePropertyKeys && AstNodeWalker.getChildren(targetNode) is NodeChildren.Leaf
        val placeholder = if (isLeafValue) AstNodeWalker.getLocation(targetNode) else null
        val caretPastValueToken = isLeafValue && lastToken != null &&
                lastToken.tokenType == TokenType.STRING_CLOSE_QUOTE &&
                isAtOrAfter(location, lastToken.lexeme.location.end)
        return CaretPath(pointer, placeholder, caretPastValueToken)
    }

    /**
     * Location of the object property at [parentPointer] whose value is [valueNode], or null when
     * [parentPointer] is not an object or owns no such property (e.g. a dash list nested directly
     * in another list).  The property's location spans its key through its value; passing it as the
     * caret's incomplete region forgives the type/additional-property errors the half-typed item
     * would otherwise trigger against sibling-discriminated branches.
     */
    private fun enclosingPropertyLocation(
        rootNode: AstNode,
        parentPointer: JsonPointer,
        valueNode: AstNode
    ): Location? {
        val parent = AstNodeWalker.navigateWithJsonPointer(rootNode, parentPointer) as? ObjectNode ?: return null
        val property = parent.properties.firstOrNull {
            (it as? ObjectPropertyNodeImpl)?.value === valueNode
        } ?: return null
        return AstNodeWalker.getLocation(property)
    }
}

package org.kson.walker

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.value.navigation.json_pointer.ExperimentalJsonPointerGlobLanguage
import org.kson.value.navigation.json_pointer.GlobMatcher
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.value.navigation.json_pointer.JsonPointerGlob
import org.kson.value.navigation.json_pointer.PointerParser

/**
 * Result of navigating to a location in a tree.
 *
 * @param value The most specific node at the target location
 * @param pointerFromRoot [JsonPointer] from root to target
 */
data class TreeNavigationResult<N>(
    val value: N,
    val pointerFromRoot: JsonPointer
)

/**
 * Generic tree navigation algorithms that work with any [KsonTreeWalker] implementation.
 *
 * These algorithms enable the same navigation logic to operate on different tree
 * representations (e.g. [org.kson.value.KsonValue] trees and IntelliJ PSI trees).
 */
object TreeNavigation {

    /**
     * Navigate a tree using a JSON Pointer.
     *
     * Follows each token in the pointer through the tree:
     * - For object nodes: matches token against property names
     * - For array nodes: matches token as an integer index
     *
     * @return The node at the end of the path, or null if navigation fails
     */
    fun <N> navigateWithJsonPointer(
        walker: KsonTreeWalker<N>,
        root: N,
        pointer: JsonPointer
    ): N? {
        // JSON Pointer always has only Literal tokens, so we expect exactly 0 or 1 result
        return navigateByParsedTokens(walker, listOf(root), pointer.rawTokens).firstOrNull()
    }

    /**
     * Navigate a tree using a JsonPointerGlob (with wildcard and pattern support).
     *
     * JsonPointerGlob extends JSON Pointer with:
     * - Wildcard tokens: `*` matches any single key or array index
     * - Glob patterns: tokens containing `*` or `?` match using glob-style patterns
     * - Recursive descent: `**` matches zero or more levels
     *
     * Unlike [navigateWithJsonPointer], this returns ALL matching nodes, since wildcards
     * and patterns can match multiple keys/indices at each level.
     *
     * @return List of all nodes matching the pointer (empty list if no matches)
     */
    @OptIn(ExperimentalJsonPointerGlobLanguage::class)
    fun <N> navigateWithJsonPointerGlob(
        walker: KsonTreeWalker<N>,
        root: N,
        pointer: JsonPointerGlob
    ): List<N> {
        return navigateByParsedTokens(walker, listOf(root), pointer.rawTokens)
    }

    /**
     * Find the most specific node at a location and build the JSON Pointer path to it.
     *
     * Recursively descends the tree, checking if the target location falls within
     * each node's bounds. Returns the deepest (most specific) node that contains
     * the target location, along with the accumulated path from root.
     *
     * @return Result containing the target node and path, or null if the target
     *         location is not within the root's bounds
     */
    fun <N> navigateToLocationWithPointer(
        walker: KsonTreeWalker<N>,
        root: N,
        targetLocation: Coordinates,
        currentPointer: JsonPointer = JsonPointer.ROOT
    ): TreeNavigationResult<N>? {
        if (!Location.containsCoordinates(walker.getLocation(root), targetLocation)) {
            return null
        }

        if (walker.isObject(root)) {
            for (prop in walker.getObjectProperties(root)) {
                val childResult = navigateToLocationWithPointer(
                    walker, prop.value, targetLocation,
                    JsonPointer.fromTokens(currentPointer.tokens + prop.name)
                )
                if (childResult != null) return childResult
            }
        } else if (walker.isArray(root)) {
            for ((index, child) in walker.getArrayElements(root).withIndex()) {
                val childResult = navigateToLocationWithPointer(
                    walker, child, targetLocation,
                    JsonPointer.fromTokens(currentPointer.tokens + index.toString())
                )
                if (childResult != null) return childResult
            }
        }

        return TreeNavigationResult(root, currentPointer)
    }

    /**
     * Navigate through a tree using parsed tokens (JSON Pointer/JsonPointerGlob style).
     *
     * Supports four token types:
     * - [PointerParser.Tokens.Literal]: Exact match against property names or array indices
     * - [PointerParser.Tokens.Wildcard]: Matches all keys/indices at one level
     * - [PointerParser.Tokens.RecursiveDescent]: Matches zero or more levels (depth-first)
     * - [PointerParser.Tokens.GlobPattern]: Pattern matching with * and ?
     */
    private fun <N> navigateByParsedTokens(
        walker: KsonTreeWalker<N>,
        roots: List<N>,
        tokens: List<PointerParser.Tokens>
    ): List<N> {
        return navigateRecursive(walker, roots, tokens, tokenIndex = 0)
    }

    private tailrec fun <N> navigateRecursive(
        walker: KsonTreeWalker<N>,
        currentNodes: List<N>,
        tokens: List<PointerParser.Tokens>,
        tokenIndex: Int
    ): List<N> {
        if (tokenIndex >= tokens.size || currentNodes.isEmpty()) {
            return currentNodes
        }

        val token = tokens[tokenIndex]

        // RecursiveDescent is special: it consumes all remaining tokens and terminates
        if (token is PointerParser.Tokens.RecursiveDescent) {
            return handleRecursiveDescent(walker, currentNodes, tokens, tokenIndex)
        }

        // All other tokens: process one step, then continue to next token
        val nextNodes = processSingleToken(walker, currentNodes, token)
        return navigateRecursive(walker, nextNodes, tokens, tokenIndex + 1)
    }

    private fun <N> handleRecursiveDescent(
        walker: KsonTreeWalker<N>,
        currentNodes: List<N>,
        tokens: List<PointerParser.Tokens>,
        tokenIndex: Int
    ): List<N> {
        val remainingTokens = tokens.subList(tokenIndex + 1, tokens.size)
        return currentNodes.flatMap { node ->
            val descendants = collectAllDescendants(walker, node)
            if (remainingTokens.isEmpty()) {
                // ** at end: for /**, include node; for /path/**, exclude it
                if (tokenIndex == 0) descendants else descendants.drop(1)
            } else {
                // ** in middle: try matching remaining tokens at all levels
                descendants.flatMap { candidate ->
                    navigateByParsedTokens(walker, listOf(candidate), remainingTokens)
                }
            }
        }
    }

    private fun <N> processSingleToken(
        walker: KsonTreeWalker<N>,
        currentNodes: List<N>,
        token: PointerParser.Tokens
    ): List<N> {
        return when (token) {
            is PointerParser.Tokens.Literal -> {
                currentNodes.mapNotNull { node ->
                    when {
                        walker.isObject(node) ->
                            walker.getObjectProperty(node, token.value)

                        walker.isArray(node) -> {
                            val index = token.value.toIntOrNull()
                            val elements = walker.getArrayElements(node)
                            if (index != null && index >= 0 && index < elements.size) {
                                elements[index]
                            } else {
                                null
                            }
                        }

                        else -> null
                    }
                }
            }

            is PointerParser.Tokens.Wildcard -> {
                currentNodes.flatMap { node ->
                    when {
                        walker.isObject(node) -> walker.getObjectProperties(node).map { it.value }
                        walker.isArray(node) -> walker.getArrayElements(node)
                        else -> emptyList()
                    }
                }
            }

            is PointerParser.Tokens.GlobPattern -> {
                currentNodes.flatMap { node ->
                    when {
                        walker.isObject(node) -> {
                            walker.getObjectProperties(node)
                                .filter { GlobMatcher.matches(token.pattern, it.name) }
                                .map { it.value }
                        }

                        walker.isArray(node) -> {
                            walker.getArrayElements(node).filterIndexed { index, _ ->
                                GlobMatcher.matches(token.pattern, index.toString())
                            }
                        }

                        else -> emptyList()
                    }
                }
            }

            is PointerParser.Tokens.RecursiveDescent -> {
                throw UnsupportedOperationException("RecursiveDescent should be handled by handleRecursiveDescent")
            }
        }
    }

    /**
     * Collect all descendants of a node using depth-first search.
     * The result includes the node itself (to support zero-level matching).
     */
    private fun <N> collectAllDescendants(walker: KsonTreeWalker<N>, node: N): List<N> {
        val result = mutableListOf<N>()
        result.add(node)

        when {
            walker.isObject(node) -> {
                for (prop in walker.getObjectProperties(node)) {
                    result.addAll(collectAllDescendants(walker, prop.value))
                }
            }

            walker.isArray(node) -> {
                for (element in walker.getArrayElements(node)) {
                    result.addAll(collectAllDescendants(walker, element))
                }
            }
        }

        return result
    }
}

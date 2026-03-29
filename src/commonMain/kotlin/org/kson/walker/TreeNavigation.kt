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
 * Navigate a tree using a JSON Pointer.
 *
 * Follows each token in the pointer through the tree:
 * - For object nodes: matches token against property names
 * - For array nodes: matches token as an integer index
 *
 * @return The node at the end of the path, or null if navigation fails
 */
fun <N> KsonTreeWalker<N>.navigateWithJsonPointer(
    root: N,
    pointer: JsonPointer
): N? {
    // JSON Pointer always has only Literal tokens, so we expect exactly 0 or 1 result
    return TreeNavigator(this).navigateByParsedTokens(listOf(root), pointer.rawTokens).firstOrNull()
}

/**
 * Navigate a tree using a [JsonPointerGlob].
 *
 * Unlike [navigateWithJsonPointer], this returns ALL matching nodes, since wildcards
 * and patterns can match multiple keys/indices at each level.
 *
 * @return List of all nodes matching the pointer (empty list if no matches)
 */
@OptIn(ExperimentalJsonPointerGlobLanguage::class)
fun <N> KsonTreeWalker<N>.navigateWithJsonPointerGlob(
    root: N,
    pointer: JsonPointerGlob
): List<N> {
    return TreeNavigator(this).navigateByParsedTokens(listOf(root), pointer.rawTokens)
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
fun <N> KsonTreeWalker<N>.navigateToLocationWithPointer(
    root: N,
    targetLocation: Coordinates
): TreeNavigationResult<N>? {
    return TreeNavigator(this).navigateToLocation(root, targetLocation, JsonPointer.ROOT)
}

/**
 * Encapsulates tree navigation algorithms for a given [KsonTreeWalker].
 *
 * Constructed per-call by the public extension functions above, so that the
 * walker is captured once and all internal methods can use it directly.
 */
private class TreeNavigator<N>(private val walker: KsonTreeWalker<N>) {

    fun navigateToLocation(
        root: N,
        targetLocation: Coordinates,
        currentPointer: JsonPointer
    ): TreeNavigationResult<N>? {
        if (!Location.containsCoordinates(walker.getLocation(root), targetLocation)) {
            return null
        }

        when (val children = walker.getChildren(root)) {
            is NodeChildren.Object -> {
                for (prop in children.properties) {
                    val childResult = navigateToLocation(
                        prop.value, targetLocation,
                        JsonPointer.fromTokens(currentPointer.tokens + prop.name)
                    )
                    if (childResult != null) return childResult
                }
            }
            is NodeChildren.Array -> {
                for ((index, child) in children.elements.withIndex()) {
                    val childResult = navigateToLocation(
                        child, targetLocation,
                        JsonPointer.fromTokens(currentPointer.tokens + index.toString())
                    )
                    if (childResult != null) return childResult
                }
            }
            is NodeChildren.Leaf -> { /* no children to descend into */ }
        }

        return TreeNavigationResult(root, currentPointer)
    }

    /**
     * Navigate through a tree using parsed tokens ([JsonPointer]/[JsonPointerGlob] style).
     */
    fun navigateByParsedTokens(
        roots: List<N>,
        tokens: List<PointerParser.Tokens>
    ): List<N> {
        return navigateRecursive(roots, tokens, tokenIndex = 0)
    }

    private tailrec fun navigateRecursive(
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
            return handleRecursiveDescent(currentNodes, tokens, tokenIndex)
        }

        // All other tokens: process one step, then continue to next token
        val nextNodes = processSingleToken(currentNodes, token)
        return navigateRecursive(nextNodes, tokens, tokenIndex + 1)
    }

    private fun handleRecursiveDescent(
        currentNodes: List<N>,
        tokens: List<PointerParser.Tokens>,
        tokenIndex: Int
    ): List<N> {
        val remainingTokens = tokens.subList(tokenIndex + 1, tokens.size)
        return currentNodes.flatMap { node ->
            val descendants = collectAllDescendants(node)
            if (remainingTokens.isEmpty()) {
                // ** at end: for /**, include node; for /path/**, exclude it
                if (tokenIndex == 0) descendants else descendants.drop(1)
            } else {
                // ** in middle: try matching remaining tokens at all levels
                descendants.flatMap { candidate ->
                    navigateByParsedTokens(listOf(candidate), remainingTokens)
                }
            }
        }
    }

    private fun processSingleToken(
        currentNodes: List<N>,
        token: PointerParser.Tokens
    ): List<N> {
        return when (token) {
            is PointerParser.Tokens.Literal -> {
                currentNodes.mapNotNull { node ->
                    when (val children = walker.getChildren(node)) {
                        is NodeChildren.Object ->
                            children.properties.firstOrNull { it.name == token.value }?.value

                        is NodeChildren.Array -> {
                            val index = token.value.toIntOrNull()
                            if (index != null && index >= 0 && index < children.elements.size) {
                                children.elements[index]
                            } else {
                                null
                            }
                        }

                        is NodeChildren.Leaf -> null
                    }
                }
            }

            is PointerParser.Tokens.Wildcard -> {
                currentNodes.flatMap { node ->
                    when (val children = walker.getChildren(node)) {
                        is NodeChildren.Object -> children.properties.map { it.value }
                        is NodeChildren.Array -> children.elements
                        is NodeChildren.Leaf -> emptyList()
                    }
                }
            }

            is PointerParser.Tokens.GlobPattern -> {
                currentNodes.flatMap { node ->
                    when (val children = walker.getChildren(node)) {
                        is NodeChildren.Object -> {
                            children.properties
                                .filter { GlobMatcher.matches(token.pattern, it.name) }
                                .map { it.value }
                        }

                        is NodeChildren.Array -> {
                            children.elements.filterIndexed { index, _ ->
                                GlobMatcher.matches(token.pattern, index.toString())
                            }
                        }

                        is NodeChildren.Leaf -> emptyList()
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
    private fun collectAllDescendants(node: N): List<N> {
        val result = mutableListOf<N>()
        result.add(node)

        when (val children = walker.getChildren(node)) {
            is NodeChildren.Object -> {
                for (prop in children.properties) {
                    result.addAll(collectAllDescendants(prop.value))
                }
            }

            is NodeChildren.Array -> {
                for (element in children.elements) {
                    result.addAll(collectAllDescendants(element))
                }
            }

            is NodeChildren.Leaf -> { /* nothing */ }
        }

        return result
    }
}

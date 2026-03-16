package org.kson.walker

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.value.navigation.json_pointer.JsonPointer

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
        var current = root
        for (token in pointer.tokens) {
            current = when {
                walker.isObject(current) ->
                    walker.getObjectProperties(current)
                        .firstOrNull { it.first == token }?.second
                        ?: return null

                walker.isArray(current) -> {
                    val index = token.toIntOrNull() ?: return null
                    val elements = walker.getArrayElements(current)
                    if (index in elements.indices) elements[index] else return null
                }

                else -> return null
            }
        }
        return current
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
            for ((key, child) in walker.getObjectProperties(root)) {
                val childResult = navigateToLocationWithPointer(
                    walker, child, targetLocation,
                    JsonPointer.fromTokens(currentPointer.tokens + key)
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
}

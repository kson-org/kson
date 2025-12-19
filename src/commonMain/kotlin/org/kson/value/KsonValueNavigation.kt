package org.kson.value

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.schema.JsonPointer

/**
 * Result of navigating to a location in a KSON document.
 * Contains both the target node and the path from root to that node.
 *
 * @param targetNode The most specific KsonValue at the target location
 * @param pointerFromRoot [JsonPointer] from root to target
 *
 * Example:
 * ```kotlin
 * // For document: { users: [{ name: "Alice" }] }
 * // At location in "Alice"
 * LocationNavigationResult(
 *     targetNode = KsonString("Alice"),
 *     pathFromRoot = listOf("users", "0", "name")
 * )
 * ```
 */
data class LocationNavigationResult(
    val targetNode: KsonValue,
    val pointerFromRoot: JsonPointer,
)

/**
 * Core utilities for navigating and traversing [KsonValue] structures.
 *
 * This provides the canonical implementation for tree operations, used by both:
 * - Schema resolution (SchemaIdLookup, RefValidator)
 * - IDE tooling (KsonNavigator, SchemaNavigator)
 *
 * All navigation methods handle the three cases of [KsonValue]:
 * - [KsonObject]: Navigate by property name
 * - [KsonList]: Navigate by array index
 * - Primitive types: Terminal nodes with no children
 */
object KsonValueNavigation {
    /**
     * Navigate through a [KsonValue] using a JSON Pointer.
     *
     * JSON Pointers provide a standard way to reference specific values within a JSON document.
     * See [org.kson.schema.JsonPointer] and RFC 6901 for details.
     *
     * @param root The root node to start navigation from
     * @param pointer The JSON Pointer to follow
     * @return The node at the end of the path, or null if navigation fails
     *
     * Example:
     * ```kotlin
     * val pointer = JsonPointer("/users/0/name")
     * val node = navigateWithJsonPointer(root, pointer)
     * ```
     */
    fun navigateWithJsonPointer(
        root: KsonValue,
        pointer: JsonPointer
    ): KsonValue? {
        return navigateByTokens(root, pointer.tokens)
    }

    /**
     * Navigate through a [KsonValue] using string tokens (JSON Pointer style).
     *
     * Each token represents one navigation step:
     * - For [KsonObject]: token is the property name
     * - For [KsonList]: token is the array index as a string (e.g., "0", "1", "2")
     *
     * @param root The root node to start navigation from
     * @param tokens The path segments to follow
     * @return The node at the end of the path, or null if navigation fails
     */
    private fun navigateByTokens(
        root: KsonValue,
        tokens: List<String>
    ): KsonValue? {
        if (tokens.isEmpty()) return root

        var node: KsonValue? = root
        for (token in tokens) {
            node = when (node) {
                is KsonObject -> node.propertyLookup[token]
                is KsonList -> {
                    val index = token.toIntOrNull()
                    if (index != null && index >= 0 && index < node.elements.size) {
                        node.elements[index]
                    } else {
                        null
                    }
                }
                else -> null
            }
            if (node == null) break
        }
        return node
    }

    /**
     * Find the most specific KsonValue at a location AND build the path to it.
     *
     * The function recursively descends the tree, checking if the target location
     * is within each node's bounds. It always returns the most specific (deepest/smallest)
     * node that contains the target location.
     *
     * @param root The root node to start navigation from
     * @param targetLocation The coordinates to find a node at
     * @param currentPointer The [JsonPointer] accumulated during recursion (used internally)
     * @return Result containing the target node and path, or null if not found
     *
     * Example:
     * ```kotlin
     * // Document: { users: [{ name: "Alice" }] }
     * // Target location: inside "Alice"
     * val result = navigateToLocationWithPath(root, Coordinates(0, 24))
     * // result.targetNode = KsonString("Alice")
     * // result.pathFromRoot = ["users", "0", "name"]
     * ```
     */
    fun navigateToLocationWithPointer(
        root: KsonValue,
        targetLocation: Coordinates,
        currentPointer: JsonPointer = JsonPointer.ROOT
    ): LocationNavigationResult? {
        // If target location is not within this node's bounds, return null
        if (!Location.containsCoordinates(root.location, targetLocation)) {
            return null
        }

        // Try to find a more specific child node that contains the target
        when (root) {
            is KsonObject -> {
                for ((key, property) in root.propertyMap) {
                    val childResult = navigateToLocationWithPointer(
                        property.propValue,
                        targetLocation,
                        JsonPointer.fromTokens(currentPointer.tokens + key)
                    )
                    if (childResult != null) {
                        return childResult  // Found a more specific child
                    }
                }
            }

            is KsonList -> {
                for ((index, element) in root.elements.withIndex()) {
                    val childResult = navigateToLocationWithPointer(
                        element,
                        targetLocation,
                        JsonPointer.fromTokens(currentPointer.tokens + index.toString())
                    )
                    if (childResult != null) {
                        return childResult  // Found a more specific child
                    }
                }
            }

            else -> {
                // Leaf node (primitive) - no children to check
            }
        }

        // No child was more specific, so this node is the target
        return LocationNavigationResult(root, currentPointer)
    }
}
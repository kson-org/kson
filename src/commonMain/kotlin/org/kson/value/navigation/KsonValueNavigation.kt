package org.kson.value.navigation

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.value.navigation.jsonPointer.JsonPointer
import org.kson.value.navigation.jsonPointer.JsonPointerGlob
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.value.navigation.jsonPointer.GlobMatcher
import org.kson.value.navigation.jsonPointer.PointerParser
import kotlin.collections.iterator

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
 * - [org.kson.value.KsonObject]: Navigate by property name
 * - [org.kson.value.KsonList]: Navigate by array index
 * - Primitive types: Terminal nodes with no children
 */
object KsonValueNavigation {
    /**
     * Navigate through a [KsonValue] using a JSON Pointer.
     *
     * JSON Pointers provide a standard way to reference specific values within a JSON document.
     * See [JsonPointer] and RFC 6901 for details.
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
        // JSON Pointer always has only Literal tokens, so we expect exactly 0 or 1 result
        return navigateByParsedTokens(root, pointer.rawTokens).firstOrNull()
    }

    /**
     * Navigate through a [KsonValue] using a JsonPointerGlob (with wildcard and pattern support).
     *
     * JsonPointerGlob extends JSON Pointer with:
     * - Wildcard tokens: `*` matches any single key or array index
     * - Glob patterns: tokens containing `*` or `?` match using glob-style patterns
     *
     * Unlike [navigateWithJsonPointer], this returns ALL matching nodes, since wildcards
     * and patterns can match multiple keys/indices at each level.
     *
     * @param root The root node to start navigation from
     * @param pointer The JsonPointerGlob to follow
     * @return List of all nodes matching the pointer (empty list if no matches)
     *
     * Example:
     * ```kotlin
     * // Match all user emails
     * val pointer1 = JsonPointerGlob("/users/\*\/email")
     * val emails = navigateWithJsonPointerGlob(root, pointer1)
     *
     * // Match roles for users with "admin" in their key
     * val pointer2 = JsonPointerGlob("/users/\*admin*\/role")
     * val adminRoles = navigateWithJsonPointerGlob(root, pointer2)
     * ```
     */
    fun navigateWithJsonPointerGlob(
        root: KsonValue,
        pointer: JsonPointerGlob
    ): List<KsonValue> {
        return navigateByParsedTokens(root, pointer.rawTokens)
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

    /**
     * Navigate through a [KsonValue] using parsed tokens (JSON Pointer/JsonPointerGlob style).
     *
     * Supports four token types:
     * - [PointerParser.Tokens.Literal]: Exact match
     * - [PointerParser.Tokens.Wildcard]: Matches all keys/indices
     * - [PointerParser.Tokens.RecursiveDescent]: Matches zero or more levels (depth-first search)
     * - [PointerParser.Tokens.GlobPattern]: Pattern matching with * and ?
     *
     * Each token represents one navigation step:
     * - For [org.kson.value.KsonObject]: token matches against property names
     * - For [org.kson.value.KsonList]: Literal/Wildcard matches indices, patterns match stringified indices
     *
     * @param root The root node to start navigation from
     * @param tokens The parsed path segments to follow
     * @return List of all nodes matching the path (might be empty if no matches found)
     */
    private fun navigateByParsedTokens(
        root: KsonValue,
        tokens: List<PointerParser.Tokens>
    ): List<KsonValue> {
        if (tokens.isEmpty()) return listOf(root)

        var currentNodes: List<KsonValue> = listOf(root)

        for ((tokenIndex, token) in tokens.withIndex()) {
            val nextNodes = mutableListOf<KsonValue>()

            for (node in currentNodes) {
                when (token) {
                    is PointerParser.Tokens.Literal -> {
                        // Exact match behavior
                        val child = when (node) {
                            is KsonObject -> node.propertyLookup[token.value]
                            is KsonList -> {
                                val index = token.value.toIntOrNull()
                                if (index != null && index >= 0 && index < node.elements.size) {
                                    node.elements[index]
                                } else {
                                    null
                                }
                            }
                            else -> null
                        }
                        if (child != null) nextNodes.add(child)
                    }

                    is PointerParser.Tokens.Wildcard -> {
                        // Match all children
                        when (node) {
                            is KsonObject -> nextNodes.addAll(node.propertyMap.values.map { it.propValue })
                            is KsonList -> nextNodes.addAll(node.elements)
                            else -> { /* primitives have no children */ }
                        }
                    }

                    is PointerParser.Tokens.RecursiveDescent -> {
                        // Recursive descent: match zero or more levels
                        val remainingTokens = tokens.subList(tokenIndex + 1, tokens.size)
                        val descendants = collectAllDescendants(node)

                        if (remainingTokens.isEmpty()) {
                            // ** at end: for /**, include node; for /path/**, exclude it
                            nextNodes.addAll(if (tokenIndex == 0) descendants else descendants.drop(1))
                        } else {
                            // ** in middle: try matching remaining tokens at all levels
                            descendants.forEach { candidate ->
                                nextNodes.addAll(navigateByParsedTokens(candidate, remainingTokens))
                            }
                        }

                        // RecursiveDescent already handled remaining tokens, so we're done
                        return nextNodes
                    }

                    is PointerParser.Tokens.GlobPattern -> {
                        // Pattern matching
                        when (node) {
                            is KsonObject -> {
                                for ((key, property) in node.propertyMap) {
                                    if (GlobMatcher.matches(token.pattern, key)) {
                                        nextNodes.add(property.propValue)
                                    }
                                }
                            }
                            is KsonList -> {
                                // For arrays, match pattern against stringified indices
                                for ((index, element) in node.elements.withIndex()) {
                                    if (GlobMatcher.matches(token.pattern, index.toString())) {
                                        nextNodes.add(element)
                                    }
                                }
                            }
                            else -> { /* primitives have no children */ }
                        }
                    }
                }
            }

            currentNodes = nextNodes
            if (currentNodes.isEmpty()) break
        }

        return currentNodes
    }

    /**
     * Collect all descendants of a node using depth-first search.
     * The result includes the node itself (to support zero-level matching).
     *
     * @param node The node to collect descendants from
     * @return List of the node itself plus all its descendants
     */
    private fun collectAllDescendants(node: KsonValue): List<KsonValue> {
        val result = mutableListOf<KsonValue>()
        result.add(node)  // Include current node for zero-level matching

        when (node) {
            is KsonObject -> {
                for (property in node.propertyMap.values) {
                    result.addAll(collectAllDescendants(property.propValue))
                }
            }
            is KsonList -> {
                for (element in node.elements) {
                    result.addAll(collectAllDescendants(element))
                }
            }
            else -> {
                // Primitives have no descendants
            }
        }

        return result
    }
}
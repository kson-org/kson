package org.kson.value

import org.kson.parser.Coordinates
import org.kson.parser.Location

/**
 * Result of navigating to a location in a KSON document.
 * Contains both the target node and the path from root to that node.
 *
 * @param targetNode The most specific KsonValue at the target location
 * @param pathFromRoot List of property names/array indices from root to target
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
    val pathFromRoot: List<String>
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
     * Generic tree traversal with visitor pattern.
     *
     * This is the canonical way to walk a [KsonValue] tree. The visitor is called
     * for every node in depth-first pre-order (parent before children).
     *
     * @param root The root node to start traversal from
     * @param visitor Callback invoked for each node with (node, parent, depth)
     *                - node: The current node being visited
     *                - parent: The parent node, or null if this is the root
     *                - depth: Distance from root (root = 0)
     *
     * Example:
     * ```kotlin
     * walkTree(root) { node, parent, depth ->
     *     println("${"  ".repeat(depth)}${node::class.simpleName}")
     * }
     * ```
     */
    fun walkTree(
        root: KsonValue,
        visitor: (node: KsonValue, parent: KsonValue?, depth: Int) -> Unit
    ) {
        walkTreeRecursive(root, null, 0, visitor)
    }

    private fun walkTreeRecursive(
        node: KsonValue,
        parent: KsonValue?,
        depth: Int,
        visitor: (KsonValue, KsonValue?, Int) -> Unit
    ) {
        visitor(node, parent, depth)

        when (node) {
            is KsonObject -> {
                node.propertyMap.values.forEach { property ->
                    walkTreeRecursive(property.propValue, node, depth + 1, visitor)
                }
            }
            is KsonList -> {
                node.elements.forEach { element ->
                    walkTreeRecursive(element, node, depth + 1, visitor)
                }
            }
            else -> {
                /* no-op, only KsonObject and KsonList have children */
            }
        }
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
     *
     * Example:
     * ```kotlin
     * // Navigate to myObj.users[0].name
     * val node = navigateByTokens(root, listOf("users", "0", "name"))
     * ```
     */
    fun navigateByTokens(
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
     * Find the parent node of a target within a tree.
     *
     * Uses reference equality (===) to identify the target node.
     *
     * @param root The root of the tree to search
     * @param target The node whose parent to find
     * @return The parent node, or null if target is the root or not found
     *
     * Example:
     * ```kotlin
     * val parent = findParent(root, someNestedNode)
     * ```
     */
    fun findParent(root: KsonValue, target: KsonValue): KsonValue? {
        if (root === target) return null

        var result: KsonValue? = null
        walkTree(root) { node, parent, _ ->
            if (node === target) {
                result = parent
            }
        }
        return result
    }

    /**
     * Find all nodes in the tree that match a predicate.
     *
     * @param root The root node to start searching from
     * @param predicate Function that returns true for nodes to include
     * @return List of all matching nodes
     *
     * Example:
     * ```kotlin
     * // Find all string values
     * val strings = findAll(root) { it is KsonString }
     *
     * // Find all objects with a specific property
     * val withId = findAll(root) {
     *     it is KsonObject && it.propertyLookup.containsKey("id")
     * }
     * ```
     */
    fun findAll(
        root: KsonValue,
        predicate: (KsonValue) -> Boolean
    ): List<KsonValue> {
        val results = mutableListOf<KsonValue>()
        walkTree(root) { node, _, _ ->
            if (predicate(node)) {
                results.add(node)
            }
        }
        return results
    }

    /**
     * Find the first node in the tree that matches a predicate.
     *
     * Traversal is depth-first, so this returns the first match in that order.
     *
     * @param root The root node to start searching from
     * @param predicate Function that returns true for the node to find
     * @return The first matching node, or null if no match found
     *
     * Example:
     * ```kotlin
     * // Find first object with "$ref" property
     * val refNode = findFirst(schema) {
     *     it is KsonObject && it.propertyLookup.containsKey("${'$'}ref")
     * }
     * ```
     */
    fun findFirst(
        root: KsonValue,
        predicate: (KsonValue) -> Boolean
    ): KsonValue? {
        var result: KsonValue? = null
        walkTree(root) { node, _, _ ->
            if (result == null && predicate(node)) {
                result = node
            }
        }
        return result
    }

    /**
     * Find the most specific KsonValue at a location AND build the path to it.
     *
     * This is more efficient than calling [findValueAtPosition] and then building
     * the path separately, as it performs both operations in a single tree traversal.
     *
     * The function recursively descends the tree, checking if the target location
     * is within each node's bounds. It always returns the most specific (deepest/smallest)
     * node that contains the target location.
     *
     * @param root The root node to start navigation from
     * @param targetLocation The coordinates to find a node at
     * @param currentPath The path accumulated during recursion (used internally)
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
    fun navigateToLocationWithPath(
        root: KsonValue,
        targetLocation: Coordinates,
        currentPath: List<String> = emptyList()
    ): LocationNavigationResult? {
        // If target location is not within this node's bounds, return null
        if (!Location.locationContainsCoordinates(root.location, targetLocation)) {
            return null
        }

        // Try to find a more specific child node that contains the target
        when (root) {
            is KsonObject -> {
                for ((key, property) in root.propertyMap) {
                    val childResult = navigateToLocationWithPath(
                        property.propValue,
                        targetLocation,
                        currentPath + key
                    )
                    if (childResult != null) {
                        return childResult  // Found a more specific child
                    }
                }
            }

            is KsonList -> {
                for ((index, element) in root.elements.withIndex()) {
                    val childResult = navigateToLocationWithPath(
                        element,
                        targetLocation,
                        currentPath + index.toString()
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
        return LocationNavigationResult(root, currentPath)
    }

    /**
     * Find the most specific (deepest/smallest) KsonValue at a given location.
     *
     * This searches the tree for the smallest node that contains the target location.
     * Useful for IDE features like hover, go-to-definition, etc.
     *
     * @param root The root node to start searching from
     * @param targetPosition The location to find a node at
     * @return The most specific node at that location, or null if not found
     *
     * Example:
     * ```kotlin
     * // Find the node at line 5, column 10
     * val location = Location(
     *     start = Coordinates(5, 10),
     *     end = Coordinates(5, 10),
     *     startOffset = 0,
     *     endOffset = 0
     * )
     * val node = findValueAtPosition(root, location)
     * ```
     */
    fun findValueAtPosition(
        root: KsonValue,
        targetPosition: Coordinates
    ): KsonValue? {
        var mostSpecific: KsonValue? = null
        val smallestSize = Int.MAX_VALUE

        walkTree(root) { node, _, _ ->
            if (Location.containsCoordinates(node.location, targetPosition)) {
                val size = calculateLocationSize(node.location)
                if (size < smallestSize) {
                    mostSpecific = node
                }
            }
        }

        return mostSpecific
    }

    /**
     * Calculate a size metric for a location (for finding smallest/most specific).
     */
    private fun calculateLocationSize(location: Location): Int {
        val lines = location.end.line - location.start.line
        val chars = location.end.column - location.start.column
        return lines * 100000 + chars
    }
}
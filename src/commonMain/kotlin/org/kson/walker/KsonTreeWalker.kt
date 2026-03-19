package org.kson.walker

import org.kson.parser.Location

/**
 * A property in a JSON-like tree: a named value.
 *
 * @param name The property key
 * @param value The property value node
 */
data class TreeProperty<N>(val name: String, val value: N)

/**
 * Abstraction for walking JSON-like tree structures.
 *
 * This interface decouples tree-navigation algorithms (JSON Pointer traversal,
 * location-based lookup, path building) from any specific tree representation.
 *
 * Generic algorithms that operate on any tree representation are provided as
 * extension functions in TreeNavigation.kt.
 *
 * Note: [org.kson.value.EmbedBlock] nodes are treated as leaves by the walker.
 * They are not objects or arrays from the walker's perspective, even though
 * [org.kson.value.EmbedBlock.asKsonObject] can convert them to an object
 * representation. This matches how embed blocks behave in JSON Pointer
 * navigation — they are opaque values, not containers to descend into.
 *
 * @param N The node type of the tree
 */
interface KsonTreeWalker<N> {
    fun isObject(node: N): Boolean
    fun isArray(node: N): Boolean

    /** Returns properties for an object node. Empty if not an object. */
    fun getObjectProperties(node: N): List<TreeProperty<N>>

    /**
     * Look up a single property by key. Implementations with O(1) map lookup
     * should override this; the default falls back to a linear scan of [getObjectProperties].
     */
    fun getObjectProperty(node: N, key: String): N? =
        getObjectProperties(node).firstOrNull { it.name == key }?.value

    /** Returns child elements for an array node. Empty if not an array. */
    fun getArrayElements(node: N): List<N>

    /** Returns the string value of a string node, or null if not a string. */
    fun getStringValue(node: N): String?

    /** Returns the source location (start/end coordinates) of a node. */
    fun getLocation(node: N): Location
}

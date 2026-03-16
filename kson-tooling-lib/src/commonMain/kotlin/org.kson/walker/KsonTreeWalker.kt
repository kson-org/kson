package org.kson.walker

import org.kson.parser.Location

/**
 * Abstraction for walking JSON-like tree structures.
 *
 * This interface decouples tree-navigation algorithms (JSON Pointer traversal,
 * location-based lookup, path building) from any specific tree representation.
 *
 * Generic algorithms that operate on any tree representation are provided by
 * [TreeNavigation].
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

    /** Returns (propertyName, valueNode) pairs for an object node. Empty if not an object. */
    fun getObjectProperties(node: N): List<Pair<String, N>>

    /** Returns child elements for an array node. Empty if not an array. */
    fun getArrayElements(node: N): List<N>

    /** Returns the string value of a string node, or null if not a string. */
    fun getStringValue(node: N): String?

    /** Returns the source location (start/end coordinates) of a node. */
    fun getLocation(node: N): Location
}

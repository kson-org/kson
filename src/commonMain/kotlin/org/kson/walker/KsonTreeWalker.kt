package org.kson.walker

import org.kson.parser.Location

/**
 * A property in a JSON-like tree: a named value.
 *
 * @param name The property key
 * @param value The property value node
 */
data class TreeProperty<N>(
    val name: String,
    val value: N,
)

/**
 * The children of a node in a KSON-like tree.
 *
 * @param N The node type of the tree
 */
sealed class NodeChildren<out N> {
    class Object<N>(
        val properties: List<TreeProperty<N>>,
    ) : NodeChildren<N>()

    class Array<N>(
        val elements: List<N>,
    ) : NodeChildren<N>()

    data object Leaf : NodeChildren<Nothing>()
}

/**
 * Abstraction for walking JSON-like tree structures.
 *
 * This interface decouples tree-navigation algorithms (JSON Pointer traversal,
 * location-based lookup, path building) from any specific tree representation.
 *
 * Generic algorithms that operate on any tree representation are provided as
 * extension functions.
 *
 * Note: [org.kson.value.EmbedBlock] nodes are treated as [NodeChildren.Leaf] by the walker.
 * They are not an object from the walker's perspective, even though
 * [org.kson.value.EmbedBlock.asKsonObject] can convert them to an object
 * representation. This matches how embed blocks behave in JSON Pointer
 * navigation — they are opaque values, not containers to descend into.
 *
 * @param N The node type of the tree
 */
interface KsonTreeWalker<N> {
    /** Returns the children of [node]: object properties, array elements, or [NodeChildren.Leaf]. */
    fun getChildren(node: N): NodeChildren<N>

    /** Returns the source location (start/end coordinates) of a node. */
    fun getLocation(node: N): Location
}

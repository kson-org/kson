package org.kson.walker

import org.kson.value.navigation.json_pointer.JsonPointer

/**
 * A [JsonPointer] into a tree of [N] nodes, resolvable only against that kind of tree.
 *
 * Trees of the same document can number a list differently: [AstNodeWalker] keeps an element in error
 * at its index, [org.kson.value.toKsonValueOrNull] drops it.  [N] records which numbering the pointer
 * follows, so resolving it against the wrong kind of tree does not compile.  A pointer written against
 * no tree, such as a `$ref` fragment, is tagged where it is constructed.
 */
data class TreePointer<N>(val pointer: JsonPointer) {

    /** The pointer to the child [token] names under this node */
    fun child(token: String): TreePointer<N> = TreePointer(JsonPointer.fromTokens(pointer.tokens + token))

    /** The pointer to the node that contains the one this names, or null for the root */
    fun parent(): TreePointer<N>? =
        if (pointer.tokens.isEmpty()) null else TreePointer(JsonPointer.fromTokens(pointer.tokens.dropLast(1)))
}

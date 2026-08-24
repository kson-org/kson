package org.kson.walker

import org.kson.ast.*
import org.kson.parser.Location

/**
 * [KsonTreeWalker] implementation for AST nodes ([AstNode]).
 *
 * Unlike [KsonValueWalker] (which requires a fully valid [org.kson.value.KsonValue] tree),
 * this walker operates directly on the parser's AST, which includes
 * [AstNodeError] nodes for syntactically broken parts of the document.
 * Error nodes are treated as leaves, so navigation algorithms stop
 * descending at them while the surrounding tree structure remains intact.
 *
 * A list element in error is such a leaf: it is addressed by its index, so it is
 * listed among its siblings to keep their indices as written.  A property in error
 * is dropped instead—it is addressed by name, and a broken property has none.
 *
 * Note [org.kson.value.toKsonValueOrNull] drops elements in error and so renumbers the
 * ones after them: an index built by walking here addresses this tree, not that one.
 *
 * This makes the walker suitable for IDE features (path building,
 * completions, hover) that need to work on partially-typed documents
 * where [org.kson.value.KsonValue] conversion would fail.
 */
object AstNodeWalker : KsonTreeWalker<AstNode> {

    override fun getChildren(node: AstNode): NodeChildren<AstNode> = when (node) {
        is ObjectNode -> NodeChildren.Object(node.properties.mapNotNull { prop ->
            val propImpl = prop as? ObjectPropertyNodeImpl ?: return@mapNotNull null
            val keyImpl = propImpl.key as? ObjectKeyNodeImpl ?: return@mapNotNull null
            val keyString = (keyImpl.key as? StringNodeImpl)?.processedStringContent ?: return@mapNotNull null
            TreeProperty(keyString, propImpl.value as AstNode)
        })
        is ListNode -> NodeChildren.Array(node.elements.map { elem ->
            if (elem is ListElementNodeImpl) elem.value as AstNode else elem
        })
        else -> NodeChildren.Leaf
    }

    override fun getLocation(node: AstNode): Location = node.location
}

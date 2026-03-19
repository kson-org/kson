package org.kson.walker

import org.kson.ast.*
import org.kson.parser.Location

/**
 * [KsonTreeWalker] implementation for AST nodes ([AstNode]).
 *
 * Unlike [KsonValueWalker] (which requires a fully valid [org.kson.value.KsonValue] tree),
 * this walker operates directly on the parser's AST, which includes
 * [AstNodeError] nodes for syntactically broken parts of the document.
 * Error nodes are treated as leaves — [isObject] and [isArray] return false,
 * so navigation algorithms stop descending at them while the surrounding
 * tree structure remains intact.
 *
 * This makes the walker suitable for IDE features (path building,
 * completions, hover) that need to work on partially-typed documents
 * where [org.kson.value.KsonValue] conversion would fail.
 */
object AstNodeWalker : KsonTreeWalker<AstNode> {

    override fun isObject(node: AstNode): Boolean = node is ObjectNode

    override fun isArray(node: AstNode): Boolean = node is ListNode

    override fun getObjectProperties(node: AstNode): List<Pair<String, AstNode>> {
        if (node !is ObjectNode) return emptyList()
        return node.properties.mapNotNull { prop ->
            val propImpl = prop as? ObjectPropertyNodeImpl ?: return@mapNotNull null
            val keyImpl = propImpl.key as? ObjectKeyNodeImpl ?: return@mapNotNull null
            val keyString = (keyImpl.key as? StringNodeImpl)?.processedStringContent ?: return@mapNotNull null
            keyString to propImpl.value as AstNode
        }
    }

    override fun getArrayElements(node: AstNode): List<AstNode> {
        if (node !is ListNode) return emptyList()
        return node.elements.mapNotNull { elem ->
            val elemImpl = elem as? ListElementNodeImpl ?: return@mapNotNull null
            elemImpl.value as AstNode
        }
    }

    override fun getStringValue(node: AstNode): String? {
        return (node as? StringNodeImpl)?.processedStringContent
    }

    override fun getLocation(node: AstNode): Location = node.location
}

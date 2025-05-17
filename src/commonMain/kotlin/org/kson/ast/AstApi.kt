package org.kson.ast

import org.kson.parser.NumberParser

/**
 * The [AstApi] classes provide a client-friendly API interface for fully valid [AstNode] trees that
 * exposes just the data properties of the represented Kson and can be traversed confidently without
 * any error- or null-checking
 */
sealed class AstApi

class KsonRootApi(val rootNode: AstApi) : AstApi()
abstract class ValueNodeApi : AstApi()
class ObjectNodeApi(val properties: List<ObjectPropertyNodeApi>) : ValueNodeApi()
class ListNodeApi(val elements: List<ListElementNodeApi>) : ValueNodeApi()
class ListElementNodeApi(val valueNodeApi: ValueNodeApi) : AstApi()
class ObjectPropertyNodeApi(val name: StringNodeApi, val value: ValueNodeApi) : ValueNodeApi()
class EmbedBlockNodeApi(val embedTag: String, val embedContent: String) : ValueNodeApi()
class StringNodeApi(val value: String) : ValueNodeApi()
class NumberNodeApi(val value: NumberParser.ParsedNumber) : ValueNodeApi()
class BooleanNodeApi(val value: Boolean) : ValueNodeApi()
class NullNodeApi : ValueNodeApi()

fun AstNode.toAstApi(): AstApi {
    if (this !is AstNodeImpl) {
        /**
         * Must have a fully valid [AstNodeImpl] to create an [AstApi] for it
         */
        throw RuntimeException("Cannot create ${AstApi::class.simpleName} Node from a ${this::class.simpleName}")
    }
    return when (this) {
        is KsonRootImpl -> KsonRootApi(rootNode.toAstApi())
        is ObjectNode -> ObjectNodeApi(properties.map { it.toAstApi() as ObjectPropertyNodeApi })
        is ListNode -> ListNodeApi(elements.map { it.toAstApi() as ListElementNodeApi })
        is ListElementNodeImpl -> ListElementNodeApi(value.toAstApi() as ValueNodeApi)
        is ObjectPropertyNodeImpl -> ObjectPropertyNodeApi(
            name.toAstApi() as StringNodeApi,
            value.toAstApi() as ValueNodeApi)
        is EmbedBlockNode -> EmbedBlockNodeApi(embedTag, embedContent)
        is StringNodeImpl -> StringNodeApi(stringContent)
        is NumberNode -> NumberNodeApi(value)
        is TrueNode -> BooleanNodeApi(true)
        is FalseNode -> BooleanNodeApi(false)
        is NullNode -> NullNodeApi()
        is ValueNodeImpl -> this.toAstApi() as ValueNodeApi
        is AstNodeError -> throw RuntimeException("Cannot create Valid Ast Node from ${this::class.simpleName}")
    }
}

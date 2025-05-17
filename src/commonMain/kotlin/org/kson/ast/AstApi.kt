package org.kson.ast

import org.kson.parser.Location
import org.kson.parser.NumberParser

/**
 * The [AstApi] classes provide a client-friendly API interface for fully valid [AstNode] trees that
 * exposes just the data properties of the represented Kson and can be traversed confidently without
 * any error- or null-checking
 */
sealed class AstApi(val location: Location)

class KsonRootApi(val rootNode: AstApi, location: Location) : AstApi(location)
abstract class ValueNodeApi(location: Location) : AstApi(location)
class ObjectNodeApi(val properties: List<ObjectPropertyNodeApi>, location: Location) : ValueNodeApi(location)
class ListNodeApi(val elements: List<ListElementNodeApi>, location: Location) : ValueNodeApi(location)
class ListElementNodeApi(val valueNodeApi: ValueNodeApi, location: Location) : AstApi(location)
class ObjectPropertyNodeApi(val name: StringNodeApi,
                            val value: ValueNodeApi,
                            location: Location) : ValueNodeApi(location)
class EmbedBlockNodeApi(val embedTag: String,
                        val embedContent: String,
                        location: Location) : ValueNodeApi(location)
class StringNodeApi(val value: String, location: Location) : ValueNodeApi(location)
class NumberNodeApi(val value: NumberParser.ParsedNumber, location: Location) : ValueNodeApi(location)
class BooleanNodeApi(val value: Boolean, location: Location) : ValueNodeApi(location)
class NullNodeApi(location: Location) : ValueNodeApi(location)

fun AstNode.toAstApi(): AstApi {
    if (this !is AstNodeImpl) {
        /**
         * Must have a fully valid [AstNodeImpl] to create an [AstApi] for it
         */
        throw RuntimeException("Cannot create ${AstApi::class.simpleName} Node from a ${this::class.simpleName}")
    }
    return when (this) {
        is KsonRootImpl -> KsonRootApi(rootNode.toAstApi(), location)
        is ObjectNode -> ObjectNodeApi(properties.map { it.toAstApi() as ObjectPropertyNodeApi }, location)
        is ListNode -> ListNodeApi(elements.map { it.toAstApi() as ListElementNodeApi }, location)
        is ListElementNodeImpl -> ListElementNodeApi(value.toAstApi() as ValueNodeApi, location)
        is ObjectPropertyNodeImpl -> ObjectPropertyNodeApi(
            name.toAstApi() as StringNodeApi,
            value.toAstApi() as ValueNodeApi,
            location)
        is EmbedBlockNode -> EmbedBlockNodeApi(embedTag, embedContent, location)
        is StringNodeImpl -> StringNodeApi(stringContent, location)
        is NumberNode -> NumberNodeApi(value, location)
        is TrueNode -> BooleanNodeApi(true, location)
        is FalseNode -> BooleanNodeApi(false, location)
        is NullNode -> NullNodeApi(location)
        is ValueNodeImpl -> this.toAstApi() as ValueNodeApi
        is AstNodeError -> throw RuntimeException("Cannot create Valid Ast Node from ${this::class.simpleName}")
    }
}

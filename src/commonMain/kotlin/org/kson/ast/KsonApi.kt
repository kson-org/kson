package org.kson.ast

import org.kson.parser.Location
import org.kson.parser.NumberParser

/**
 * The [KsonApi] classes provide a client-friendly API interface for fully valid Kson [AstNode] trees that
 * exposes just the data properties of the represented Kson and can be traversed confidently without
 * any error- or null-checking
 */
sealed class KsonApi(val location: Location)

abstract class KsonValue(location: Location) : KsonApi(location)

class KsonObject(val properties: List<KsonObjectProperty>, location: Location) : KsonValue(location)
class KsonList(val elements: List<KsonListElement>, location: Location) : KsonValue(location)
class KsonListElement(val valueNodeApi: KsonValue, location: Location) : KsonApi(location)
class KsonObjectProperty(val name: KsonString,
                         val value: KsonValue,
                         location: Location) :KsonApi(location)
class EmbedBlock(val embedTag: String,
                 val embedContent: String,
                 location: Location) : KsonValue(location)
class KsonString(val value: String, location: Location) : KsonValue(location)
class KsonNumber(val value: NumberParser.ParsedNumber, location: Location) : KsonValue(location)
class KsonBoolean(val value: Boolean, location: Location) : KsonValue(location)
class KsonNull(location: Location) : KsonValue(location)

fun AstNode.toKsonApi(): KsonApi {
    if (this !is AstNodeImpl) {
        /**
         * Must have a fully valid [AstNodeImpl] to create an [KsonApi] for it
         */
        throw RuntimeException("Cannot create ${KsonApi::class.simpleName} Node from a ${this::class.simpleName}")
    }
    return when (this) {
        is KsonRootImpl -> rootNode.toKsonApi()
        is ObjectNode -> KsonObject(properties.map { it.toKsonApi() as KsonObjectProperty }, location)
        is ListNode -> KsonList(elements.map { it.toKsonApi() as KsonListElement }, location)
        is ListElementNodeImpl -> KsonListElement(value.toKsonApi() as KsonValue, location)
        is ObjectPropertyNodeImpl -> KsonObjectProperty(
            name.toKsonApi() as KsonString,
            value.toKsonApi() as KsonValue,
            location)
        is EmbedBlockNode -> EmbedBlock(embedTag, embedContent, location)
        is StringNodeImpl -> KsonString(stringContent, location)
        is NumberNode -> KsonNumber(value, location)
        is TrueNode -> KsonBoolean(true, location)
        is FalseNode -> KsonBoolean(false, location)
        is NullNode -> KsonNull(location)
        is KsonValueNodeImpl -> this.toKsonApi() as KsonValue
        is AstNodeError -> throw RuntimeException("Cannot create Valid Ast Node from ${this::class.simpleName}")
    }
}

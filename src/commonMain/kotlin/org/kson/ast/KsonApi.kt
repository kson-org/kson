package org.kson.ast

import org.kson.parser.Location
import org.kson.parser.NumberParser
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The [KsonApi] classes provide a client-friendly API interface for fully valid Kson [AstNode] trees that
 * exposes just the data properties of the represented Kson and can be traversed confidently without
 * any error- or null-checking
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
sealed class KsonApi(val location: Location)

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class KsonValue(location: Location) : KsonApi(location) {
    /**
     * Ensure all our [KsonValue] classes implement their [equals] and [hashCode]
     */
    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}

class KsonObject(val propertyList: List<KsonObjectProperty>, location: Location) : KsonValue(location) {
    val propertyMap: Map<String, KsonValue> by lazy {
        propertyList.associate { it.name.value to it.ksonValue }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KsonObject) return false
        
        if (propertyMap.size != other.propertyMap.size) return false
        
        return propertyMap.all { (key, value) ->
            other.propertyMap[key]?.let { value == it } ?: false
        }
    }

    override fun hashCode(): Int {
        return propertyMap.hashCode()
    }
}

class KsonList(val elements: List<KsonListElement>, location: Location) : KsonValue(location) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KsonList) return false
        
        if (elements.size != other.elements.size) return false
        
        return elements.zip(other.elements).all { (a, b) ->
            a.ksonValue == b.ksonValue
        }
    }

    override fun hashCode(): Int {
        return elements.map { it.ksonValue.hashCode() }.hashCode()
    }
}

class KsonListElement(val ksonValue: KsonValue, location: Location) : KsonApi(location)
class KsonObjectProperty(val name: KsonString,
                         val ksonValue: KsonValue,
                         location: Location) :KsonApi(location)
class EmbedBlock(val embedTag: String,
                 val embedContent: String,
                 location: Location) : KsonValue(location) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbedBlock) return false
        
        return embedTag == other.embedTag && embedContent == other.embedContent
    }

    override fun hashCode(): Int {
        return 31 * embedTag.hashCode() + embedContent.hashCode()
    }
}

class KsonString(val value: String, location: Location) : KsonValue(location) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KsonString) return false
        
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

class KsonNumber(val value: NumberParser.ParsedNumber, location: Location) : KsonValue(location) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KsonNumber) return false
        
        // Numbers are equal if their numeric values are equal (supporting cross-type comparison)
        val thisValue = when (value) {
            is NumberParser.ParsedNumber.Integer -> value.value.toDouble()
            is NumberParser.ParsedNumber.Decimal -> value.value
        }
        val otherValue = when (other.value) {
            is NumberParser.ParsedNumber.Integer -> other.value.value.toDouble()
            is NumberParser.ParsedNumber.Decimal -> other.value.value
        }
        
        return thisValue == otherValue
    }

    override fun hashCode(): Int {
        // Use the double value for consistent hashing across integer/decimal representations
        val doubleValue = when (value) {
            is NumberParser.ParsedNumber.Integer -> value.value.toDouble()
            is NumberParser.ParsedNumber.Decimal -> value.value
        }
        return doubleValue.hashCode()
    }
}

class KsonBoolean(val value: Boolean, location: Location) : KsonValue(location) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KsonBoolean) return false
        
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

class KsonNull(location: Location) : KsonValue(location) {
    override fun equals(other: Any?): Boolean {
        return other is KsonNull
    }

    override fun hashCode(): Int {
        return KsonNull::class.hashCode()
    }
}

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

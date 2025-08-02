package org.kson

import org.kson.ast.*
import org.kson.parser.Location
import org.kson.parser.NumberParser
import org.kson.stdlibx.exceptions.ShouldNotHappenException

/**
 * The [KsonValue] classes provide a client-friendly API interface for fully valid Kson [AstNode] trees that
 * exposes just the data properties of the represented Kson and can be traversed confidently without
 * any error- or null-checking
 */
sealed class KsonValue(val location: Location) {
    /**
     * Ensure all our [KsonValue] classes implement their [equals] and [hashCode]
     */
    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}

class KsonObject(val propertyMap: Map<KsonString, KsonValue>, location: Location) : KsonValue(location) {
    /**
     * Convenience lookup with the [KsonString] keys transformed to regular [String]s
     */
    val propertyLookup = propertyMap.mapKeys { it.key.value }
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

class KsonList(val elements: List<KsonValue>, location: Location) : KsonValue(location) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KsonList) return false
        
        if (elements.size != other.elements.size) return false
        
        return elements.zip(other.elements).all { (a, b) ->
            a == b
        }
    }

    override fun hashCode(): Int {
        return elements.map { it.hashCode() }.hashCode()
    }
}

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

fun AstNode.toKsonValue(): KsonValue {
    if (this !is AstNodeImpl) {
        /**
         * Must have a fully valid [AstNodeImpl] to create an [KsonValue] for it
         */
        throw RuntimeException("Cannot create ${KsonValue::class.simpleName} Node from a ${this::class.simpleName}")
    }
    return when (this) {
        is KsonRootImpl -> rootNode.toKsonValue()
        is ObjectNode -> {
            KsonObject(properties.associate { prop ->
                val propImpl = prop as? ObjectPropertyNodeImpl
                    ?: throw ShouldNotHappenException("this AST if fully valid")
                val propKey = propImpl.key as? ObjectKeyNodeImpl
                    ?: throw ShouldNotHappenException("this AST if fully valid")
                propKey.key.toKsonValue() as KsonString to propImpl.value.toKsonValue()
            },
                location)
        }
        is ListNode -> KsonList(elements.map { elem ->
            val listElementNode = elem as? ListElementNodeImpl
                ?: throw ShouldNotHappenException("this AST if fully valid")
            listElementNode.value.toKsonValue()

        }, location)
        is EmbedBlockNode -> EmbedBlock(embedTag, embedContent, location)
        is StringNodeImpl -> KsonString(processedStringContent, location)
        is NumberNode -> KsonNumber(value, location)
        is TrueNode -> KsonBoolean(true, location)
        is FalseNode -> KsonBoolean(false, location)
        is NullNode -> KsonNull(location)
        is KsonValueNodeImpl -> this.toKsonValue()
        is ObjectKeyNodeImpl -> {
            throw ShouldNotHappenException("these properties are processed above in the ${ObjectNode::class.simpleName} case")
        }
        is ObjectPropertyNodeImpl -> {
            throw ShouldNotHappenException("these properties are processed above in the ${ObjectNode::class.simpleName} case")
        }
        is ListElementNodeImpl -> {
            throw ShouldNotHappenException("these elements are processed above in the ${ListNode::class.simpleName} case")
        }
        is AstNodeError -> throw ShouldNotHappenException("Cannot create Valid Ast Node from ${this::class.simpleName}")
    }
}

package org.kson.value

import org.kson.ast.*
import org.kson.parser.Location
import org.kson.parser.NumberParser
import org.kson.parser.behavior.embedblock.EmbedObjectKeys
import org.kson.stdlibx.exceptions.ShouldNotHappenException

/**
 * The [KsonValue] classes provide a client-friendly API interface for fully valid Kson [AstNode] trees that
 * exposes just the data properties of the represented Kson and can be traversed confidently without
 * any error- or null-checking
 *
 * NOTE: [KsonValue] classes implement a "logical" equals/hashcode which compares just the values, not the
 *   [location] (which we consider metadata).  The ability to treat these [KsonValue]s as _values_ leads to
 *   more ergonomic code than having a strict equals that incorporates [location].
 */
sealed class KsonValue(protected val astNode: KsonValueNode) {

    val location: Location by lazy {
        astNode.location
    }

    /**
     * Ensure all our [KsonValue] classes implement their [equals] and [hashCode]
     * NOTE: this [equals] and [hashCode] must be logical equality of the underlying values, and
     *   no take [location] into account
     */
    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
}

data class KsonObjectProperty(val propName: KsonString, val propValue: KsonValue)

class KsonObject(
    /**
     * [propertyMap] indexes the [KsonObjectProperty]s in this [KsonObject] by the raw [String]s from
     * [KsonObjectProperty.propName].  This ensures that the full [KsonString] for the property key
     * is easily accessible so its [Location] may be used to locate the key in the original KSON
     * source.
     *
     * For a direct [String] key to [KsonValue] value lookup for this [KsonObject], so [propertyLookup]
     */
    val propertyMap: Map<String, KsonObjectProperty>, astNode: KsonValueNode) : KsonValue(astNode) {
    /**
     * Convenience lookup with the [String] keys pointing directly to the regular [KsonValue] values
     */
    val propertyLookup = propertyMap.mapValues { it.value.propValue }
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

class KsonList(val elements: List<KsonValue>, astNode: ListNode) : KsonValue(astNode) {
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

class EmbedBlock(embedBlockNode: EmbedBlockNode) : KsonValue(embedBlockNode) {
    private val embedTagNode = embedBlockNode.embedTagNode
    private val embedContentNode = embedBlockNode.embedContentNode

    val embedTag: KsonString? = embedTagNode?.let { KsonString(it) }
    val embedContent: KsonString = KsonString(embedContentNode)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbedBlock) return false
        
        return embedTag == other.embedTag && embedContent == other.embedContent
    }

    fun asKsonObject(): KsonObject {
        return KsonObject(
            buildMap {
                embedTagNode?.let {
                    val embedTagStringNode = object: UnquotedStringNode(embedTagNode.sourceTokens) {
                        override val stringContent = EmbedObjectKeys.EMBED_TAG.key
                        override val processedStringContent = EmbedObjectKeys.EMBED_TAG.key
                    }
                    put(EmbedObjectKeys.EMBED_TAG.key,
                        KsonObjectProperty(KsonString(embedTagStringNode),
                            KsonString(it)))
                }
                val embedContentStringNode = object: UnquotedStringNode(embedContentNode.sourceTokens) {
                    override val stringContent = EmbedObjectKeys.EMBED_CONTENT.key
                    override val processedStringContent = EmbedObjectKeys.EMBED_CONTENT.key
                }
                put(EmbedObjectKeys.EMBED_CONTENT.key,
                    KsonObjectProperty(KsonString(embedContentStringNode),
                        KsonString(embedContentNode)))
            },
            astNode
        )
    }

    override fun hashCode(): Int {
        return 31 * embedTag.hashCode() + 31 * embedContent.hashCode()
    }
}

class KsonString(private val stringNode: StringNodeImpl) : KsonValue(stringNode), SubParseable {
    val value: String by lazy {
        stringNode.processedStringContent
    }

    override fun subOffsetLocation(subStartOffset: Int, subEndOffset: Int): Location {
        return stringNode.contentTransformer.mapToOriginal(subStartOffset,subEndOffset)
    }

    override fun subCoordinatesLocation(subStartLine: Int, subStartColumn: Int, subEndLine: Int, subEndColumn: Int): Location {
        return stringNode.contentTransformer
            .mapToOriginal(subStartLine, subStartColumn, subEndLine, subEndColumn)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KsonString) return false
        
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

class KsonNumber(private val numberNode: NumberNode) : KsonValue(numberNode), SubParseable {
    val value = numberNode.value

    override fun subOffsetLocation(subStartOffset: Int, subEndOffset: Int): Location {
        return numberNode.contentTransformer.mapToOriginal(subStartOffset,subEndOffset)
    }

    override fun subCoordinatesLocation(subStartLine: Int, subStartColumn: Int, subEndLine: Int, subEndColumn: Int): Location {
        return numberNode.contentTransformer
            .mapToOriginal(subStartLine, subStartColumn, subEndLine, subStartColumn)
    }

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

class KsonBoolean(astNode: BooleanNode) : KsonValue(astNode) {
    val value = astNode.value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KsonBoolean) return false
        
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

class KsonNull(astNode: NullNode) : KsonValue(astNode) {
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
                    ?: throw ShouldNotHappenException("this AST is fully valid")
                val propKey = propImpl.key as? ObjectKeyNodeImpl
                    ?: throw ShouldNotHappenException("this AST is fully valid")
                val keyName = propKey.key.toKsonValue() as KsonString
                keyName.value to KsonObjectProperty(keyName, propImpl.value.toKsonValue())
            },
                this)
        }
        is ListNode -> KsonList(elements.map { elem ->
            val listElementNode = elem as? ListElementNodeImpl
                ?: throw ShouldNotHappenException("this AST is fully valid")
            listElementNode.value.toKsonValue()

        }, this)
        is EmbedBlockNode -> EmbedBlock(this)
        is StringNodeImpl -> KsonString(this)
        is NumberNode -> KsonNumber(this)
        is TrueNode -> KsonBoolean(this)
        is FalseNode -> KsonBoolean(this)
        is NullNode -> KsonNull(this)
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
        is AstNodeError -> throw UnsupportedOperationException("Cannot create Valid Ast Node from ${this::class.simpleName}")
    }
}

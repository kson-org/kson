package org.kson.ast

import org.kson.CompileTarget
import org.kson.CompileTarget.*
import org.kson.ast.AstNode.Indent
import org.kson.parser.Location
import org.kson.parser.behavior.embedblock.EmbedDelim
import org.kson.parser.NumberParser
import org.kson.parser.NumberParser.ParsedNumber
import org.kson.tools.IndentType
import org.kson.tools.FormattingStyle
import org.kson.parser.Parser
import org.kson.parser.behavior.StringQuote
import org.kson.parser.behavior.StringQuote.*
import org.kson.parser.behavior.StringUnquoted
import org.kson.parser.behavior.embedblock.EmbedObjectKeys

interface AstNode {
    /**
     * Public method for transforming the AST rooted at this node into the source of the given [compileTarget],
     * rendered with the given [indent]
     */
    fun toSource(indent: Indent, compileTarget: CompileTarget): String = toSourceWithNext(indent, null, compileTarget)

    /**
     * Internal method for recursive source generation calls must pass down context about the next node to be
     * rendered from this tree.
     *
     * This should only be called by other [AstNode] implementations.
     */
    fun toSourceWithNext(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String

    /**
     * The source location from which this [AstNode] was parsed
     */
    val location: Location

    /**
     * Abstract representation of the indentation to apply when serializing an AST as source code
     */
    data class Indent(
        /**
         * The [IndentType] to use when indenting output source
         */
        private val indentType: IndentType,
        /**
         * How deep to make this indent
         */
        private val indentLevel: Int = 0,
        /**
         * Whether or not this indent "hangs", i.e. only starts after the first newline of the text being indented
         */
        val hangingIndent: Boolean = false
    ) {
        /**
         * Constructs an initial/default indent
         */
        constructor() : this(IndentType.Space(2),0, false)

        private val indentString = indentType.indentString

        fun firstLineIndent(): String {
            return if (hangingIndent) {
                ""
            } else {
                return bodyLinesIndent()
            }
        }

        fun bodyLinesIndent(): String {
            return indentString.repeat(indentLevel)
        }

        /**
         * Produce a copy of this indent with the given [hanging] value for its [hanging]
         */
        fun clone(hanging: Boolean): Indent {
            return Indent(indentType, indentLevel, hanging)
        }

        /**
         * Produce the "next" indent in from this one, with the given [hanging] value for its [hanging]
         */
        fun next(hanging: Boolean): Indent {
            return Indent(indentType, indentLevel + 1, hanging)
        }
    }
}

/**
 * Base [AstNode] to be subclassed by all Kson AST Node classes
 */
sealed class AstNodeImpl(override val location: Location) : AstNode {
    /**
     * Transpiles this [AstNode] to the given [compileTarget] source, respecting the configuration in the given
     * [CompileTarget]
     */
    override fun toSourceWithNext(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return if (compileTarget.preserveComments && this is Documented && comments.isNotEmpty()) {
            // if we have comments, write them followed by the node content on the next line with an appropriate indent
            indent.firstLineIndent() + comments.joinToString("\n${indent.bodyLinesIndent()}") +
                    "\n" + toSourceInternal(indent.clone(false), nextNode, compileTarget)
        } else {
            // otherwise, just pass through to the node content
            toSourceInternal(indent, nextNode, compileTarget)
        }
    }

    /**
     * Subclasses must implement serialization of the AST subtree rooted at their node to a corresponding
     * source code snippet for [compileTarget], EXCLUDING comments (comment writing is handled "higher" up
     * in [toSourceWithNext]).
     *
     * This method is protected since it should never be called outside of [toSourceWithNext], which handles ensuring
     * comments are properly serialized for all nodes when appropriate.  So:
     *
     * DO NOT call this method---call [toSourceWithNext] instead.
     */
    protected abstract fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String
}

/**
 * Base class for the "shadow" versions of some of our [AstNode]s that we create to stitch into a partial
 * AST built out of some source with errors.
 *
 * All the subclasses of this use the same strategy of having an interface define the node type and providing
 * two implementations: the concrete `Impl` version for valid [AstNode]s and the "shadow" `Error` implementation
 * which patches the AST with an [AstNodeError] where an [AstNodeImpl] would otherwise go
 */
open class AstNodeError(private val invalidSource: String, location: Location) : AstNode, AstNodeImpl(location) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                invalidSource.split("\n")
                    .joinToString("\n") { line ->
                        indent.firstLineIndent() + line
                    }
            }
        }
    }
}

/**
 * Core Ast type for the values expressible in [Kson].  This type that maps to the `ksonValue` element of
 * the grammar documented on [Parser]
 */
interface KsonValueNode : AstNode
class KsonValueNodeError(content: String, location: Location) : KsonValueNode, AstNodeError(content, location)
abstract class KsonValueNodeImpl(location: Location) : KsonValueNode, AstNodeImpl(location)

/**
 * Any kson entity is either the [KsonRoot] of the document, an [ObjectPropertyNode]
 * on an object, or a [ListElementNode] in a list, and so semantically, those are the things
 * that make sense to document, so in our comment preservation strategy, these are the
 * [AstNode]s which accept comments.  This interface ties them together.
 */
interface Documented {
    val comments: List<String>
}

interface KsonRoot : AstNode
class KsonRootError(content: String, location: Location) : KsonRoot, AstNodeError(content, location)
class KsonRootImpl(
    val rootNode: KsonValueNode,
    private val trailingContent: List<KsonValueNode>,
    override val comments: List<String>,
    private val documentEndComments: List<String>,
    location: Location
) : KsonRoot, AstNodeImpl(location), Documented {

    /**
     * Produces valid [compileTarget] source code for the AST rooted at this [KsonRoot]
     */
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                var ksonDocument = rootNode.toSourceWithNext(indent, null, compileTarget)

                trailingContent.forEach {
                    if (ksonDocument.takeLast(2) != "\n\n") {
                        ksonDocument += "\n\n"
                    }
                    ksonDocument += it.toSourceWithNext(indent, null, compileTarget)
                }

                // remove any trailing newlines
                while(ksonDocument.endsWith("\n")) {
                    ksonDocument = ksonDocument.removeSuffix("\n")
                }

                if (compileTarget.preserveComments && documentEndComments.isNotEmpty()) {
                    val endComments = documentEndComments.joinToString("\n")
                    ksonDocument += if (ksonDocument.endsWith(endComments)) {
                        // endComments are already embedded in the document, likely as part of a trailing error
                        ""
                    } else {
                        if(compileTarget is Kson && compileTarget.formatConfig.formattingStyle == FormattingStyle.COMPACT){
                            "\n" + endComments
                        }else{
                            "\n\n" + endComments
                        }
                    }
                }

                ksonDocument
            }
        }
    }
}

class ObjectNode(val properties: List<ObjectPropertyNode>, location: Location) : KsonValueNodeImpl(location) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        if (properties.isEmpty()) {
            return "${indent.firstLineIndent()}{}"
        }

        return when (compileTarget) {
            is Kson -> {
                when (compileTarget.formatConfig.formattingStyle) {
                    FormattingStyle.DELIMITED -> formatDelimitedObject(indent, nextNode, compileTarget)
                    FormattingStyle.PLAIN -> formatUndelimitedObject(indent, nextNode, compileTarget)
                    FormattingStyle.COMPACT -> formatCompactObject(indent, nextNode, compileTarget)
                }
            }
            is Yaml -> formatUndelimitedObject(indent, nextNode, compileTarget)
            is Json -> formatDelimitedObject(indent, nextNode, compileTarget)
        }
    }

    private fun formatDelimitedObject(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
            val seperator = when(compileTarget) {
                is Kson -> "\n"
                is Json -> ",\n"
                is Yaml -> throw UnsupportedOperationException("We never format YAML objects as delimited")
            }

            return """
            |${indent.firstLineIndent()}{
            |${properties.withIndex().joinToString(seperator) { (index, property) ->
                val nodeAfterThisChild = properties.getOrNull(index + 1) ?: nextNode
                property.toSourceWithNext(indent.next(false), nodeAfterThisChild, compileTarget) }
            }
            |${indent.bodyLinesIndent()}}
            """.trimMargin()

    }

    private fun formatCompactObject(indent: Indent , nextNode: AstNode?, compileTarget: CompileTarget): String {
        val outputObject = properties.withIndex().joinToString(""){ (index, property) ->
            val nodeAfterThisChild = properties.getOrNull(index + 1) ?: nextNode
            val result = property.toSourceWithNext(indent, nodeAfterThisChild, compileTarget)

            // Only add space after this property if not using a space could result in ambiguity with the next node
            val needsSpace = index < properties.size - 1 &&
                    property is ObjectPropertyNodeImpl &&
                    result.last() != '\n' &&
                    when (property.value) {
                        is QuotedStringNode -> {
                            StringUnquoted.isUnquotable(property.value.stringContent)
                        }
                        is UnquotedStringNode,
                        is NumberNode,
                        is TrueNode,
                        is FalseNode,
                        is NullNode -> true
                        else -> false
                    }

            if (needsSpace) "$result " else result
        }
        return if (nextNode is ObjectPropertyNode) {
            // If the last property is a number we need to add whitespace before the '.' to prevent it becoming a number
            val needsSpace = (properties.last() as ObjectPropertyNodeImpl).value is NumberNode
            outputObject + if(needsSpace) " ." else "."
        } else outputObject
    }

    private fun formatUndelimitedObject(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val outputObject = properties.withIndex().joinToString("\n") { (index, property) ->
            val nodeAfterThisChild = properties.getOrNull(index + 1) ?: nextNode
            if (index == 0) {
                property.toSourceWithNext(indent, nodeAfterThisChild, compileTarget)
            } else {
                // ensure subsequent properties do not think they are hanging
                property.toSourceWithNext(indent.clone(false), nodeAfterThisChild, compileTarget)
            }
        }

        /**
         * Only need to explicitly end this object with a [org.kson.parser.TokenType.DOT] if the next
         * thing in this document is an [ObjectPropertyNode] that does not belong to this object
         */
        return if (compileTarget is Kson && nextNode is ObjectPropertyNode) {
            "$outputObject\n${indent.bodyLinesIndent()}."
        } else {
            // put a newline after multi-property objects
            outputObject + if (properties.size > 1) "\n" else ""
        }
    }
}

interface ObjectKeyNode : StringNode
class ObjectKeyNodeError(content: String, location: Location) : ObjectKeyNode, AstNodeError(content, location)
class ObjectKeyNodeImpl(
    val key: StringNode
) : ObjectKeyNode, AstNodeImpl(key.location) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val keyOutput = key.toSourceWithNext(indent, null, compileTarget)
        return "$keyOutput:"
    }
}

interface ObjectPropertyNode : AstNode
class ObjectPropertyNodeError(content: String, location: Location) : ObjectPropertyNode, AstNodeError(content, location)
class ObjectPropertyNodeImpl(
    val key: ObjectKeyNode,
    val value: KsonValueNode,
    override val comments: List<String>,
    location: Location
) :
    ObjectPropertyNode, AstNodeImpl(location), Documented {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                when (compileTarget.formatConfig.formattingStyle){
                    FormattingStyle.DELIMITED -> delimitedObjectProperty(indent, nextNode, compileTarget)
                    FormattingStyle.COMPACT  -> compactObjectProperty(indent, nextNode, compileTarget)
                    FormattingStyle.PLAIN -> undelimitedObjectProperty(indent, nextNode, compileTarget)
                }
            }
            is Yaml -> undelimitedObjectProperty(indent, nextNode, compileTarget)
            is Json -> delimitedObjectProperty(indent, nextNode, compileTarget)
        }
    }

    private fun delimitedObjectProperty(indent:Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val delimitedPropertyIndent = if (value is ListNode || value is ObjectNode ||
            // check if we're compiling an embed block to an object
            (compileTarget is Json && value is EmbedBlockNode && compileTarget.retainEmbedTags)) {
            // For delimited lists and objects, don't increase their indent here - they provide their own indent nest
            indent.clone(true)
        } else {
            // otherwise, increase the indent
            indent.next(true)
        }
        return key.toSourceWithNext(indent, value, compileTarget) + " " +
                    value.toSourceWithNext(delimitedPropertyIndent, nextNode, compileTarget)
    }

    private fun undelimitedObjectProperty(indent:Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return if (
            (value is ListNode && value.elements.isNotEmpty()) ||
            (value is ObjectNode && value.properties.isNotEmpty()) ||
            // check if we're compiling an embed block to an object
            (compileTarget is Yaml && value is EmbedBlockNode && compileTarget.retainEmbedTags)) {
            // For non-empty lists and objects, put the value on the next line
            key.toSourceWithNext(indent, value, compileTarget) + "\n" +
                    value.toSourceWithNext(indent.next(false), nextNode, compileTarget)
        } else {
            key.toSourceWithNext(indent, value, compileTarget) + " " +
                    value.toSourceWithNext(indent.next(true), nextNode, compileTarget)
        }
    }

    private fun compactObjectProperty(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        // A comment always needs to start on a new line. This can happen either when the first property or the next
        // node is commented.
        val firstPropertyHasComments = if (value !is ObjectNode) {
            false
        } else {
            when (val firstProperty = value.properties.firstOrNull()){
                is ObjectPropertyNodeImpl -> firstProperty.comments.isNotEmpty()
                null -> false
                else -> false
            }
        }
        val nextNodeHasComments = (nextNode is Documented && nextNode.comments.isNotEmpty())

        return key.toSourceWithNext(indent, value, compileTarget) +
                if (firstPropertyHasComments) {
                    "\n"
                } else {
                    ""
                } +
                value.toSourceWithNext(indent, nextNode, compileTarget) +
                if (nextNodeHasComments) "\n" else ""
    }
}

class ListNode(
    val elements: List<ListElementNode>,
    location: Location
) : KsonValueNodeImpl(location) {
    private sealed class ListDelimiters(val open: Char, val close: Char){
        data object AngleBrackets : ListDelimiters('<', '>')
        data object SquareBrackets: ListDelimiters('[', ']')
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val listDelimiter = when (compileTarget) {
                is Kson -> ListDelimiters.AngleBrackets
                is Yaml, is Json -> ListDelimiters.SquareBrackets
            }
        if (elements.isEmpty()) {
            return "${indent.firstLineIndent()}${listDelimiter.open}${listDelimiter.close}"
        }
        return when (compileTarget) {
            is Kson -> {
                when(compileTarget.formatConfig.formattingStyle) {
                    FormattingStyle.PLAIN -> {
                        val outputList = formatUndelimitedList(indent, nextNode, compileTarget)
                        /**
                         * Only need to explicitly end this list with a [org.kson.parser.TokenType.DOT] if the next
                         * thing in this document is a [ListElementNode] that does not belong to this list
                         */
                        if (nextNode is ListElementNode) {
                            "$outputList\n${indent.bodyLinesIndent()}="
                        } else {
                            outputList
                        }
                    }
                    FormattingStyle.DELIMITED -> formatDelimitedList(indent, nextNode, compileTarget, listDelimiter)
                    FormattingStyle.COMPACT -> formatCompactList(indent, nextNode, compileTarget, ListDelimiters.SquareBrackets)
                }
            }
            is Yaml -> formatUndelimitedList(indent, nextNode, compileTarget)
            is Json -> formatDelimitedList(indent, nextNode, compileTarget, listDelimiter)
        }
    }

    private fun formatDelimitedList(
        indent: Indent,
        nextNode: AstNode?,
        compileTarget: CompileTarget,
        listDelimiters: ListDelimiters
    ): String {
        val seperator = when (compileTarget) {
            is Kson -> "\n"
            is Json -> ",\n"
            else -> throw UnsupportedOperationException("We never format YAML objects as delimited")
        }

        // We pad our list bracket with newlines if our list is non-empty
        val bracketPadding = "\n"
        return indent.firstLineIndent() + listDelimiters.open + bracketPadding +
                elements.withIndex().joinToString(seperator) { (index, element) ->
                    val nodeAfterThisChild = elements.getOrNull(index + 1) ?: nextNode
                    element.toSourceWithNext(indent.next(false), nodeAfterThisChild, compileTarget)
                } +
                bracketPadding +
                indent.bodyLinesIndent() + listDelimiters.close
    }

    private fun formatCompactList(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget, listDelimiters: ListDelimiters): String {
        return elements.withIndex().joinToString(
            "",
            prefix = listDelimiters.open.toString(),
            postfix = listDelimiters.close.toString()
        ) { (index, element) ->
            val nodeAfterThisChild = elements.getOrNull(index + 1) ?: nextNode
            val elementString = if (element is Documented && element.comments.isNotEmpty()) {
                "\n"
            } else {
                ""
            } + element.toSourceWithNext(indent.clone(hanging = true), nodeAfterThisChild, compileTarget)


            val isNotLastElement = index < elements.size - 1
            
            // Extract current and next element values for type checking
            val currentValue = (element as? ListElementNodeImpl)?.value
            val currentIsObject = currentValue is ObjectNode
            val nextIsObject = (nodeAfterThisChild as? ListElementNodeImpl)?.value is ObjectNode

            // Determine formatting based on context
            when {
                // Both objects need a dot separator, with space if current ends with number
                (isNotLastElement && currentIsObject && nextIsObject) -> "{$elementString}"

                // Add space between elements in a list, except when the element is a list
                isNotLastElement && element is ListElementNodeImpl && element.value !is ListNode -> {
                    "$elementString "
                }

                else -> elementString
            }
        }
    }

    private fun formatUndelimitedList(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return elements.withIndex().joinToString("\n") { (index, element) ->
            val nodeAfterThisChild = elements.getOrNull(index + 1) ?: nextNode
            element.toSourceWithNext(indent, nodeAfterThisChild, compileTarget)
        }
    }
}

interface ListElementNode : AstNode
class ListElementNodeError(content: String, location: Location) : AstNodeError(content, location), ListElementNode
class ListElementNodeImpl(val value: KsonValueNode, override val comments: List<String>, location: Location)
    : ListElementNode, AstNodeImpl(location), Documented {

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                when (compileTarget.formatConfig.formattingStyle) {
                    FormattingStyle.PLAIN -> formatWithDash(indent, nextNode, compileTarget)
                    FormattingStyle.DELIMITED -> formatWithDash(indent, nextNode, compileTarget, isDelimited = true)
                    FormattingStyle.COMPACT -> value.toSourceWithNext(indent, nextNode, compileTarget)
                }
            }

            is Yaml -> formatWithDash(indent, nextNode, compileTarget)

            is Json -> value.toSourceWithNext(indent, nextNode, compileTarget)
        }
    }

    private fun formatWithDash(
        indent: Indent,
        nextNode: AstNode?,
        compileTarget: CompileTarget,
        isDelimited: Boolean = false
    ): String {
        return if ((value is ListNode && value.elements.isNotEmpty()) && !isDelimited) {
            indent.bodyLinesIndent() + "- \n" + value.toSourceWithNext(
                indent.next(false),
                nextNode,
                compileTarget
            )
        } else {
            indent.bodyLinesIndent() + "- " + value.toSourceWithNext(
                indent.next(true),
                nextNode,
                compileTarget
            )
        }
    }
}

interface StringNode : KsonValueNode
abstract class StringNodeImpl(location: Location) : StringNode, KsonValueNodeImpl(location) {
    abstract val stringContent: String

    val processedStringContent: String by lazy {
        unescapeStringContent(stringContent)
    }
}

/**
 * Note: [ksonEscapedStringContent] is expected to be the exact content of a [stringQuote]-delimited [Kson] string,
 *   including all escapes, but excluding the outer quotes.  A [Kson] string is escaped identically to a Json string,
 *   except that [Kson] allows raw whitespace to be embedded in strings
 */
open class QuotedStringNode(private val ksonEscapedStringContent: String,
                            private val stringQuote: StringQuote,
                            location: Location) : StringNodeImpl(location) {

    /**
     * An "unquoted" Kson string: i.e. a valid Kson string with all escapes intact except for quote escapes.
     * This string must be [SingleQuote]'ed or [DoubleQuote]'ed and then quote-escaped with [StringQuote.escapeQuotes]
     * to obtain a fully valid KsonString
     */
    private val unquotedString: String by lazy {
        stringQuote.unescapeQuotes(ksonEscapedStringContent)
    }

    override val stringContent: String by lazy {
        unquotedString
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                // Check if we can use this string unquoted
                val isSimple = StringUnquoted.isUnquotable(unquotedString)

                indent.firstLineIndent() +
                    if (isSimple) {
                        unquotedString
                    } else {
                        val singleQuoteCount = SingleQuote.countDelimiterOccurrences(unquotedString)
                        val doubleQuoteCount = DoubleQuote.countDelimiterOccurrences(unquotedString)

                        // prefer single-quotes unless double-quotes would require less escaping
                        val chosenDelimiter = if (doubleQuoteCount < singleQuoteCount) {
                            DoubleQuote
                        } else {
                            SingleQuote
                        }

                    val escapedContent = chosenDelimiter.escapeQuotes(unquotedString)
                    "${chosenDelimiter}$escapedContent${chosenDelimiter}"
                }
            }

            is Yaml -> {
                indent.firstLineIndent() + "\"" + DoubleQuote.escapeQuotes(unquotedString) + "\""
            }

            is Json -> {
                indent.firstLineIndent() + "\"${escapeRawWhitespace(DoubleQuote.escapeQuotes(unquotedString))}\""
            }
        }
    }
}

class UnquotedStringNode(override val stringContent: String, location: Location) : StringNodeImpl(location) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + stringContent
            }

            is Json -> {
                indent.firstLineIndent() + "\"${renderForJsonString(stringContent)}\""
            }
        }
    }
}

/**
 * Callers are in charge of ensuring that `stringValue` is parseable by [NumberParser]
 */
class NumberNode(stringValue: String, location: Location) : KsonValueNodeImpl(location) {
    val value: ParsedNumber by lazy {
        val parsedNumber = NumberParser(stringValue).parse()
        parsedNumber.number ?: throw IllegalStateException("Hitting this indicates a parser bug: unparseable " +
                "strings should be passed here but we got: " + stringValue)
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json-> {
                indent.firstLineIndent() + value.asString
            }
        }
    }
}

class TrueNode(location: Location) : KsonValueNodeImpl(location) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                indent.firstLineIndent() + "true"
            }
        }
    }
}

class FalseNode(location: Location) : KsonValueNodeImpl(location) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json-> {
                indent.firstLineIndent() + "false"
            }
        }
    }
}

class NullNode(location: Location) : KsonValueNodeImpl(location) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                indent.firstLineIndent() + "null"
            }
        }
    }
}

class EmbedBlockNode(
    val embedTagNode: StringNodeImpl?,
    val metadataTagNode: StringNodeImpl?,
    val embedContentNode: StringNodeImpl,
    embedDelim: EmbedDelim,
    location: Location
) :
    KsonValueNodeImpl(location) {

    private val embedTag: String = embedTagNode?.stringContent ?: ""
    private val metadataTag: String = metadataTagNode?.stringContent ?: ""
    private val embedContent: String by lazy {
        embedDelim.unescapeEmbedContent(embedContentNode.stringContent)
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                val percentCount = EmbedDelim.Percent.countDelimiterOccurrences(embedContent)
                val dollarCount = EmbedDelim.Dollar.countDelimiterOccurrences(embedContent)

                // Choose delimiter that requires least escaping
                val (delimiter, content) = when {
                    // The primary delimiter is not in the content, so we can use the default delimiter
                    // without any escaping needed
                    percentCount == 0 ->
                        EmbedDelim.Percent to embedContent
                    
                    // Otherwise, check if we can use the alternate delimiter without escaping
                    dollarCount == 0 ->
                        EmbedDelim.Dollar to EmbedDelim.Dollar.escapeEmbedContent(embedContent)
                    
                    // We'll choose the delimiter that requires less escaping
                    else -> {
                        val chosenDelimiter = if (dollarCount < percentCount) EmbedDelim.Dollar else EmbedDelim.Percent
                        chosenDelimiter to chosenDelimiter.escapeEmbedContent(embedContent)
                    }
                }

                val embedPreamble = embedTag + if(metadataTag.isNotEmpty()) ": $metadataTag" else ""
                when (compileTarget.formatConfig.formattingStyle){
                    FormattingStyle.PLAIN, FormattingStyle.DELIMITED -> {
                        // Format the embed block
                        indent.firstLineIndent() + delimiter.openDelimiter + embedPreamble + "\n" +
                                indent.bodyLinesIndent() + content.lines()
                            .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                                delimiter.closeDelimiter
                    }
                    FormattingStyle.COMPACT -> {
                        // Format the embed block
                        delimiter.openDelimiter + embedPreamble + "\n" +
                                content.lines()
                            .joinToString("\n") { it } +
                                delimiter.closeDelimiter
                    }
                }
            }

            is Yaml -> {
                if (!compileTarget.retainEmbedTags) {
                    renderMultilineYamlString(embedContent, indent, indent.next(false))
                } else {
                    encodeEmbedBlock(compileTarget, indent)
                }
            }
            is Json -> {
                if (!compileTarget.retainEmbedTags) {
                    indent.firstLineIndent() + "\"${renderForJsonString(embedContent)}\""
                } else {
                    encodeEmbedBlock(compileTarget, indent)
                }
            }
        }
    }

    /**
     * Encode the [EmbedBlockNode] to a Json or Yaml object with the [EmbedObjectKeys]
     */
    private fun encodeEmbedBlock(compileTarget: CompileTarget, indent: Indent): String {
        return when (compileTarget) {
            is Json -> {
                val nextIndent = indent.next(false)
                val embedTag = if (embedTag.isNotEmpty()) {
                    nextIndent.bodyLinesIndent() + "\"${EmbedObjectKeys.EMBED_TAG.key}\": \"$embedTag\"," + "\n"
                } else {
                    ""
                }
                val metadataTag = if (metadataTag.isNotEmpty()) {
                    nextIndent.bodyLinesIndent() + "\"${EmbedObjectKeys.EMBED_METADATA.key}\": \"$metadataTag\"," + "\n"
                } else {
                    ""
                }

                """
                |${indent.firstLineIndent()}{
                |$embedTag$metadataTag${nextIndent.bodyLinesIndent()}"${EmbedObjectKeys.EMBED_CONTENT.key}": "${
                    renderForJsonString(
                        embedContent
                    )
                }"
                |${indent.bodyLinesIndent()}}
                        """.trimMargin()

            }

            is Yaml -> {
                if (embedTag.isNotEmpty()) {
                    indent.firstLineIndent() + "${EmbedObjectKeys.EMBED_TAG.key}: \"" + embedTag + "\"\n"
                } else {
                    ""
                } +
                        if (metadataTag.isNotEmpty()) {
                            indent.firstLineIndent() + "${EmbedObjectKeys.EMBED_METADATA.key}: \"" + metadataTag + "\"\n"
                        } else {
                            ""
                        } +
                        indent.firstLineIndent() + "${EmbedObjectKeys.EMBED_CONTENT.key}: " +
                        renderMultilineYamlString(embedContent, indent.clone(true), indent.next(true))
            }
            is Kson -> throw UnsupportedOperationException("should not encode embed block as ${compileTarget::class.simpleName}")
        }

    }

    /**
     * Formats a string as a Yaml multiline string, preserving indentation
     *
     * @param content The string content to format
     * @param indent The base indentation level
     * @param contentIndent Additional indentation to apply to the content
     * @return a Yaml-formatted multiline string with any needed indentation markers
     */
    private fun renderMultilineYamlString(
        content: String,
        indent: Indent,
        contentIndent: Indent
    ): String {
        // Find minimum leading whitespace across non-empty lines
        val contentIndentSize = content.split("\n")
            .filter { it.isNotEmpty() }
            .minOfOrNull { line -> line.takeWhile { it.isWhitespace() }.length } ?: 0

        // The user's content has an indent we must maintain, so we must tell Yaml how much indent
        // we are giving it on our multiline string to ensure it does not eat up the content's indent too
        val indentSize = contentIndent.bodyLinesIndent().length
        val multilineLineIndicator = if (contentIndentSize > 0) "|$indentSize" else "|"

        return indent.firstLineIndent() + multilineLineIndicator + "\n" +
                content.split("\n")
                    .joinToString("\n") { line ->
                        contentIndent.bodyLinesIndent() + line
                    }
    }
}

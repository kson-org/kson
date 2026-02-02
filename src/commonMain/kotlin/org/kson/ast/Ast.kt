package org.kson.ast

import org.kson.CompileTarget
import org.kson.CompileTarget.*
import org.kson.Json
import org.kson.ast.AstNode.Indent
import org.kson.parser.Location
import org.kson.parser.behavior.embedblock.EmbedDelim
import org.kson.parser.NumberParser
import org.kson.parser.NumberParser.ParsedNumber
import org.kson.tools.IndentType
import org.kson.parser.Parser
import org.kson.parser.Token
import org.kson.parser.TokenType.COMMENT
import org.kson.parser.TokenType.WHITESPACE
import org.kson.parser.behavior.IdentityContentTransformer
import org.kson.parser.behavior.KsonContentTransformer
import org.kson.parser.behavior.StringQuote
import org.kson.parser.behavior.StringQuote.*
import org.kson.parser.behavior.StringUnquoted
import org.kson.parser.behavior.embedblock.EmbedContentTransformer
import org.kson.parser.behavior.embedblock.EmbedObjectKeys
import org.kson.parser.behavior.quotedstring.QuotedStringContentTransformer
import org.kson.stdlibx.exceptions.ShouldNotHappenException
import org.kson.tools.FormattingStyle.*

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
     * The sequence of tokens that defines this [AstNode] in the originally parsed source
     */
    val sourceTokens: List<Token>

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
        constructor() : this(IndentType.Space(2), 0, false)

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
sealed class AstNodeImpl(override val sourceTokens: List<Token>) : AstNode {
    override val location: Location by lazy {
        Location.merge(sourceTokens.first().lexeme.location, sourceTokens.last().lexeme.location)
    }

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
open class AstNodeError(sourceTokens: List<Token>) : AstNode, AstNodeImpl(sourceTokens) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                val invalidSource = sourceTokens.joinToString("") { it.lexeme.text }
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
class KsonValueNodeError(sourceTokens: List<Token>) : KsonValueNode, AstNodeError(sourceTokens)
abstract class KsonValueNodeImpl(sourceTokens: List<Token>) : KsonValueNode, AstNodeImpl(sourceTokens)

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
class KsonRootError(sourceTokens: List<Token>) : KsonRoot, AstNodeError(sourceTokens)
class KsonRootImpl(
    val rootNode: KsonValueNode,
    private val trailingContent: List<KsonValueNode>,
    override val comments: List<String>,
    private val documentEndComments: List<String>,
    sourceTokens: List<Token>
) : KsonRoot, AstNodeImpl(sourceTokens), Documented {

    /**
     * Produces valid [compileTarget] source code for the AST rooted at this [KsonRoot]
     */
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                var ksonDocument = rootNode.toSourceWithNext(indent, trailingContent.firstOrNull(), compileTarget)

                trailingContent.forEachIndexed { index, trailingValue ->
                    if (ksonDocument.takeLast(2) != "\n\n") {
                        ksonDocument += "\n\n"
                    }
                    ksonDocument += trailingValue.toSourceWithNext(
                        indent,
                        trailingContent.getOrNull(index + 1),
                        compileTarget
                    )
                }

                // remove any trailing newlines
                while (ksonDocument.endsWith("\n")) {
                    ksonDocument = ksonDocument.removeSuffix("\n")
                }

                if (compileTarget.preserveComments && documentEndComments.isNotEmpty()) {
                    val endComments = documentEndComments.joinToString("\n")
                    ksonDocument += if (ksonDocument.endsWith(endComments)) {
                        // endComments are already embedded in the document, likely as part of a trailing error
                        ""
                    } else {
                        if (compileTarget is Kson && compileTarget.formatConfig.formattingStyle == COMPACT) {
                            "\n" + endComments
                        } else {
                            "\n\n" + endComments
                        }
                    }
                }

                ksonDocument
            }
        }
    }
}

/**
 *  This check is used to determine if the document contains trailing content.
 *  This is the case if the node after an [ObjectNode] or [ListNode] is a [KsonValueNode] (not an element
 *  adding to an object or list).
 *  It must be erroneous "trailing" content, so we can preserve our [org.kson.parser.TokenType.END_DASH] or
 *  [org.kson.parser.TokenType.DOT] to be safe (that trailing content may be a malformed
 *  object we don't want to merge with).
 */
private fun isTrailingContent(nextNode: AstNode?): Boolean {
    return nextNode is KsonValueNode
}

class ObjectNode(val properties: List<ObjectPropertyNode>, sourceTokens: List<Token>) : KsonValueNodeImpl(sourceTokens) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        if (properties.isEmpty()) {
            return "${indent.firstLineIndent()}{}"
        }

        return when (compileTarget) {
            is Kson -> {
                when (compileTarget.formatConfig.formattingStyle) {
                    DELIMITED -> formatDelimitedObject(indent, nextNode, compileTarget)
                    PLAIN -> formatUndelimitedObject(indent, nextNode, compileTarget)
                    COMPACT -> formatCompactObject(indent, nextNode, compileTarget)
                    CLASSIC -> formatDelimitedObject(indent, nextNode, compileTarget)
                }
            }

            is Yaml -> formatUndelimitedObject(indent, nextNode, compileTarget)
        }
    }

    private fun formatDelimitedObject(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val separator = when (compileTarget) {
            is Kson -> {
                if (compileTarget.formatConfig.formattingStyle == CLASSIC) {
                    ",\n"
                } else {
                    "\n"
                }
            }
            is Yaml -> throw UnsupportedOperationException("We never format YAML objects as delimited")
        }

        return """
            |${indent.firstLineIndent()}{
            |${
            properties.withIndex().joinToString(separator) { (index, property) ->
                val nodeAfterThisChild = properties.getOrNull(index + 1) ?: nextNode
                property.toSourceWithNext(indent.next(false), nodeAfterThisChild, compileTarget)
            }
        }
            |${indent.bodyLinesIndent()}}
            """.trimMargin()

    }

    private fun formatCompactObject(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val outputObject = properties.withIndex().joinToString("") { (index, property) ->
            val nodeAfterThisChild = properties.getOrNull(index + 1) ?: nextNode
            val result = property.toSourceWithNext(indent, nodeAfterThisChild, compileTarget)

            // Only add space after this property if not using a space could result in ambiguity with the next node
            val needsSpace = index < properties.size - 1 &&
                    property is ObjectPropertyNodeImpl &&
                    result.last() != '\n' &&
                    when (property.value) {
                        is QuotedStringNode -> {
                            StringUnquoted.isUnquotable(property.value.rawStringContent)
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
        return if (needsEndDot(nextNode)) {
            // If the last property is a number we need to add whitespace before the '.' to prevent it becoming a number
            val needsSpace = (properties.last() as ObjectPropertyNodeImpl).value is NumberNode
            outputObject + if (needsSpace) " ." else "."
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

        return if (compileTarget is Kson && needsEndDot(nextNode)) {
            "$outputObject\n${indent.bodyLinesIndent()}."
        } else {
            // put a newline after multi-property objects
            outputObject + if (properties.size > 1) "\n" else ""
        }
    }

    /**
     * Only need to explicitly end this object with a [org.kson.parser.TokenType.DOT] if the next
     * node in this document is an [ObjectPropertyNode] that does not belong to this object,
     * or if there is any trailing content (which means we have erroneous content after the main document)
     */
    private fun needsEndDot(nextNode: AstNode?): Boolean {
        return nextNode is ObjectPropertyNode || isTrailingContent(nextNode)
    }
}

interface ObjectKeyNode : StringNode
class ObjectKeyNodeError(sourceTokens: List<Token>) : ObjectKeyNode, AstNodeError(sourceTokens)
class ObjectKeyNodeImpl(
    val key: StringNode
) : ObjectKeyNode, AstNodeImpl(key.sourceTokens) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val keyOutput = key.toSourceWithNext(indent, null, compileTarget)
        return "$keyOutput:"
    }
}

interface ObjectPropertyNode : AstNode
class ObjectPropertyNodeError(sourceTokens: List<Token>) : ObjectPropertyNode, AstNodeError(sourceTokens)
class ObjectPropertyNodeImpl(
    val key: ObjectKeyNode,
    val value: KsonValueNode,
    override val comments: List<String>,
    sourceTokens: List<Token>
) :
    ObjectPropertyNode, AstNodeImpl(sourceTokens), Documented {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                when (compileTarget.formatConfig.formattingStyle) {
                    DELIMITED -> delimitedObjectProperty(indent, nextNode, compileTarget)
                    COMPACT -> compactObjectProperty(indent, nextNode, compileTarget)
                    PLAIN -> undelimitedObjectProperty(indent, nextNode, compileTarget)
                    CLASSIC -> delimitedObjectProperty(indent, nextNode, compileTarget)
                }
            }

            is Yaml -> undelimitedObjectProperty(indent, nextNode, compileTarget)
        }
    }

    private fun delimitedObjectProperty(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val delimitedPropertyIndent = if (value is ListNode || value is ObjectNode ||
            // check if we're compiling an embed block to an object
            (compileTarget is Json && value is EmbedBlockNode && compileTarget.retainEmbedTags)
        ) {
            // For delimited lists and objects, don't increase their indent here - they provide their own indent nest
            indent.clone(true)
        } else {
            // otherwise, increase the indent
            indent.next(true)
        }
        return key.toSourceWithNext(indent, value, compileTarget) + " " +
                value.toSourceWithNext(delimitedPropertyIndent, nextNode, compileTarget)
    }

    private fun undelimitedObjectProperty(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return if (
            (value is ListNode && value.elements.isNotEmpty()) ||
            (value is ObjectNode && value.properties.isNotEmpty()) ||
            // check if we're compiling an embed block to an object
            (compileTarget is Yaml && value is EmbedBlockNode && compileTarget.retainEmbedTags)
        ) {
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
            when (val firstProperty = value.properties.firstOrNull()) {
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
    sourceTokens: List<Token>
) : KsonValueNodeImpl(sourceTokens) {
    private sealed class ListDelimiters(val open: Char, val close: Char) {
        data object AngleBrackets : ListDelimiters('<', '>')
        data object SquareBrackets : ListDelimiters('[', ']')
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        val listDelimiter = when (compileTarget) {
            is Kson -> {
                if (compileTarget.formatConfig.formattingStyle == CLASSIC) {
                    ListDelimiters.SquareBrackets
                } else {
                    ListDelimiters.AngleBrackets
                }
            }
            is Yaml-> ListDelimiters.SquareBrackets
        }
        if (elements.isEmpty()) {
            return "${indent.firstLineIndent()}${listDelimiter.open}${listDelimiter.close}"
        }
        return when (compileTarget) {
            is Kson -> {
                when (compileTarget.formatConfig.formattingStyle) {
                    PLAIN -> {
                        val outputList = formatUndelimitedList(indent, nextNode, compileTarget)

                        if (needsEndDash(nextNode)) {
                            "$outputList\n${indent.bodyLinesIndent()}="
                        } else {
                            outputList
                        }
                    }

                    DELIMITED -> formatDelimitedList(indent, nextNode, compileTarget, listDelimiter)
                    COMPACT -> formatCompactList(
                        indent,
                        nextNode,
                        compileTarget,
                        ListDelimiters.SquareBrackets
                    )
                    CLASSIC -> formatDelimitedList(indent, nextNode, compileTarget, listDelimiter)
                }
            }

            is Yaml -> formatUndelimitedList(indent, nextNode, compileTarget)
        }
    }

    private fun formatDelimitedList(
        indent: Indent,
        nextNode: AstNode?,
        compileTarget: CompileTarget,
        listDelimiters: ListDelimiters
    ): String {
        val separator = when (compileTarget) {
            is Kson -> if(compileTarget.formatConfig.formattingStyle == CLASSIC) {
                ",\n"
            }else{
                "\n"
            }
            else -> throw UnsupportedOperationException("We never format YAML objects as delimited")
        }

        // We pad our list bracket with newlines if our list is non-empty
        val bracketPadding = "\n"
        return indent.firstLineIndent() + listDelimiters.open + bracketPadding +
                elements.withIndex().joinToString(separator) { (index, element) ->
                    val nodeAfterThisChild = elements.getOrNull(index + 1) ?: nextNode
                    element.toSourceWithNext(indent.next(false), nodeAfterThisChild, compileTarget)
                } +
                bracketPadding +
                indent.bodyLinesIndent() + listDelimiters.close
    }

    private fun formatCompactList(
        indent: Indent,
        nextNode: AstNode?,
        compileTarget: CompileTarget,
        listDelimiters: ListDelimiters
    ): String {
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
                // Both objects need a separator when both are non-empty
                (isNotLastElement && currentIsObject && nextIsObject) -> {
                    val currentIsEmptyObject = currentValue.properties.isEmpty()
                    if (currentIsEmptyObject) {
                        // Empty objects already have braces, don't wrap them
                        "$elementString "
                    } else {
                        // Non-empty objects need to be wrapped to separate from the next object
                        "{$elementString}"
                    }
                }

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

    /**
    * Only need to explicitly end this list with a [org.kson.parser.TokenType.END_DASH] if the next
    * node in this document is a [ListElementNode] that does not belong to this list, or if there
    * is any trailing content (which means we have erroneous content after the main document)
    */
    private fun needsEndDash(nextNode: AstNode?):Boolean{
        return nextNode is ListElementNode || isTrailingContent(nextNode)
    }
}

interface ListElementNode : AstNode
class ListElementNodeError(sourceTokens: List<Token>) : AstNodeError(sourceTokens), ListElementNode
class ListElementNodeImpl(val value: KsonValueNode,
                          override val comments: List<String>,
                          sourceTokens: List<Token>) :
    ListElementNode, AstNodeImpl(sourceTokens), Documented {

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                when (compileTarget.formatConfig.formattingStyle) {
                    PLAIN -> formatWithDash(indent, nextNode, compileTarget)
                    DELIMITED -> formatWithDash(indent, nextNode, compileTarget, isDelimited = true)
                    COMPACT -> value.toSourceWithNext(indent, nextNode, compileTarget)
                    CLASSIC -> value.toSourceWithNext(indent, nextNode, compileTarget)
                }
            }

            is Yaml -> formatWithDash(indent, nextNode, compileTarget)
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
abstract class StringNodeImpl(sourceTokens: List<Token>) : StringNode, KsonValueNodeImpl(sourceTokens) {
    /**
     * The raw KSON source content of this string with ALL escapes still embedded in the string, including escaped
     * quotes. See [processedStringContent] for an evaluated version that can be shared externally as data
     */
    abstract val rawStringContent: String

    /**
     * The value of [rawStringContent] with delimiter-specific escaping removed (e.g. quote escapes for quoted strings,
     * embed delimiter escapes for embed blocks) but all other escapes intact. Identity for unquoted strings.
     */
    abstract val delimiterUnescapedRawContent: String

    /**
     * The processed data value of this string, with ALL escapes evaluated, ready for external consumption
     */
    abstract val processedStringContent: String

    abstract val contentTransformer: KsonContentTransformer
}

class QuotedStringNode(
    sourceTokens: List<Token>,
    // TODO this should not be nullable
    private val stringQuote: StringQuote?,
) : StringNodeImpl(sourceTokens) {

    override val contentTransformer: QuotedStringContentTransformer by lazy {
        QuotedStringContentTransformer(rawStringContent, location)
    }

    override val processedStringContent: String by lazy {
        if (stringQuote != null) {
            contentTransformer.processedContent
        } else {
            rawStringContent
        }
    }

    /**
     * Note: [rawStringContent] is the exact content of a [stringQuote]-delimited [Kson] string,
     *   including all escapes, but excluding the outer quotes.  A [Kson] string is escaped identically to a Json string,
     *   except that [Kson] allows raw whitespace to be embedded in strings
     */
    override val rawStringContent: String by lazy {
        renderTokens(sourceTokens)
    }

    /**
     * An "unquoted" Kson string: i.e. a valid Kson string with all escapes intact except for quote escapes.
     * This string must be [SingleQuote]'ed or [DoubleQuote]'ed and then quote-escaped with [StringQuote.escapeQuotes]
     * to obtain a fully valid KsonString
     */
    override val delimiterUnescapedRawContent: String by lazy {
        stringQuote?.unescapeQuotes(rawStringContent) ?: rawStringContent
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        if (StringUnquoted.isUnquotable(delimiterUnescapedRawContent)) {
            /**
             * This string does not require quotes in Kson, so we use the [renderUnquotableKsonString]
             * to get the best rendering for this string across all compile targets
             */
            return renderUnquotableKsonString(delimiterUnescapedRawContent, indent, compileTarget)
        }

        return when (compileTarget) {
            is Kson -> {
                if (compileTarget.formatConfig.formattingStyle == CLASSIC) {
                    return indent.firstLineIndent() + "\"${escapeRawWhitespace(DoubleQuote.escapeQuotes(delimiterUnescapedRawContent))}\""
                } else {
                    val singleQuoteCount = SingleQuote.countDelimiterOccurrences(delimiterUnescapedRawContent)
                    val doubleQuoteCount = DoubleQuote.countDelimiterOccurrences(delimiterUnescapedRawContent)

                    // prefer single-quotes unless double-quotes would require less escaping
                    val chosenDelimiter = if (doubleQuoteCount < singleQuoteCount) {
                        DoubleQuote
                    } else {
                        SingleQuote
                    }

                    val escapedContent = chosenDelimiter.escapeQuotes(delimiterUnescapedRawContent)
                    indent.firstLineIndent() + "${chosenDelimiter}$escapedContent${chosenDelimiter}"
                }
            }

            is Yaml -> {
                indent.firstLineIndent() + "\"${DoubleQuote.escapeQuotes(
                    // we ensure forward slashes are unescaped here since YAML does not allow escaping them
                    unescapeForwardSlashes(delimiterUnescapedRawContent))}\""
            }
        }
    }
}

open class UnquotedStringNode(sourceTokens: List<Token>) : StringNodeImpl(sourceTokens) {
    override val processedStringContent: String by lazy {
        rawStringContent
    }

    override val contentTransformer: IdentityContentTransformer by lazy {
        IdentityContentTransformer(rawStringContent, location)
    }

    override val rawStringContent: String by lazy {
        renderTokens(sourceTokens)
    }

    override val delimiterUnescapedRawContent: String by lazy {
        rawStringContent
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return renderUnquotableKsonString(rawStringContent, indent, compileTarget)
    }
}

private val yamlReservedKeywords = setOf(
    // Boolean true values
    "y", "Y", "yes", "Yes", "YES",
    "true", "True", "TRUE",
    "on", "On", "ON",
    // Boolean false values
    "n", "N", "no", "No", "NO",
    "false", "False", "FALSE",
    "off", "Off", "OFF",
    // Null values
    "null", "Null", "NULL"
)

/**
 * Render helper for unquoted KSON strings, i.e. [String]s for which [StringUnquoted.isUnquotable] returns true.
 * NOTE: the caller is responsible for ensuring [StringUnquoted.isUnquotable] returns true on [unquotedKsonString]
 */
private fun renderUnquotableKsonString(unquotedKsonString: String, indent: Indent, compileTarget: CompileTarget): String {
    return when (compileTarget) {
        is Kson -> {
            if (compileTarget.formatConfig.formattingStyle == CLASSIC){
                return indent.firstLineIndent() + "\"${unquotedKsonString}\""
            }else{
                indent.firstLineIndent() + unquotedKsonString
            }
        }

        is Yaml -> {
            indent.firstLineIndent() + if (yamlReservedKeywords.contains(unquotedKsonString)) {
                "$DoubleQuote$unquotedKsonString$DoubleQuote"
            } else {
                unquotedKsonString
            }
        }
    }
}

/**
 * Callers are in charge of ensuring that [sourceTokens] are fully valid, i.e. that the
 * [stringValue] produced by them is parseable by [NumberParser]
 */
class NumberNode(sourceTokens: List<Token>) : KsonValueNodeImpl(sourceTokens) {
    val stringValue: String by lazy {
        renderTokens(sourceTokens)
    }

    val contentTransformer: IdentityContentTransformer by lazy {
        IdentityContentTransformer(stringValue, location)
    }

    val value: ParsedNumber by lazy {
        val parsedNumber = NumberParser(stringValue).parse()
        parsedNumber.number ?: throw IllegalStateException(
            "Hitting this indicates a parser bug: unparseable " +
                    "strings NEVER should be passed here but we got: " + stringValue
        )
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + value.asString
            }
        }
    }
}

abstract class BooleanNode(sourceTokens: List<Token>) : KsonValueNodeImpl(sourceTokens) {
    abstract val value: Boolean
}

class TrueNode(sourceTokens: List<Token>) : BooleanNode(sourceTokens) {
    override val value = true

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + "true"
            }
        }
    }
}

class FalseNode(sourceTokens: List<Token>) : BooleanNode(sourceTokens) {
    override val value = false

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + "false"
            }
        }
    }
}

class NullNode(sourceTokens: List<Token>) : KsonValueNodeImpl(sourceTokens) {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + "null"
            }
        }
    }
}

class EmbedBlockNode(
    val embedTagNode: StringNodeImpl?,
    val embedContentNode: StringNodeImpl,
    sourceTokens: List<Token>
) :
    KsonValueNodeImpl(sourceTokens) {

    private val embedTag: String = embedTagNode?.processedStringContent ?: ""
    private val embedContent: String = embedContentNode.processedStringContent

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> renderKsonFormat(indent, compileTarget)
            is Yaml -> renderYamlFormat(indent, compileTarget)
        }
    }

    /**
     * Renders this embed block as KSON format source code
     *
     * @param indent The indentation to apply
     * @param compileTarget The KSON compile target with formatting configuration
     * @return The rendered KSON source string
     */
    private fun renderKsonFormat(indent: Indent, compileTarget: Kson): String {
        val (delimiter, content) = selectOptimalDelimiter()

        return when (compileTarget.formatConfig.formattingStyle) {
            PLAIN, DELIMITED -> {
                val indentedContent = content.lines().joinToString("\n${indent.bodyLinesIndent()}") { it }
                "${indent.firstLineIndent()}${delimiter.openDelimiter}$embedTag\n${indent.bodyLinesIndent()}$indentedContent${delimiter.closeDelimiter}"
            }
            COMPACT -> {
                val compactContent = content.lines().joinToString("\n") { it }
                "${delimiter.openDelimiter}$embedTag\n$compactContent${delimiter.closeDelimiter}"
            }
            CLASSIC -> {
                renderJsonFormat(indent, compileTarget as? Json ?: Json())
            }
        }
    }

    /**
     * Selects the optimal delimiter for the embed block content, preferring delimiters that don't appear in the content
     * to avoid escaping. Returns a pair of the chosen delimiter and the content (escaped if necessary).
     *
     * @return A pair of the chosen [EmbedDelim] and the content string (escaped if the delimiter appears in content)
     */
    private fun selectOptimalDelimiter(): Pair<EmbedDelim, String> {
        val percentCount = EmbedDelim.Percent.countDelimiterOccurrences(embedContent)
        val dollarCount = EmbedDelim.Dollar.countDelimiterOccurrences(embedContent)

        return when {
            percentCount == 0 -> EmbedDelim.Percent to embedContent
            dollarCount == 0 -> EmbedDelim.Dollar to embedContent
            else -> {
                val delimiter = if (dollarCount < percentCount) EmbedDelim.Dollar else EmbedDelim.Percent
                delimiter to delimiter.escapeEmbedContent(embedContent)
            }
        }
    }

    /**
     * Renders this embed block as YAML format source code
     *
     * @param indent The indentation to apply
     * @param compileTarget The YAML compile target with configuration
     * @return The rendered YAML source string
     */
    private fun renderYamlFormat(indent: Indent, compileTarget: Yaml): String {
        return if (!compileTarget.retainEmbedTags) {
            renderMultilineYamlString(embedContent, indent, indent.next(false))
        } else {
            renderYamlObject(indent)
        }
    }

    /**
     * Renders this embed block as JSON format source code
     *
     * @param indent The indentation to apply
     * @param compileTarget The JSON compile target with configuration
     * @return The rendered JSON source string
     */
    private fun renderJsonFormat(indent: Indent, compileTarget: Json): String {
        return if (!compileTarget.retainEmbedTags) {
            indent.firstLineIndent() + "\"${renderForJsonString(embedContent)}\""
        } else {
            renderJsonObject(indent, indent.next(false))
        }
    }

    /**
     * Renders this embed block as a JSON object with separate fields for embed tag, metadata, and content
     *
     * @param indent The base indentation to apply to the object
     * @param nextIndent The indentation for nested object properties
     * @return The rendered JSON object string
     */
    private fun renderJsonObject(indent: Indent, nextIndent: Indent): String {
        val embedTagLine = if (embedTag.isNotEmpty()) {
            "${nextIndent.bodyLinesIndent()}\"${EmbedObjectKeys.EMBED_TAG.key}\": \"${DoubleQuote.escapeQuotes(embedTag)}\",\n"
        } else ""

        return """
            |${indent.firstLineIndent()}{
            |$embedTagLine${nextIndent.bodyLinesIndent()}"${EmbedObjectKeys.EMBED_CONTENT.key}": "${renderForJsonString(embedContent)}"
            |${indent.bodyLinesIndent()}}
        """.trimMargin()
    }

    /**
     * Renders this embed block as a YAML object with separate fields for embed tag, metadata, and content
     *
     * @param indent The indentation to apply to the object
     * @return The rendered YAML object string
     */
    private fun renderYamlObject(indent: Indent): String {
        val embedTagLine = if (embedTag.isNotEmpty()) {
            "${indent.firstLineIndent()}${EmbedObjectKeys.EMBED_TAG.key}: \"${DoubleQuote.escapeQuotes(embedTag)}\"\n"
        } else ""

        return embedTagLine +
                "${indent.firstLineIndent()}${EmbedObjectKeys.EMBED_CONTENT.key}: " +
                renderMultilineYamlString(embedContent, indent.clone(true), indent.next(true))
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

class EmbedBlockContentNode(
    sourceTokens: List<Token>,
    private val embedDelim: EmbedDelim
) : StringNodeImpl(sourceTokens) {
    override val rawStringContent: String by lazy {
        renderTokens(sourceTokens)
    }

    override val delimiterUnescapedRawContent: String by lazy {
        embedDelim.unescapeEmbedContent(rawStringContent)
    }

    override val contentTransformer: EmbedContentTransformer by lazy {
        EmbedContentTransformer(
            rawContent = rawStringContent,
            embedDelim = embedDelim,
            rawLocation = this.location
        )
    }

    override val processedStringContent: String by lazy {
        contentTransformer.processedContent
    }

    override fun toSourceInternal(
        indent: Indent,
        nextNode: AstNode?,
        compileTarget: CompileTarget
    ): String {
        /**
         * [EmbedBlockNode] renders this using its [processedStringContent] property
         */
        throw ShouldNotHappenException("this node is render by ${EmbedBlockNode::class.simpleName}")
    }
}

private fun renderTokens(sourceTokens: List<Token>): String {
    return sourceTokens
        .filter { it.tokenType != WHITESPACE && it.tokenType != COMMENT }
        .joinToString("") { it.lexeme.text }
}

package org.kson.ast

import org.kson.CompileTarget
import org.kson.CompileTarget.*
import org.kson.ast.AstNode.Indent
import org.kson.parser.behavior.embedblock.EmbedDelim
import org.kson.parser.NumberParser
import org.kson.parser.NumberParser.ParsedNumber
import org.kson.tools.IndentType
import org.kson.parser.behavior.StringQuote
import org.kson.parser.behavior.StringQuote.SingleQuote
import org.kson.parser.behavior.StringQuote.DoubleQuote

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
abstract class AstNodeImpl : AstNode {
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
open class AstNodeError(private val invalidSource: String) : AstNode, AstNodeImpl() {
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
 * Any kson entity is either the [KsonRoot] of the document, an [ObjectPropertyNode]
 * on an object, or a [ListElementNode] in a list, and so semantically, those are the things
 * that make sense to document, so in our comment preservation strategy, these are the
 * [AstNode]s which accept comments.  This interface ties them together.
 */
interface Documented {
    val comments: List<String>
}

interface KsonRoot : AstNode
class KsonRootError(content: String) : KsonRoot, AstNodeError(content)
class KsonRootImpl(
    private val rootNode: AstNode,
    override val comments: List<String>,
    private val documentEndComments: List<String>
) : KsonRoot, AstNodeImpl(), Documented {

    /**
     * Produces valid [compileTarget] source code for the AST rooted at this [KsonRoot]
     */
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                var ksonDocument = rootNode.toSourceWithNext(indent, null, compileTarget)

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
                        "\n\n" + endComments
                    }
                }

                ksonDocument
            }
        }
    }
}

interface ValueNode : AstNode
class ValueNodeError(content: String) : ValueNode, AstNodeError(content)
abstract class ValueNodeImpl : ValueNode, AstNodeImpl()

class ObjectNode(private val properties: List<ObjectPropertyNode>) : ValueNodeImpl() {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                if (properties.isEmpty()) {
                    indent.firstLineIndent() + "{}"
                } else {
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
                    if (compileTarget is Kson && nextNode is ObjectPropertyNode) {
                        "$outputObject\n${indent.bodyLinesIndent()}."
                    } else {
                        // put a newline after multi-property objects
                        outputObject + if (properties.size > 1) "\n" else ""
                    }
                }
            }
            
            is Json -> {
                if (properties.isEmpty()) {
                    "${indent.firstLineIndent()}{}"
                } else {
                    """
                    |${indent.firstLineIndent()}{
                    |${properties.withIndex().joinToString(",\n") { (index, property) ->
                        val nodeAfterThisChild = properties.getOrNull(index + 1) ?: nextNode
                        property.toSourceWithNext(indent.next(false), nodeAfterThisChild, compileTarget) }
                    }
                    |${indent.bodyLinesIndent()}}
                    """.trimMargin()
                }
            }
        }
    }
}

interface ObjectPropertyNode : AstNode
class ObjectPropertyNodeError(content: String) : ObjectPropertyNode, AstNodeError(content)
class ObjectPropertyNodeImpl(
    private val name: KeywordNode,
    private val value: ValueNode,
    override val comments: List<String>
) :
    ObjectPropertyNode, AstNodeImpl(), Documented {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                if (value is ListNode || value is ObjectNode) {
                    // For lists and objects, put the value on the next line
                    name.toSourceWithNext(indent, value, compileTarget) + ":\n" +
                            value.toSourceWithNext(indent.next(false), nextNode, compileTarget)
                } else {
                    name.toSourceWithNext(indent, value, compileTarget) + ": " +
                            value.toSourceWithNext(indent.next(true), nextNode, compileTarget)
                }
            }

            is Json -> {
                "${name.toSourceWithNext(indent, nextNode, compileTarget)}: ${
                    value.toSourceWithNext(
                        indent.clone(true),
                        nextNode,
                        compileTarget
                    )
                }"
            }
        }
    }
}

class ListNode(
    private val elements: List<ListElementNode>
) : ValueNodeImpl() {

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                if (elements.isEmpty()) {
                    indent.firstLineIndent() + "<>"
                } else {
                    val outputList = elements.withIndex().joinToString("\n") { (index, element) ->
                        val nodeAfterThisChild = elements.getOrNull(index + 1) ?: nextNode
                        element.toSourceWithNext(indent, nodeAfterThisChild, compileTarget)
                    }

                    /**
                     * Only need to explicitly end this list with a [org.kson.parser.TokenType.DOT] if the next
                     * thing in this document is a [ListElementNode] that does not belong to this list
                     */
                    if (nextNode is ListElementNode) {
                        "$outputList\n${indent.bodyLinesIndent()}."
                    } else {
                        outputList
                    }
                }
            }

            is Yaml -> {
                if (elements.isEmpty()) {
                    indent.firstLineIndent() + "[]"
                } else {
                    elements.withIndex().joinToString("\n") { (index, element) ->
                        val nodeAfterThisChild = elements.getOrNull(index + 1) ?: nextNode
                        element.toSourceWithNext(indent.clone(false), nodeAfterThisChild, compileTarget)
                    }
                }
            }

            is Json -> {
                // We pad our list bracket with newlines if our list is non-empty
                val bracketPadding = if (elements.isEmpty()) "" else "\n"
                val endBraceIndent = if (elements.isEmpty()) "" else indent.bodyLinesIndent()
                indent.firstLineIndent() + "[" + bracketPadding +
                        elements.withIndex().joinToString(",\n") { (index, element) ->
                            val nodeAfterThisChild = elements.getOrNull(index + 1) ?: nextNode
                            element.toSourceWithNext(indent.next(false), nodeAfterThisChild, compileTarget)
                        } +
                        bracketPadding + endBraceIndent + "]"
            }
        }
    }
}

interface ListElementNode : AstNode
class ListElementNodeError(content: String) : AstNodeError(content), ListElementNode
class ListElementNodeImpl(val value: ValueNode, override val comments: List<String>)
    : ListElementNode, AstNodeImpl(), Documented {

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                if (value is ListNode) {
                    indent.bodyLinesIndent() + "- \n" + value.toSourceWithNext(indent.next(false), nextNode, compileTarget)
                } else {
                    indent.bodyLinesIndent() + "- " + value.toSourceWithNext(indent.next(true), nextNode, compileTarget)
                }
            }

            is Json ->  value.toSourceWithNext(indent, nextNode, compileTarget)
        }
    }
}

interface KeywordNode : ValueNode
class KeywordNodeError(content: String) : KeywordNode, AstNodeError(content)
abstract class KeywordNodeImpl : KeywordNode, ValueNodeImpl() {
    abstract val stringContent: String
}

/**
 * Note: [ksonEscapedStringContent] is expected to be the exact content of a [stringQuote]-delimited [Kson] string,
 *   including all escapes, but excluding the outer quotes.  A [Kson] string is escaped identically to a Json string,
 *   except that [Kson] allows raw whitespace to be embedded in strings
 */
open class StringNode(private val ksonEscapedStringContent: String, private val stringQuote: StringQuote) : KeywordNodeImpl() {

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
                // Check if we can use this string as a bare identifier
                val isSimple = unquotedString.isNotBlank() && unquotedString.withIndex().all { (index, letter) ->
                    if (index == 0) {
                        letter.isLetter() || letter == '_'
                    } else {
                        letter.isLetterOrDigit() || letter == '_'
                    }
                }

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

class IdentifierNode(override val stringContent: String) : KeywordNodeImpl() {
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
class NumberNode(stringValue: String) : ValueNodeImpl() {
    val value: ParsedNumber by lazy {
        val parsedNumber = NumberParser(stringValue).parse()
        parsedNumber.number ?: throw RuntimeException("Hitting this indicates a parser bug: unparseable " +
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

class TrueNode : ValueNodeImpl() {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                indent.firstLineIndent() + "true"
            }
        }
    }
}

class FalseNode : ValueNodeImpl() {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json-> {
                indent.firstLineIndent() + "false"
            }
        }
    }
}

class NullNode : ValueNodeImpl() {
    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                indent.firstLineIndent() + "null"
            }
        }
    }
}

class EmbedBlockNode(private val embedTag: String, embedContent: String, embedDelim: EmbedDelim) :
    ValueNodeImpl() {

    private val embedContent: String by lazy { embedDelim.unescapeEmbedContent(embedContent) }

    companion object {
        /**
         * If we are asked to compile with [CompileTarget.Yaml.retainEmbedTags], we compile
         * embed blocks to a Yaml object with these two properties: one for the tag string,
         * and one for the content multiline string
         */
        const val EMBED_TAG_KEYWORD = "embedTag"
        const val EMBED_CONTENT_KEYWORD = "embedContent"
    }

    override fun toSourceInternal(indent: Indent, nextNode: AstNode?, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                val defaultDelimCount = EmbedDelim.Percent.countDelimiterOccurrences(embedContent)
                if (defaultDelimCount == 0) {
                    // The primary delimiter is not in the content, so we can use the default delimiter
                    // without any escaping needed
                    indent.firstLineIndent() + EmbedDelim.Percent + embedTag + "\n" +
                            indent.bodyLinesIndent() + embedContent.split("\n")
                                .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                            EmbedDelim.Percent
                } else {
                    // Otherwise, check if we can use the alternate delimiter without escaping
                    val altDelimCount = EmbedDelim.Dollar.countDelimiterOccurrences(embedContent)
                    if (altDelimCount == 0) {
                        // We can use the alternate delimiter, but must handle default delimiter escapes
                        val escapedContent = EmbedDelim.Dollar.escapeEmbedContent(embedContent)
                        indent.firstLineIndent() + EmbedDelim.Dollar + embedTag + "\n" +
                                indent.bodyLinesIndent() + escapedContent.split("\n")
                                    .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                                EmbedDelim.Dollar
                    } else {
                        // We'll choose the delimiter that requires less escaping
                        val chosenDelimiter = if (altDelimCount < defaultDelimCount) {
                            EmbedDelim.Dollar
                        } else {
                            EmbedDelim.Percent
                        }

                        val escapedContent = chosenDelimiter.escapeEmbedContent(embedContent)
                        indent.firstLineIndent() + chosenDelimiter + embedTag + "\n" +
                                indent.bodyLinesIndent() + escapedContent.split("\n")
                                    .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                                chosenDelimiter
                    }
                }
            }

            is Yaml -> {
                if (!compileTarget.retainEmbedTags) {
                    renderMultilineYamlString(embedContent, indent, indent.next(false))
                } else {
                    indent.firstLineIndent() + "$EMBED_TAG_KEYWORD: \"" + embedTag + "\"\n" +
                    indent.bodyLinesIndent() + "$EMBED_CONTENT_KEYWORD: " +
                    renderMultilineYamlString(embedContent, indent, indent.next(false))
                }
            }

            is Json -> {
                if (!compileTarget.retainEmbedTags) {
                    indent.firstLineIndent() + "\"${renderForJsonString(embedContent)}\""
                } else {
                    val nextIndent = indent.next(false)
                    """
                    |${indent.firstLineIndent()}{
                    |${nextIndent.bodyLinesIndent()}"$EMBED_TAG_KEYWORD": "$embedTag",
                    |${nextIndent.bodyLinesIndent()}"$EMBED_CONTENT_KEYWORD": "${renderForJsonString(embedContent)}"
                    |}
                    """.trimMargin()
                }
            }
        }
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

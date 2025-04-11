package org.kson.ast

import org.kson.CompileTarget
import org.kson.CompileTarget.*
import org.kson.ast.AstNode.Indent
import org.kson.parser.EmbedDelim
import org.kson.parser.NumberParser
import org.kson.parser.NumberParser.ParsedNumber

abstract class AstNode {
    /**
     * Abstract representation of the indentation to apply when serializing an AST as source code
     */
    data class Indent(
        /**
         * How deep to make this indent
         */
        private val indentLevel: Int,
        /**
         * The number of spaces to render for each [indentLevel]
         */
        private val indentSize: Int,
        /**
         * Whether or not this indent "hangs", i.e. only starts after the first newline of the text being indented
         */
        private val hangingIndent: Boolean = false
    ) {
        /**
         * Constructs an initial/default indent
         */
        constructor() : this(0, 2, false)

        private val indentString = " ".repeat(indentSize)

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
         * Produce a copy of this indent with the given [hanging] value for its [hangingIndent]
         */
        fun clone(hanging: Boolean): Indent {
            return Indent(indentLevel, indentSize, hanging)
        }

        /**
         * Produce the "next" indent in from this one, with the given [hanging] value for its [hangingIndent]
         */
        fun next(hanging: Boolean): Indent {
            return Indent(indentLevel + 1, indentSize, hanging)
        }
    }

    /**
     * Transpiles this [AstNode] to the given [compileTarget] source, respecting the configuration in the given
     * [CompileTarget]
     */
    fun toSource(indent: Indent, compileTarget: CompileTarget): String {
        return if (compileTarget.preserveComments && this is Documented && comments.isNotEmpty()) {
            // if we have comments, write them followed by the node content on the next line with an appropriate indent
            indent.firstLineIndent() + comments.joinToString("\n${indent.bodyLinesIndent()}") +
                    "\n" + toSourceInternal(indent.clone(false), compileTarget)
        } else {
            // otherwise, just pass through to the node content
            toSourceInternal(indent, compileTarget)
        }
    }

    /**
     * Subclasses must implement serialization of the AST subtree rooted at their node to a corresponding
     * source code snippet for [compileTarget], EXCLUDING comments (comment writing is handled "higher" up
     * in [toSource]).
     *
     * This method is protected since it should never be called outside of [toSource], which handles ensuring
     * comments are properly serialized for all nodes when appropriate.  So:
     *
     * DO NOT call this method---call [toSource] instead.
     */
    protected abstract fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String
}

/**
 * Any kson entity is ether the [KsonRoot] of the document, an [ObjectPropertyNode]
 * on an object, or a [ListElementNode] in a list, and so semantically, those are the things
 * that make sense to document, so in our comment preservation strategy, these are the
 * [AstNode]s which accept comments.  This interface ties them together.
 */
interface Documented {
    val comments: List<String>
}

class KsonRoot(
    private val rootNode: AstNode,
    override val comments: List<String>,
    private val documentEndComments: List<String>
) : AstNode(), Documented {

    /**
     * Produces valid [compileTarget] source code for the AST rooted at this [KsonRoot]
     */
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                rootNode.toSource(indent, compileTarget) +
                        if (compileTarget.preserveComments && documentEndComments.isNotEmpty()) {
                            "\n\n" + documentEndComments.joinToString("\n")
                        } else {
                            ""
                        }
            }
        }
    }
}

abstract class ValueNode : AstNode()

class ObjectDefinitionNode(private val internalsNode: ObjectInternalsNode) :
    ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                internalsNode.toSource(indent, compileTarget)
            }
        }
    }
}

class ObjectInternalsNode(private val properties: List<ObjectPropertyNode>) : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                if (properties.isEmpty()) {
                    "${indent.firstLineIndent()}{}"
                } else {
                    """
                    |${indent.firstLineIndent()}{
                    |${properties.joinToString("\n") { it.toSource(indent.next(false), compileTarget) }}
                    |${indent.bodyLinesIndent()}}
                    """.trimMargin()
                }
            }
            
            is Json -> {
                if (properties.isEmpty()) {
                    "${indent.firstLineIndent()}{}"
                } else {
                    """
                    |${indent.firstLineIndent()}{
                    |${properties.joinToString(",\n") { it.toSource(indent.next(false), compileTarget) }}
                    |${indent.bodyLinesIndent()}}
                    """.trimMargin()
                }
            }

            is Yaml -> {
                if (properties.isEmpty()) {
                    indent.firstLineIndent() + "{}"
                } else {
                    properties.joinToString("\n") {
                        it.toSource(indent, compileTarget)
                    }
                }
            }
        }
    }
}

class ObjectPropertyNode(
    private val name: KeywordNode,
    private val value: ValueNode,
    override val comments: List<String>
) :
    AstNode(), Documented {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Json -> {
                "${name.toSource(indent, compileTarget)}: ${
                    value.toSource(
                        indent.clone(true),
                        compileTarget
                    )
                }"
            }
            is Yaml -> {
                if (value is ListNode || value is ObjectDefinitionNode) {
                    // For lists and objects, put the value on the next line
                    name.toSource(indent, compileTarget) + ":\n" +
                            value.toSource(indent.next(false), compileTarget)
                } else {
                    name.toSource(indent, compileTarget) + ": " + value.toSource(indent.clone(true), compileTarget)
                }
            }
        }
    }
}

class ListNode(private val elements: List<ListElementNode>) : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Json -> {
                // We pad our list bracket with newlines if our list is non-empty
                val bracketPadding = if (elements.isEmpty()) "" else "\n"
                val endBraceIndent = if (elements.isEmpty()) "" else indent.bodyLinesIndent()
                indent.firstLineIndent() + "[" + bracketPadding +
                        elements.joinToString(",\n") {
                            it.toSource(indent.next(false), compileTarget)
                        } +
                        bracketPadding + endBraceIndent + "]"
            }

            is Yaml -> {
                if (elements.isEmpty()) {
                    indent.firstLineIndent() + "[]"
                } else {
                    elements.joinToString("\n") {
                        it.toSource(indent.clone(false), compileTarget)
                    }
                }
            }
        }
    }
}

class ListElementNode(val value: ValueNode, override val comments: List<String>) : AstNode(), Documented {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Json -> {
                value.toSource(indent, compileTarget)
            }
            is Yaml -> {
                if (value is ListNode) {
                    indent.firstLineIndent() + "- \n" + value.toSource(indent.next(false), compileTarget)
                } else {
                    indent.firstLineIndent() + "- " + value.toSource(indent.clone(true), compileTarget)
                }
            }
        }
    }
}

abstract class KeywordNode : ValueNode() {
    abstract val stringContent: String
}

open class StringNode(override val stringContent: String) : KeywordNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + "\"" + stringContent + "\""
            }

            is Json -> {
                indent.firstLineIndent() + "\"${escapeStringLiterals(stringContent)}\""
            }
        }
    }
}

class IdentifierNode(override val stringContent: String) : KeywordNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + stringContent
            }

            is Json -> {
                indent.firstLineIndent() + "\"${escapeStringLiterals(stringContent)}\""
            }
        }
    }
}

/**
 * Callers are in charge of ensuring that `stringValue` is parseable by [NumberParser]
 */
class NumberNode(stringValue: String) : ValueNode() {
    val value: ParsedNumber by lazy {
        val parsedNumber = NumberParser(stringValue).parse()
        parsedNumber.number ?: throw RuntimeException("Hitting this indicates a parser bug: unparseable " +
                "strings should be passed here but we got: " + stringValue)
    }

    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json-> {
                indent.firstLineIndent() + value.asString
            }
        }
    }
}

class TrueNode : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                indent.firstLineIndent() + "true"
            }
        }
    }
}

class FalseNode : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json-> {
                indent.firstLineIndent() + "false"
            }
        }
    }
}

class NullNode : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml, is Json -> {
                indent.firstLineIndent() + "null"
            }
        }
    }
}

class EmbedBlockNode(private val embedTag: String, private val embedContent: String) :
    ValueNode() {

    companion object {
        /**
         * If we are asked to compile with [CompileTarget.Yaml.retainEmbedTags], we compile
         * embed blocks to a Yaml object with these two properties: one for the tag string,
         * and one for the content multiline string
         */
        const val EMBED_TAG_KEYWORD = "embedTag"
        const val EMBED_CONTENT_KEYWORD = "embedContent"
    }

    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                val defaultDelimCount = EmbedDelim.Percent.countDelimiterOccurrences(embedContent)
                if (defaultDelimCount == 0) {
                    // The primary delimiter is not in the content, so we can use the default delimiter
                    // without any escaping needed
                    indent.firstLineIndent() + EmbedDelim.Percent + embedTag + "\n" +
                            indent.bodyLinesIndent() + embedContent.split("\n")
                                .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                            indent.bodyLinesIndent() + EmbedDelim.Percent
                } else {
                    // Otherwise, check if we can use the alternate delimiter without escaping
                    val altDelimCount = EmbedDelim.Dollar.countDelimiterOccurrences(embedContent)
                    if (altDelimCount == 0) {
                        // We can use the alternate delimiter, but must handle default delimiter escapes
                        val escapedContent = EmbedDelim.Dollar.escapeEmbedContent(embedContent)
                        indent.firstLineIndent() + EmbedDelim.Dollar + embedTag + "\n" +
                                indent.bodyLinesIndent() + escapedContent.split("\n")
                                    .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                                indent.bodyLinesIndent() + EmbedDelim.Dollar
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
                                indent.bodyLinesIndent() + chosenDelimiter
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
                    indent.firstLineIndent() + "\"${escapeStringLiterals(embedContent)}\""
                } else {
                    val nextIndent = indent.next(false)
                    """
                    |${indent.firstLineIndent()}{
                    |${nextIndent.bodyLinesIndent()}"$EMBED_TAG_KEYWORD": "$embedTag",
                    |${nextIndent.bodyLinesIndent()}"$EMBED_CONTENT_KEYWORD": "${escapeStringLiterals(embedContent)}"
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

/**
 * Escapes quotes and whitespace characters (newlines, carriage returns and tabs) in a string,
 * useful for instance when serializing a Kson-escaped string (which allows raw whitespace)
 * to a JSON-escaped string (which does not).
 *
 * TODO this is very likely not sufficient for JSON escaping and should be inspected and improved
 *
 * @param str The string to escape
 * @return The string with quotes and whitespace characters (newlines, carriage returns, tabs) escaped with backslashes
 */
private fun escapeStringLiterals(str: String): String {
    return str
        // escape backslashes that aren't part of escape's we're detecting here
        .replace("""(?<!\\)\\(?!["\\nrt])""".toRegex(), """\\\\""")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

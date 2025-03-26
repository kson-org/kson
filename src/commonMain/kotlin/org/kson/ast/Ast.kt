package org.kson.ast

import org.kson.ast.AstNode.Indent
import org.kson.ast.CompileTarget.Kson
import org.kson.ast.CompileTarget.Yaml
import org.kson.parser.EMBED_DELIMITER
import org.kson.parser.NumberParser
import org.kson.parser.NumberParser.ParsedNumber

/**
 * Configuration for different compilation targets
 */
sealed class CompileTarget {

    /**
     * Compile target for serializing a Kson AST out to Kson source
     */
    data object Kson : CompileTarget()

    /**
     * Compile target for Yaml transpilation
     *
     * @param retainEmbedTags If true, embed blocks will be compiled to objects containing both tag and content
     */
    data class Yaml(
        val retainEmbedTags: Boolean = false
    ) : CompileTarget()
}

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
     * Transpiles this [AstNode] to the given [compileTarget], preserving comments from the originally parsed Kson
     */
    fun toCommentedSource(indent: Indent, compileTarget: CompileTarget): String {
        return if (this is Documented && comments.isNotEmpty()) {
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
     * in [toCommentedSource]).
     *
     * This method is protected since it should never be called outside of [toCommentedSource], which handles ensuring
     * comments are properly serialized for all nodes when appropriate.  So:
     *
     * DO NOT call this method---call [toCommentedSource] instead.
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
            is Kson, is Yaml -> {
                rootNode.toCommentedSource(indent, compileTarget) +
                        if (documentEndComments.isNotEmpty()) {
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
            is Kson, is Yaml -> {
                internalsNode.toCommentedSource(indent, compileTarget)
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
                    |${properties.joinToString("\n") { it.toCommentedSource(indent.next(false), compileTarget) }}
                    |${indent.bodyLinesIndent()}}
                    """.trimMargin()
                }
            }

            is Yaml -> {
                if (properties.isEmpty()) {
                    indent.firstLineIndent() + "{}"
                } else {
                    properties.joinToString("\n") { 
                        it.toCommentedSource(indent, compileTarget) 
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
            is Kson -> {
                "${name.toCommentedSource(indent, compileTarget)}: ${
                    value.toCommentedSource(
                        indent.clone(true),
                        compileTarget
                    )
                }"
            }
            is Yaml -> {
                if (value is ListNode || value is ObjectDefinitionNode) {
                    // For lists and objects, put the value on the next line
                    name.toCommentedSource(indent, compileTarget) + ":\n" +
                            value.toCommentedSource(indent.next(false), compileTarget)
                } else {
                    name.toCommentedSource(indent, compileTarget) + ": " + value.toCommentedSource(indent.clone(true), compileTarget)
                }
            }
        }
    }
}

class ListNode(private val elements: List<ListElementNode>) : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                // We pad our list bracket with newlines if our list is non-empty
                val bracketPadding = if (elements.isEmpty()) "" else "\n"
                val endBraceIndent = if (elements.isEmpty()) "" else indent.bodyLinesIndent()
                indent.firstLineIndent() + "[" + bracketPadding +
                        elements.joinToString(",\n") {
                            it.toCommentedSource(indent.next(false), compileTarget)
                        } +
                        bracketPadding + endBraceIndent + "]"
            }

            is Yaml -> {
                if (elements.isEmpty()) {
                    indent.firstLineIndent() + "[]"
                } else {
                    elements.joinToString("\n") {
                        it.toCommentedSource(indent.clone(false), compileTarget)
                    }
                }
            }
        }
    }
}

class ListElementNode(val value: ValueNode, override val comments: List<String>) : AstNode(), Documented {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson -> {
                value.toCommentedSource(indent, compileTarget)
            }
            is Yaml -> {
                if (value is ListNode) {
                    indent.firstLineIndent() + "- \n" + value.toCommentedSource(indent.next(false), compileTarget)
                } else {
                    indent.firstLineIndent() + "- " + value.toCommentedSource(indent.clone(true), compileTarget)
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
        }
    }
}

class IdentifierNode(override val stringContent: String) : KeywordNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + stringContent
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
            is Kson, is Yaml -> {
                indent.firstLineIndent() + value.asString
            }
        }
    }
}

class TrueNode : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + "true"
            }
        }
    }
}

class FalseNode : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + "false"
            }
        }
    }
}

class NullNode : ValueNode() {
    override fun toSourceInternal(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            is Kson, is Yaml -> {
                indent.firstLineIndent() + "null"
            }
        }
    }
}

/**
 * TODO [embedTag] and [embedContent] may contain escaped embed delimiters.  These will need to be processed once
 *   we implement compile targets other than re-serializing out to Kson
 */
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
                indent.firstLineIndent() + EMBED_DELIMITER + embedTag + "\n" +
                        indent.bodyLinesIndent() + embedContent.split("\n")
                            .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                        indent.bodyLinesIndent() + EMBED_DELIMITER
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

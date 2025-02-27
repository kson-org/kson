package org.kson.ast

import org.kson.ast.AstNode.CompileTarget.KSON
import org.kson.ast.AstNode.CompileTarget.YAML
import org.kson.parser.EMBED_DELIMITER

abstract class AstNode {
    enum class CompileTarget {
        KSON,
        YAML
    }

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
     * Serialize the AST subtree rooted at this [AstNode] to a corresponding kson source code snippet,
     * including comments
     *
     * Produces complete Kson source when invoked on a [KsonRoot]
     */
    fun toKsonSource(indent: Indent): String {
        return toCommentedSource(indent, KSON)
    }

    /**
     * Yaml transpilation entry point: serializes the AST subtree rooted at this [AstNode] to a corresponding
     * Yaml source code snippet, including comments
     *
     * NOTE: this compiles embed blocks to Yaml multiline strings
     *    TODO add an option to have embed blocks compile to an object which retains the tag, something like:
     *      ```
     *       embedTag: "the embed tag"
     *       content: |
     *         the embed block content
     *      ```
     *
     *
     * Produces complete Yaml source when invoked on a [KsonRoot]
     */
    fun toYamlSource(indent: Indent): String {
        return toCommentedSource(indent, YAML)
    }

    /**
     * Transpiles this [AstNode] to the given [compileTarget], preserving comments from the originally parsed Kson
     */
    fun toCommentedSource(indent: Indent, compileTarget: CompileTarget): String {
        return if (this is Documented && comments.isNotEmpty()) {
            // if we have comments, write them followed by the node content on the next line with an appropriate indent
            indent.firstLineIndent() + comments.joinToString("\n${indent.bodyLinesIndent()}") +
                    "\n" + toCompileTargetSource(indent.clone(false), compileTarget)
        } else {
            // otherwise, just pass through to the node content
            toCompileTargetSource(indent, compileTarget)
        }
    }

    /**
     * Subclasses must implement serialization of the AST subtree rooted at their node to a corresponding
     * source code snippet for [compileTarget], EXCLUDING comments (comment writing is handled "higher" up in the
     * in [toCommentedSource]).
     *
     * This method is protected since it should never be called outside of [toCommentedSource], which handles ensuring
     * comments are properly serialized for all nodes.
     */
    protected abstract fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String
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
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON, YAML -> {
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
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON, YAML -> {
                internalsNode.toCommentedSource(indent, compileTarget)
            }
        }
    }
}

class ObjectInternalsNode(private val properties: List<ObjectPropertyNode>) : ValueNode() {
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON -> {
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

            YAML -> {
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
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON -> {
                "${name.toCommentedSource(indent, compileTarget)}: ${
                    value.toCommentedSource(
                        indent.clone(true),
                        compileTarget
                    )
                }"
            }
            YAML -> {
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
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON -> {
                // We pad our list bracket with newlines if our list is non-empty
                val bracketPadding = if (elements.isEmpty()) "" else "\n"
                val endBraceIndent = if (elements.isEmpty()) "" else indent.bodyLinesIndent()
                indent.firstLineIndent() + "[" + bracketPadding +
                        elements.joinToString(",\n") {
                            it.toKsonSource(indent.next(false))
                        } +
                        bracketPadding + endBraceIndent + "]"
            }

            YAML -> {
                if (elements.isEmpty()) {
                    indent.firstLineIndent() + "[]"
                } else {
                    elements.joinToString("\n") {
                        it.toYamlSource(indent.clone(false))
                    }
                }
            }
        }
    }
}

class ListElementNode(val value: ValueNode, override val comments: List<String>) : AstNode(), Documented {
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON -> {
                value.toCommentedSource(indent, compileTarget)
            }
            YAML -> {
                if (value is ListNode) {
                    indent.firstLineIndent() + "- \n" + value.toYamlSource(indent.next(false))
                } else {
                    indent.firstLineIndent() + "- " + value.toYamlSource(indent.clone(true))
                }
            }
        }
    }
}

abstract class KeywordNode : ValueNode() {
    abstract val keyword: String
}

open class StringNode(override val keyword: String) : KeywordNode() {
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON, YAML -> {
                indent.firstLineIndent() + "\"" + keyword + "\""
            }
        }
    }
}

class IdentifierNode(override val keyword: String) : KeywordNode() {
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON, YAML -> {
                indent.firstLineIndent() + keyword
            }
        }
    }
}

/**
 * @param stringDouble MUST be parseable as a [Double]
 */
class NumberNode(private val stringDouble: String) : ValueNode() {
    /**
     * This throws a [NumberFormatException] if the given [stringDouble]
     * violates the pre-condition that it must be parseable as [Double]
     */
    val value = stringDouble.toDouble()

    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON, YAML -> {
                indent.firstLineIndent() + value.toString()
            }
        }
    }
}

class TrueNode : ValueNode() {
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON, YAML -> {
                indent.firstLineIndent() + "true"
            }
        }
    }
}

class FalseNode : ValueNode() {
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON, YAML -> {
                indent.firstLineIndent() + "false"
            }
        }
    }
}

class NullNode : ValueNode() {
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON, YAML -> {
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
    override fun toCompileTargetSource(indent: Indent, compileTarget: CompileTarget): String {
        return when (compileTarget) {
            KSON -> {
                indent.firstLineIndent() + EMBED_DELIMITER + embedTag + "\n" +
                        indent.bodyLinesIndent() + embedContent.split("\n")
                            .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                        indent.bodyLinesIndent() + EMBED_DELIMITER
            }

            YAML -> {
                val indentSize = indent.next(false).bodyLinesIndent().length
                
                // Find minimum leading whitespace across non-empty lines
                val contentIndentSize = embedContent.split("\n")
                    .filter { it.isNotBlank() }
                    .minOfOrNull { line -> line.takeWhile { it.isWhitespace() }.length } ?: 0

                // The user's embedded content has an indent we must maintain, so we must tell Yaml how much indent
                // is just for Yaml to ensure it does not eat up the content's indent too
                val multilineLineIndicator = if (contentIndentSize > 0) "|$indentSize" else "|"

                val nextIndent = indent.next(false).bodyLinesIndent()
                indent.firstLineIndent() + multilineLineIndicator + "\n" +
                        embedContent.split("\n")
                            .joinToString("\n") { line ->
                                nextIndent + line
                            }
            }
        }
    }
}
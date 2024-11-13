package org.kson.ast

import org.kson.parser.EMBED_DELIM_CHAR

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
     * Serialize the AST subtree rooted at this node to a corresponding kson source code snippet,
     * including comments
     * (or an entire kson source file when called on a [KsonRoot])
     */
    fun toKsonSource(indent: Indent): String {
        return if (this is Documented && comments.isNotEmpty()) {
            // if we have comments, write them followed by the node content on the next line with an appropriate indent
            indent.firstLineIndent() + comments.joinToString("\n${indent.firstLineIndent()}") +
                    "\n" + toKsonSourceInternal(indent.clone(false))
        } else {
            // otherwise, just pass through to the node content
            toKsonSourceInternal(indent)
        }
    }

    /**
     * Subclasses must implement serialization of the AST subtree rooted at their node to a corresponding kson
     * source code snippet, EXCLUDING comments (comment writing is handled "higher" up in the rendering
     * in [toKsonSource])
     */
    protected abstract fun toKsonSourceInternal(indent: Indent): String
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

class KsonRoot(private val rootNode: AstNode, override val comments: List<String>, val documentEndComments: List<String>) : AstNode(), Documented {

    /**
     * Produces valid kson source corresponding to the AST rooted at this [KsonRoot]
     */
    override fun toKsonSourceInternal(indent: Indent): String {
        return rootNode.toKsonSource(indent) +
                if (documentEndComments.isNotEmpty()) {
                    "\n\n" + documentEndComments.joinToString("\n")
                } else {
                    ""
                }
    }
}

abstract class ValueNode : AstNode()

class ObjectDefinitionNode(private val internalsNode: ObjectInternalsNode) :
    ValueNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        return internalsNode.toKsonSource(indent)
    }
}

class ObjectInternalsNode(private val properties: List<ObjectPropertyNode>) : ValueNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        return if (properties.isEmpty()) {
            "${indent.firstLineIndent()}{}"
        } else {
            """
                |${indent.firstLineIndent()}{
                |${properties.joinToString("\n") { it.toKsonSource(indent.next(false)) }}
                |${indent.bodyLinesIndent()}}
                """.trimMargin()
        }
    }

}

class ObjectPropertyNode(private val name: KeywordNode, private val value: ValueNode, override val comments: List<String>) :
    AstNode(), Documented {
    override fun toKsonSourceInternal(indent: Indent): String {
        return "${name.toKsonSource(indent)}: ${value.toKsonSource(indent.clone(true))}"
    }
}

class ListNode(private val elements: List<ListElementNode>) : ValueNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        // We pad our list bracket with newlines if our list is non-empty
        val bracketPadding = if (elements.isEmpty()) "" else "\n"
        val endBraceIndent = if (elements.isEmpty()) "" else indent.bodyLinesIndent()
        return indent.firstLineIndent() + "[" + bracketPadding +
                elements.joinToString(",\n") {
                    it.toKsonSource(indent.next(false))
                } +
                bracketPadding + endBraceIndent + "]"
    }
}

class ListElementNode(val value: ValueNode, override val comments: List<String>) : AstNode(), Documented {
    override fun toKsonSourceInternal(indent: Indent): String {
        return value.toKsonSource(indent)
    }
}

abstract class KeywordNode : ValueNode() {
    abstract val keyword: String
}

open class StringNode(override val keyword: String) : KeywordNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + "\"" + keyword + "\""
    }
}

class IdentifierNode(override val keyword: String) : KeywordNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + keyword
    }
}

/**
 * @param stringDouble MUST be parseable as a [Double]
 */
class NumberNode(private val stringDouble: String) : ValueNode() {
    /**
     * This throws a [NumberFormatException] if the given [stringDouble]
     * violates the pre-conditon that it must be parseable as [Double]
     */
    val value = stringDouble.toDouble()
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + value.toString()
    }
}

class TrueNode : ValueNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + "true"
    }
}

class FalseNode : ValueNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + "false"
    }
}

class NullNode : ValueNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + "null"
    }
}

/**
 * TODO [embedTag] and [embedContent] may contain escaped embed delimiters.  These will need to be processed once
 *   we implement compile targets other than re-serializing out to Kson
 */
class EmbedBlockNode(private val embedTag: String, private val embedContent: String) :
    ValueNode() {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + EMBED_DELIM_CHAR + EMBED_DELIM_CHAR + embedTag + "\n" +
                indent.bodyLinesIndent() + embedContent.split("\n")
            .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                indent.bodyLinesIndent() + EMBED_DELIM_CHAR + EMBED_DELIM_CHAR
    }
}
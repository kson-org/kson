package org.kson.ast

import org.kson.parser.EMBED_DELIM_CHAR

abstract class AstNode(private val comments: List<String>) {

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
        return if (comments.isNotEmpty()) {
            // if we have comments, write them followed by the node content on the next line with an appropriate indent
            indent.firstLineIndent() + comments.joinToString("\n") +
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

class KsonRoot(private val rootNode: AstNode, comments: List<String>) : AstNode(comments) {

    /**
     * Produces valid kson source corresponding to the AST rooted at this [KsonRoot]
     */
    override fun toKsonSourceInternal(indent: Indent): String {
        return rootNode.toKsonSource(indent)
    }
}

abstract class ValueNode(comments: List<String>) : AstNode(comments)

class ObjectDefinitionNode(
    private val name: String = "", private val internalsNode: ObjectInternalsNode,
    comments: List<String>
) :
    ValueNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        val renderedName = if (name.isEmpty()) "" else "$name "
        return "$renderedName${internalsNode.toKsonSource(indent)}"
    }
}

class ObjectInternalsNode(private val properties: List<PropertyNode>, comments: List<String>) : ValueNode(comments) {
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

class PropertyNode(private val name: KeywordNode, private val value: ValueNode, comments: List<String>) :
    AstNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        return "${name.toKsonSource(indent)}: ${value.toKsonSource(indent.clone(true))}"
    }
}

class ListNode(private val elements: List<ListElementNode>, comments: List<String>) : ValueNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        // We pad our list bracket with newlines if our list is non-empty
        val bracketPadding = if (elements.isEmpty()) "" else "\n"
        return indent.firstLineIndent() + "[" + bracketPadding +
                elements.joinToString(",\n") {
                    it.toKsonSource(indent.next(false))
                } +
                bracketPadding + indent.bodyLinesIndent() + "]"
    }
}

class ListElementNode(val value: ValueNode, comments: List<String>) : AstNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        return value.toKsonSource(indent)
    }
}

abstract class KeywordNode(comments: List<String>) : ValueNode(comments) {
    abstract val keyword: String
}

open class StringNode(override val keyword: String, comments: List<String>) : KeywordNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + "\"" + keyword + "\""
    }
}

class IdentifierNode(override val keyword: String, comments: List<String>) : KeywordNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + keyword
    }
}

/**
 * @param stringValue MUST be parseable as a [Double] parser todo this is a lot to ask of callers, can/should we improve?
 */
class NumberNode(stringValue: String, comments: List<String>) : ValueNode(comments) {
    /**
     * Our parse believes it will never allow an unparseable string to be passed into this constructor,
     * so we allow the uncaught NumberFormatException to bubble out as a RuntimeException
     * to loudly error when/if our belief is invalidated
     */
    val value = stringValue.toDouble()
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + value.toString()
    }
}

class TrueNode(comments: List<String>) : ValueNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + "true"
    }
}

class FalseNode(comments: List<String>) : ValueNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + "false"
    }
}

class NullNode(comments: List<String>) : ValueNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + "null"
    }
}

class EmbedBlockNode(private val embedTag: String, private val embedContent: String, comments: List<String>) :
    ValueNode(comments) {
    override fun toKsonSourceInternal(indent: Indent): String {
        return indent.firstLineIndent() + EMBED_DELIM_CHAR + EMBED_DELIM_CHAR + embedTag + "\n" +
                indent.bodyLinesIndent() + embedContent.split("\n")
            .joinToString("\n${indent.bodyLinesIndent()}") { it } +
                indent.bodyLinesIndent() + EMBED_DELIM_CHAR + EMBED_DELIM_CHAR
    }
}
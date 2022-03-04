package org.kson.ast

import org.kson.parser.EMBED_DELIM_CHAR

interface AstNode {
    /**
     * Serialize the AST subtree rooted at this node to a corresponding kson source code snippet
     * (or an entire kson source file when called on a [KsonRoot])
     */
    fun toKsonSource(indentLevel: Int = 0, indent: String = "  "): String
}

class KsonRoot(private val rootNode: AstNode) : AstNode {

    /**
     * Produces valid kson source corresponding to the AST rooted at this [KsonRoot]
     */
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return rootNode.toKsonSource(indentLevel, indent)
    }
}

interface ValueNode : AstNode

class ObjectDefinitionNode(private val name: String = "", private val internalsNode: ObjectInternalsNode) :
    ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        val renderedName = if (name.isEmpty()) "" else "$name "
        return "$renderedName${internalsNode.toKsonSource(indentLevel, indent)}"
    }
}

class ObjectInternalsNode(private val properties: List<PropertyNode>) : ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return if (properties.isEmpty()) {
            "{}"
        } else {
            """
                |{
                |${properties.joinToString("\n") { it.toKsonSource(indentLevel + 1, indent) }}
                |}
                """.trimMargin()
        }
    }

}

class PropertyNode(private val name: KeywordNode, private val value: ValueNode) : AstNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "${name.toKsonSource(0)}: ${value.toKsonSource(0)}"
    }
}

class ListNode(private val values: List<ValueNode>) : ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        // We pad our list bracket with newlines if our list is non-empty
        val bracketPadding = if (values.isEmpty()) "" else "\n"
        return indent.repeat(indentLevel) + "[" + bracketPadding +
                values.joinToString(",\n") {
                    it.toKsonSource(indentLevel + 1)
                } +
                indent.repeat(indentLevel) + bracketPadding + "]"
    }
}

interface KeywordNode : AstNode {
    val value: String
}

open class StringNode(override val value: String) : ValueNode, KeywordNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "\"" + value + "\""
    }
}

class IdentifierNode(override val value: String) : ValueNode, KeywordNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + value
    }
}

/**
 * @param stringValue MUST be parseable as a [Double] parser todo this is a lot to ask of callers, can/should we improve?
 */
class NumberNode(stringValue: String) : ValueNode {
    /**
     * Our parse believes it will never allow an unparseable string to be passed into this constructor,
     * so we allow the uncaught NumberFormatException to bubble out as a RuntimeException
     * to loudly error when/if our belief is invalidated
     */
    val value = stringValue.toDouble()
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + value.toString()
    }
}

class TrueNode : ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "true"
    }
}

class FalseNode : ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "false"
    }
}

class NullNode : ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "null"
    }
}

class EmbedBlockNode(private val embedTag: String, private val embedContent: String) : ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + EMBED_DELIM_CHAR + EMBED_DELIM_CHAR + embedTag + "\n" +
                embedContent.split("\n").joinToString("\n") { it } +
                indent.repeat(indentLevel) + EMBED_DELIM_CHAR + EMBED_DELIM_CHAR
    }
}
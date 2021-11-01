package org.kson.ast

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

class ObjectDefinitionNode(private val name: String = "", private val internalsNode: ObjectInternalsNode?) :
    ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        val renderedName = if (name.isEmpty()) "" else "$name "
        return if (internalsNode != null) {
            """
            |$renderedName{
            |${internalsNode.toKsonSource(indentLevel + 1, indent)}
            |}
            """.trimMargin()
        } else {
            "{}"
        }
    }
}

class ObjectInternalsNode(private val properties: List<PropertyNode>) : ValueNode {
    override fun toKsonSource(indentLevel: Int, indent: String): String {
        return properties.joinToString("\n") { it.toKsonSource(indentLevel, indent) }
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

class NumberNode(private val value: Number) : ValueNode {
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
        return indent.repeat(indentLevel) + "```" + embedTag + "\n" +
                embedContent.split("\n").joinToString("\n") { it } +
                indent.repeat(indentLevel) + "```"
    }
}
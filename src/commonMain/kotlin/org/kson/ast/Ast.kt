package org.kson.ast

interface AstNode {
    /**
     * Prints a string representation of the AST capturing its structure and content.
     * Useful for debugging and testing.
     *
     * parser todo: add type info to the output, make content rendering optional
     *              (this will facilitate concise tests on just the structure, with
     *               verbose content tests used only when needed)
     */
    fun debugPrint(indentLevel: Int = 0, indent: String = "  "): String
}

class KsonRoot(private val rootNode: AstNode) : AstNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return rootNode.debugPrint(indentLevel, indent)
    }
}

interface ValueNode : AstNode

class ObjectDefinitionNode(private val name: String = "", private val internalsNode: ObjectInternalsNode?) :
    ValueNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        val renderedName = if (name.isEmpty()) "" else "$name "
        return if (internalsNode != null) {
            """
            |$renderedName{
            |${internalsNode.debugPrint(indentLevel + 1, indent)}
            |}
            """.trimMargin()
        } else {
            "{}"
        }
    }
}

class ObjectInternalsNode(private val properties: List<PropertyNode>) : ValueNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return properties.joinToString("\n") { it.debugPrint(indentLevel, indent) }
    }

}

class PropertyNode(private val name: KeywordNode, private val value: ValueNode) : AstNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "${name.debugPrint(0)}: ${value.debugPrint(0)}"
    }
}

class ListNode(private val values: List<ValueNode>) : ValueNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        // We pad our list bracket with newlines if our list is non-empty
        val bracketPadding = if (values.isEmpty()) "" else "\n"
        return indent.repeat(indentLevel) + "[" + bracketPadding +
                values.joinToString(",\n") {
                    it.debugPrint(indentLevel + 1)
                } +
                indent.repeat(indentLevel) + bracketPadding + "]"
    }
}

interface KeywordNode : AstNode {
    val value: String
}

open class StringNode(override val value: String) : ValueNode, KeywordNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "\"" + value + "\""
    }
}

class IdentifierNode(override val value: String) : ValueNode, KeywordNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + value
    }
}

class NumberNode(private val value: Number) : ValueNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + value.toString()
    }
}

class TrueNode : ValueNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "true"
    }
}

class FalseNode : ValueNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "false"
    }
}

class NullNode : ValueNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "null"
    }
}

class EmbedBlockNode(private val embedTag: String, private val embedContent: String) : ValueNode {
    override fun debugPrint(indentLevel: Int, indent: String): String {
        return indent.repeat(indentLevel) + "```" + embedTag + "\n" +
                embedContent.split("\n").joinToString("\n") { it } +
                indent.repeat(indentLevel) + "```"
    }
}
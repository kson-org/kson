package org.kson.tooling

import org.kson.ast.*
import org.kson.parser.Token
import org.kson.parser.TokenType

/**
 * Builds [StructuralRange] lists from a KSON document's AST and token stream.
 *
 * Walks the AST recursively to emit folding ranges for all multi-line
 * structural constructs: objects, lists (bracket, angle-bracket, and dash),
 * object properties, and embed blocks. Also scans the token stream for
 * consecutive comment lines to produce comment folding ranges.
 */
internal object FoldingRangeBuilder {

    fun build(rootNode: AstNode?, tokens: List<Token>): List<StructuralRange> {
        val ranges = mutableListOf<StructuralRange>()
        if (rootNode != null) {
            collectFromAst(rootNode, ranges)
        }
        collectCommentBlocks(tokens, ranges)
        return ranges
    }

    private fun collectFromAst(node: AstNode, ranges: MutableList<StructuralRange>) {
        when (node) {
            is ObjectNode -> {
                addMultiLineRange(node, StructuralRangeKind.OBJECT, ranges)
                for (property in node.properties) {
                    collectFromAst(property, ranges)
                }
            }
            is ObjectPropertyNodeImpl -> {
                addMultiLineRange(node, StructuralRangeKind.PROPERTY, ranges)
                if (node.value is ListNode) {
                    for (element in (node.value as ListNode).elements) {
                        collectFromAst(element, ranges)
                    }
                } else {
                    collectFromAst(node.value, ranges)
                }
            }
            is ListNode -> {
                addMultiLineRange(node, StructuralRangeKind.ARRAY, ranges)
                for (element in node.elements) {
                    collectFromAst(element, ranges)
                }
            }
            is ListElementNodeImpl -> {
                collectFromAst(node.value, ranges)
            }
            is EmbedBlockNode -> {
                addMultiLineRange(node, StructuralRangeKind.EMBED, ranges)
            }
            else -> {}
        }
    }

    private fun addMultiLineRange(node: AstNode, kind: StructuralRangeKind, ranges: MutableList<StructuralRange>) {
        val location = node.location
        if (location.end.line > location.start.line) {
            ranges.add(StructuralRange(location.start.line, location.end.line, kind))
        }
    }

    /**
     * Scans the token stream for runs of consecutive COMMENT tokens and
     * emits a COMMENT range for each run that spans multiple lines.
     */
    private fun collectCommentBlocks(tokens: List<Token>, ranges: MutableList<StructuralRange>) {
        var blockStartLine = -1
        var blockEndLine = -1

        for (token in tokens) {
            if (token.tokenType == TokenType.COMMENT) {
                val line = token.lexeme.location.start.line
                if (blockStartLine < 0 || line != blockEndLine + 1) {
                    if (blockStartLine >= 0 && blockEndLine > blockStartLine) {
                        ranges.add(StructuralRange(blockStartLine, blockEndLine, StructuralRangeKind.COMMENT))
                    }
                    blockStartLine = line
                }
                blockEndLine = line
            } else if (token.tokenType != TokenType.WHITESPACE) {
                if (blockStartLine >= 0 && blockEndLine > blockStartLine) {
                    ranges.add(StructuralRange(blockStartLine, blockEndLine, StructuralRangeKind.COMMENT))
                }
                blockStartLine = -1
            }
        }

        if (blockStartLine >= 0 && blockEndLine > blockStartLine) {
            ranges.add(StructuralRange(blockStartLine, blockEndLine, StructuralRangeKind.COMMENT))
        }
    }
}

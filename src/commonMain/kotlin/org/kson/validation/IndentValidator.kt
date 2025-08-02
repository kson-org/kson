package org.kson.validation

import org.kson.ast.*
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.*

/**
 * Validates that objects and lists do not have deceptive indentation, i.e. indentation that visually implies
 * incorrect list/object nesting
 *
 * NOTE: we only validate the alignment of the "leading" indent of entries to avoid deceptive indentation.
 * Items which do not start a line, i.e. do not have an indent, are considered okay
 */
class IndentValidator {
    fun validate(ast: KsonRoot, messageSink: MessageSink) {
        if (ast is KsonRootImpl) {
            validateNode(ast.rootNode, -1, messageSink)
        }
    }

    private fun validateNode(node: KsonValueNode, previousNodeLine: Int, messageSink: MessageSink) {
        /**
         * If an object or list does not start at the first element, it is delimited,
         * so we must account for that in order to no consider something like the following as mis-aligned:
         *
         * ```
         * {x:1
         * y:2}
         * ```
         */
        val previousLine = if (node is ObjectNode && node.properties.isNotEmpty()
            && node.location.start.column != node.properties.first().location.start.column
        ) {
            node.location.start.line
        } else if (node is ListNode && node.elements.isNotEmpty()
            && node.location.start.column != node.elements.first().location.start.column
        ) {
            node.location.start.line
        } else {
            previousNodeLine
        }

        when (node) {
            is ObjectNode -> validateObject(node, previousLine, messageSink)
            is ListNode -> validateList(node, previousLine, messageSink)
            is EmbedBlockNode, is UnquotedStringNode, is QuotedStringNode,
            is NumberNode, is TrueNode, is FalseNode, is NullNode,
            is KsonValueNodeError -> {
                // No indentation validation for these elements
            }
        }
    }

    private fun validateObject(objNode: ObjectNode, previousNodeLine: Int, messageSink: MessageSink) {
        validateAlignment(
            items = objNode.properties,
            previousNodeLine,
            misalignmentMessage = OBJECT_PROPERTIES_MISALIGNED,
            messageSink
        ) { property, _ ->
            if (property is ObjectPropertyNodeImpl) {
                validateNode(property.value, property.key.location.end.line, messageSink)
            }
        }
    }

    private fun validateList(listNode: ListNode, previousNodeLine: Int, messageSink: MessageSink) {
        validateAlignment(
            items = listNode.elements,
            previousNodeLine,
            misalignmentMessage = DASH_LIST_ITEMS_MISALIGNED,
            messageSink
        ) { element, lineBeforeElem ->
            if (element is ListElementNodeImpl) {
                val value = element.value
                val prevLineNum = if (value is ObjectNode || value is ListNode) {
                    element.location.start.line
                } else {
                    lineBeforeElem
                }
                validateNode(value, prevLineNum, messageSink)
            }
        }
    }

    private fun <T : AstNode> validateAlignment(
        items: List<T>,
        previousNodeLine: Int,
        misalignmentMessage: MessageType,
        messageSink: MessageSink,
        validateChild: (T, Int) -> Unit
    ) {
        var previousItem: AstNode? = null// Recursively validate all children

        for (item in items) {
            val previousItemLine = previousItem?.location?.end?.line ?: previousNodeLine
            previousItem = item
            validateChild(item, previousItemLine)
        }

        if (items.size < 2) {
            // No alignment to check with 0 or 1 item
            return
        }

        var prevLine = previousNodeLine
        var expectedColumn: Int? = null

        // Check alignment of the indentation of all other items
        for (item in items) {
            // this item is not indented (it's trailing another value), so it has no indent to align
            if (item.location.start.line == prevLine) {
                prevLine = item.location.end.line
                continue
            } else {
                prevLine = item.location.end.line
                if (expectedColumn == null) {
                    // this is the first leading line we've seen, so it defines our target indent
                    expectedColumn = item.location.start.column
                }
            }
            val itemColumn = item.location.start.column
            if (itemColumn != expectedColumn) {
                messageSink.error(item.location.trimToFirstLine(), misalignmentMessage.create())
            }
        }
    }
}

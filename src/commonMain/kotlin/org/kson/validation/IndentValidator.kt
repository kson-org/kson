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
            validateNodeAlignment(ast.rootNode, messageSink)
            validateNodeNesting(ast.rootNode, 0,
                // Note: this message type is unused for root (minNestingColumn: 0 means all nesting is legal)
                OBJECT_PROPERTY_NESTING_ISSUE,
                messageSink)
        }
    }

    /**
     * Validate that [node] is indented to at least position [minNestingColumn], logging [messageType] when this is
     * violated.  We pass [messageType] here so that the message can be parent-aware (i.e. if we're improperly nested
     * in an object, we can have an object-specific message.  Similar for a list.)
     */
    private fun validateNodeNesting(node: KsonValueNode,
                                    minNestingColumn: Int,
                                    messageType: MessageType,
                                    messageSink: MessageSink) {
        when (node) {
            is ObjectNode -> validateObjectNodeNesting(node, minNestingColumn, messageType, messageSink)
            is ListNode -> validateListNodeNesting(node, minNestingColumn, messageType, messageSink)
            is EmbedBlockNode, is UnquotedStringNode, is QuotedStringNode,
            is NumberNode, is TrueNode, is FalseNode, is NullNode,
            is KsonValueNodeError -> {
                if (node.location.start.column < minNestingColumn) {
                    messageSink.error(node.location.trimToFirstLine(), messageType.create())
                }
            }
        }
    }

    private fun validateObjectNodeNesting(
        node: ObjectNode,
        minNestingColumn: Int,
        messageType: MessageType,
        messageSink: MessageSink,
    ) {
        node.properties.forEach { property ->
            if (property.location.start.column < minNestingColumn) {
                messageSink.error(property.location.trimToFirstLine(), messageType.create())
            }
            if (property is ObjectPropertyNodeImpl) {
                validateNodeNesting(property.value, property.key.location.start.column + 1,
                    OBJECT_PROPERTY_NESTING_ISSUE, messageSink)
            }
        }
    }

    private fun validateListNodeNesting(
        node: ListNode,
        minNestingColumn: Int,
        messageType: MessageType,
        messageSink: MessageSink,
    ) {
        node.elements.forEach { element ->
            if (element.location.start.column < minNestingColumn) {
                messageSink.error(element.location.trimToFirstLine(), messageType.create())
            }
            if (element is ListElementNodeImpl) {
                // an element with a dash has location offset before its value, so its value must sit one column deeper
                val minListNestingColumn = if (element.location.startOffset < element.value.location.startOffset) {
                    element.location.start.column + 1
                } else {
                    element.location.start.column
                }
                validateNodeNesting(element.value, minListNestingColumn, DASH_LIST_ITEMS_NESTING_ISSUE, messageSink)
            }
        }
    }
    /**
     * Validate that the leading entries of every object and list in the tree rooted at [node] line up with the
     * first entry of the object or list they belong to
     */
    private fun validateNodeAlignment(node: KsonValueNode, messageSink: MessageSink) {
        when (node) {
            is ObjectNode -> validateObjectAlignment(node, messageSink)
            is ListNode -> validateListAlignment(node, messageSink)
            is EmbedBlockNode, is UnquotedStringNode, is QuotedStringNode,
            is NumberNode, is TrueNode, is FalseNode, is NullNode,
            is KsonValueNodeError -> {
                // No indentation validation for these elements
            }
        }
    }

    private fun validateObjectAlignment(objNode: ObjectNode, messageSink: MessageSink) {
        validateAlignment(
            items = objNode.properties,
            misalignmentMessage = OBJECT_PROPERTIES_MISALIGNED,
            messageSink
        ) { property ->
            if (property is ObjectPropertyNodeImpl) {
                validateNodeAlignment(property.value, messageSink)
            }
        }
    }

    private fun validateListAlignment(listNode: ListNode, messageSink: MessageSink) {
        validateAlignment(
            items = listNode.elements,
            misalignmentMessage = DASH_LIST_ITEMS_MISALIGNED,
            messageSink
        ) { element ->
            if (element is ListElementNodeImpl) {
                validateNodeAlignment(element.value, messageSink)
            }
        }
    }

    private fun <T : AstNode> validateAlignment(
        items: List<T>,
        misalignmentMessage: MessageType,
        messageSink: MessageSink,
        validateChild: (T) -> Unit
    ) {
        // Recursively validate all children
        items.forEach(validateChild)

        if (items.size < 2) {
            // No alignment to check with 0 or 1 items
            return
        }

        var prevLine = items[0].location.end.line
        val expectedColumn = items[0].location.start.column

        // Check alignment of the indentation of all other items
        for (item in items.subList(1, items.size)) {
            // this item is not indented (it's trailing another value), so it has no indent to align
            if (item.location.start.line == prevLine) {
                prevLine = item.location.end.line
                continue
            }
            prevLine = item.location.end.line
            val itemColumn = item.location.start.column
            if (itemColumn != expectedColumn) {
                messageSink.error(item.location.trimToFirstLine(), misalignmentMessage.create())
            }
        }
    }
}

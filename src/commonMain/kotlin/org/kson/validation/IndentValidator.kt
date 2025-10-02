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
            validateNodeAlignment(ast.rootNode, -1, messageSink)
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
            is ObjectNode -> {
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
            is ListNode -> {
                node.elements.forEach { element ->
                    if (element.location.start.column < minNestingColumn) {
                        messageSink.error(element.location.trimToFirstLine(), messageType.create())
                    }

                    if (element is ListElementNodeImpl) {
                        val minListNestingColumn = if (element.location.startOffset < element.value.location.startOffset) {
                            // this list element starts before its value, i.e. has a dash, so ensure its value is nested
                            element.location.start.column + 1
                        } else {
                            // this list element has no dash, so no extra nesting is needed
                            element.location.start.column
                        }
                        validateNodeNesting(element.value, minListNestingColumn, DASH_LIST_ITEMS_NESTING_ISSUE, messageSink)
                    }
                }
            }
            is EmbedBlockNode, is UnquotedStringNode, is QuotedStringNode,
            is NumberNode, is TrueNode, is FalseNode, is NullNode,
            is KsonValueNodeError -> {
                if (node.location.start.column < minNestingColumn) {
                    messageSink.error(node.location.trimToFirstLine(), messageType.create())
                }
            }
        }
    }
    /**
     * @param node the [KsonValueNode] to validate
     * @param previousNodeLine the document line location of the previous [KsonValueNode] in this document
     *   and objects are indented so as not to appear to be part of the containing list/object
     */
    private fun validateNodeAlignment(node: KsonValueNode, previousNodeLine: Int, messageSink: MessageSink) {
        /**
         * If an object or list does not start at the first element, it is delimited,
         * so we must account for that in order to not consider something like the following as mis-aligned:
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
            is ObjectNode -> validateObjectAlignment(node, previousLine, messageSink)
            is ListNode -> validateListAlignment(node, previousLine, messageSink)
            is EmbedBlockNode, is UnquotedStringNode, is QuotedStringNode,
            is NumberNode, is TrueNode, is FalseNode, is NullNode,
            is KsonValueNodeError -> {
                // No indentation validation for these elements
            }
        }
    }

    private fun validateObjectAlignment(objNode: ObjectNode, previousNodeLine: Int, messageSink: MessageSink) {
        validateAlignment(
            items = objNode.properties,
            previousNodeLine,
            misalignmentMessage = OBJECT_PROPERTIES_MISALIGNED,
            messageSink
        ) { property, _ ->
            if (property is ObjectPropertyNodeImpl) {
                validateNodeAlignment(
                    property.value,
                    property.key.location.end.line,
                    messageSink)
            }
        }
    }

    private fun validateListAlignment(listNode: ListNode, previousNodeLine: Int, messageSink: MessageSink) {
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

                validateNodeAlignment(value, prevLineNum, messageSink)
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
        var previousItem: AstNode? = null

        // Recursively validate all children
        for (item in items) {
            val previousItemLine = previousItem?.location?.end?.line ?: previousNodeLine
            previousItem = item
            validateChild(item, previousItemLine)
        }

        if (items.size < 2) {
            // No alignment to check with 0 or 1 items
            return
        }

        var prevLine = items[0].location.end.line
        var expectedColumn = items[0].location.start.column

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

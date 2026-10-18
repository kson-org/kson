package org.kson.validation

import org.kson.ast.*
import org.kson.ast.BoundaryStyle.DELIMITED
import org.kson.ast.BoundaryStyle.PLAIN
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.*

/**
 * [IndentValidator] tests a KSON document for "deceptive" indents: visual indentation that contradicts the document's
 * actual structure/value. For instance:
 *
 * ```kson
 * teams:
 *   - name: 'Team A'
 *     members:
 *       - Alice
 *       - Bob
 *   - name: 'Team B'
 *   # ERROR: we mark `- name: 'Team B'` as deceptively indented since it _looks_ like a sibling of `- name: 'Team B'`,
 *   #        but actually parses in KSON as a sibling of `- Alice` and `- Bob`.
 *```
 *
 * The use can fix the indent to properly encode their intent, such as terminating the nested list:
 *
 * ```kson
 * teams:
 *   - name: 'Team A'
 *     members:
 *       - Alice
 *       - Bob
 *       =
 *   - name: 'Team B'
 * ```
 *
 * These checks are part of KSON's approach to accommodating both YAML's readability and JSON's rigor and generally part
 * KSON's desire to have build-in guardrails to help users successfully encode their data in a way they trust.
 *
 * This validator is comprehensive and aspires to catch all places where incorrect nesting is visually implied.
 * Note that it focuses on _disallowing_ deceptive indents rather than _enforcing_ a particular "blessed" format,
 * inspecting entries which actually have an indent, i.e. entries starting a line. These are the elements that create
 * the visual nesting or unnesting that readers perceive as structure, and hence are actively depcetive rather than just
 * messy or unclear.
 */
class IndentValidator {
    private val objectPropertiesMisaligned = DeceptiveIndentMessage(
        PLAIN_OBJECT_PROPERTIES_MISALIGNED,
        DELIMITED_OBJECT_PROPERTIES_MISALIGNED
    )
    private val listElementsMisaligned = DeceptiveIndentMessage(
        PLAIN_LIST_ELEMENTS_MISALIGNED,
        DELIMITED_LIST_ELEMENTS_MISALIGNED
    )
    private val objectPropertyNesting = DeceptiveIndentMessage(
        PLAIN_OBJECT_PROPERTY_NESTING_ISSUE,
        DELIMITED_OBJECT_PROPERTY_NESTING_ISSUE
    )
    private val listElementNesting = DeceptiveIndentMessage(
        PLAIN_LIST_ELEMENT_NESTING_ISSUE,
        DELIMITED_LIST_ELEMENT_NESTING_ISSUE
    )

    fun validate(ast: KsonRoot, messageSink: MessageSink) {
        if (ast is KsonRootImpl) {
            val rootNode = ast.rootNode
            validateNodeAlignment(rootNode, messageSink)
            // A document's root value may be indented however it likes, so we start validating inside it,
            // relative to it
            when (rootNode) {
                is ObjectNode -> validateObjectNodeNesting(
                    rootNode, 0, objectPropertyNesting.messageFor(rootNode.style), messageSink
                )
                is ListNode -> validateListNodeNesting(
                    rootNode, 0, listElementNesting.messageFor(rootNode.style), messageSink
                )
                else -> {
                    // a root that is neither an object nor a list has nothing nested inside it
                }
            }
        }
    }

    /**
     * Validate that [node] is indented to at least position [minNestingColumn], logging [nestingMessage] when this
     * is violated.
     *
     * Callers provide [nestingMessage] since they have the visibility to what context we're testing the nesting within,
     * and hence the context to craft the most helpful message in the case of a problem
     */
    private fun validateNodeNesting(node: KsonValueNode,
                                    minNestingColumn: Int,
                                    nestingMessage: MessageType,
                                    messageSink: MessageSink) {
        when (node) {
            is ObjectNode -> validateObjectNodeNesting(node, minNestingColumn, nestingMessage, messageSink)
            is ListNode -> validateListNodeNesting(node, minNestingColumn, nestingMessage, messageSink)
            is EmbedBlockNode, is UnquotedStringNode, is QuotedStringNode,
            is NumberNode, is TrueNode, is FalseNode, is NullNode,
            is KsonValueNodeError -> reportIfUnderNested(node, minNestingColumn, nestingMessage, messageSink)
        }
    }

    private fun validateObjectNodeNesting(
        node: ObjectNode,
        minNestingColumn: Int,
        nestingMessage: MessageType,
        messageSink: MessageSink,
    ) {
        reportIfUnderNested(node, minNestingColumn, nestingMessage, messageSink)

        node.properties.forEach { property ->
            if (property is ObjectPropertyNodeImpl) {
                // Ensure this property's value is indented deeper than its key
                validateNodeNesting(property.value, property.key.location.start.column + 1,
                    objectPropertyNesting.messageFor(node.style), messageSink)
            }
        }
    }

    private fun validateListNodeNesting(
        node: ListNode,
        minNestingColumn: Int,
        nestingMessage: MessageType,
        messageSink: MessageSink,
    ) {
        reportIfUnderNested(node, minNestingColumn, nestingMessage, messageSink)

        node.elements.forEach { element ->
            if (element is ListElementNodeImpl) {
                // an element with a dash has location offset before its value, so its value must sit one column deeper
                val minListNestingColumn = if (element.location.startOffset < element.value.location.startOffset) {
                    element.location.start.column + 1
                } else {
                    element.location.start.column
                }
                /**
                 * Ensure this element's value is indented deeper than its list dash
                 */
                validateNodeNesting(element.value, minListNestingColumn,
                    listElementNesting.messageFor(node.style), messageSink)
            }
        }
    }

    /**
     * Report [node] when it is indented less than [minNestingColumn] columns
     */
    private fun reportIfUnderNested(
        node: AstNode,
        minNestingColumn: Int,
        nestingMessage: MessageType,
        messageSink: MessageSink
    ) {
        /**
         * This simple check works for any node since by design its start column is not whitespace---it's where the
         * node starts visually. Indentation of sub-entries in any container is then validated in [validateNodeAlignment]
         * as an alignment concern, not a nesting concern
         */
        if (node.location.start.column < minNestingColumn) {
            messageSink.error(node.location.trimToFirstLine(), nestingMessage.create())
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
            misalignmentMessage = objectPropertiesMisaligned.messageFor(objNode.style),
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
            misalignmentMessage = listElementsMisaligned.messageFor(listNode.style),
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

    private class DeceptiveIndentMessage(
        private val inPlainSyntax: MessageType,
        private val inDelimitedSyntax: MessageType
    ) {
        /**
         * The [MessageType] to report for an entry of an object or list with this [boundaryStyle]
         */
        fun messageFor(boundaryStyle: BoundaryStyle): MessageType = when (boundaryStyle) {
            PLAIN -> inPlainSyntax
            DELIMITED -> inDelimitedSyntax
        }
    }
}

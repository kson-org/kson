package org.kson.validation

import org.kson.ast.*
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType

/**
 * Validates that objects in a KSON document do not contain duplicate keys.
 *
 * This validator traverses [KsonRoot] and checks each [ObjectNode] to ensure that
 * no [ObjectKeyNode] appears more than once within the same [ObjectNode]. Duplicate keys can lead
 * to ambiguous data interpretation and potential data loss.
 *
 * The validator will report an error for each duplicate key found, identifying
 * the location of the duplicate (not the original) and the key name.
 *
 * Example of invalid KSON that this validator catches:
 * ```
 * {
 *   key1: value1
 *   key2: value2
 *   key1: value3  // Error: Duplicate key "key1"
 * }
 * ```
 */
class DuplicateKeyValidator {
    /**
     * Validates the given AST for duplicate keys in objects.
     *
     * @param ast The root of the AST to validate
     * @param messageSink The sink to report validation errors to
     */
    fun validate(ast: KsonRoot, messageSink: MessageSink) {
        if (ast is KsonRootImpl) {
            validateNode(ast.rootNode, messageSink)
        }
    }

    /**
     * Recursively validates a node and its children for duplicate keys.
     * Only processes ObjectNode and ListNode types, as other node types
     * cannot contain duplicate keys.
     */
    private fun validateNode(node: KsonValueNode, messageSink: MessageSink) {
        when (node) {
            is ObjectNode -> validateObject(node, messageSink)
            is ListNode -> validateList(node, messageSink)
            else -> {}
        }
    }

    /**
     * Validates an object node for duplicate keys.
     *
     * Maintains a map of seen keys to detect duplicates. When a duplicate is found,
     * an error is reported at the location of the duplicate key (not the original).
     * After checking for duplicates, recursively validates any nested structures
     * within the object's property values.
     */
    private fun validateObject(objNode: ObjectNode, messageSink: MessageSink) {
        val seenKeys = mutableMapOf<String, ObjectPropertyNode>()

        objNode.properties.filterIsInstance<ObjectPropertyNodeImpl>().forEach {
            // Get the property key as string
            val keyString = if (it.key is ObjectKeyNodeImpl && it.key.key is StringNodeImpl) {
                it.key.key.stringContent
            } else {
                ""
            }

            // Check if the property has been seen before, if not the new unique property is added to seenKeys
            val existingProperty = seenKeys[keyString]
            if (existingProperty != null) {
                messageSink.error(
                    it.key.location,
                    MessageType.OBJECT_DUPLICATE_KEY.create(keyString)
                )
            } else {
                seenKeys[keyString] = it
            }

            // Recursively validate nested values
            validateNode(it.value, messageSink)
        }
    }

    /**
     * Validates all elements within a list node.
     *
     * Lists themselves cannot have duplicate keys, but they may contain
     * objects that need to be validated. This method recursively validates
     * each element in the list.
     */
    private fun validateList(listNode: ListNode, messageSink: MessageSink) {
        for (element in listNode.elements) {
            if (element is ListElementNodeImpl) {
                validateNode(element.value, messageSink)
            }
        }
    }
}
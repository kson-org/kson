package org.kson.schema.validators

import org.kson.ast.KsonList
import org.kson.ast.KsonListElement
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonArrayValidator

class UniqueItemsValidator(private val uniqueItems: Boolean) : JsonArrayValidator() {
    override fun validateArray(node: KsonList, messageSink: MessageSink) {
        if (uniqueItems && !areItemsUnique(node.elements)) {
            messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Items in this array must be unique"))
        }
    }

    /**
     * Check if all items in a list are unique using JSON Schema equality semantics.
     */
    private fun areItemsUnique(elements: List<KsonListElement>): Boolean {
        for (i in elements.indices) {
            for (j in i + 1 until elements.size) {
                if (elements[i].ksonValue == elements[j].ksonValue) {
                    return false
                }
            }
        }
        return true
    }
}

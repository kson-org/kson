package org.kson.schema.validators

import org.kson.value.KsonList
import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonArrayValidator

class UniqueItemsValidator(private val uniqueItems: Boolean) : JsonArrayValidator() {
    override fun validateArray(node: KsonList, messageSink: MessageSink) {
        if (uniqueItems && !areItemsUnique(node.elements)) {
            messageSink.error(node.location, MessageType.SCHEMA_ARRAY_ITEMS_NOT_UNIQUE.create())
        }
    }

    /**
     * Check if all items in a list are unique using JSON Schema equality semantics.
     */
    private fun areItemsUnique(elements: List<KsonValue>): Boolean {
        for (i in elements.indices) {
            for (j in i + 1 until elements.size) {
                if (elements[i] == elements[j]) {
                    return false
                }
            }
        }
        return true
    }
}

package org.kson.schema.validators

import org.kson.value.KsonList
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonArrayValidator

class MaxItemsValidator(private val maxItems: Long) : JsonArrayValidator() {
    override fun validateArray(node: KsonList, messageSink: MessageSink) {
        if (node.elements.size > maxItems) {
            messageSink.error(node.location, MessageType.SCHEMA_ARRAY_TOO_LONG.create(maxItems.toString()))
        }
    }
}

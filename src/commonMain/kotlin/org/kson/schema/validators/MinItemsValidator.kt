package org.kson.schema.validators

import org.kson.value.KsonList
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonArrayValidator

class MinItemsValidator(private val minItems: Long) : JsonArrayValidator() {
    override fun validateArray(node: KsonList, messageSink: MessageSink) {
        if (node.elements.size < minItems) {
            messageSink.error(node.location, MessageType.SCHEMA_ARRAY_TOO_SHORT.create(minItems.toString()))
        }
    }
}

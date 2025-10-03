package org.kson.schema.validators

import org.kson.value.KsonNumber
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonNumberValidator

class MinimumValidator(private val minimum: Double) : JsonNumberValidator() {
    override fun validateNumber(node: KsonNumber, messageSink: MessageSink) {
        val number = node.value.asDouble
        if (number < minimum) {
            messageSink.error(node.location, MessageType.SCHEMA_VALUE_TOO_SMALL.create(minimum.toString()))
        }
    }
}

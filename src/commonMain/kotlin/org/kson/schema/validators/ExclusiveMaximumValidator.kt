package org.kson.schema.validators

import org.kson.value.KsonNumber
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonNumberValidator

class ExclusiveMaximumValidator(private val exclusiveMaximum: Double) : JsonNumberValidator() {
    override fun validateNumber(node: KsonNumber, messageSink: MessageSink) {
        val number = node.value.asDouble
        if (number >= exclusiveMaximum) {
            messageSink.error(node.location, MessageType.SCHEMA_VALUE_TOO_LARGE_EXCLUSIVE.create(exclusiveMaximum.toString()))
        }
    }
}

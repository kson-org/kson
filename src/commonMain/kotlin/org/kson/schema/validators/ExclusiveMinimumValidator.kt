package org.kson.schema.validators

import org.kson.value.KsonNumber
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonNumberValidator

class ExclusiveMinimumValidator(private val exclusiveMinimum: Double) : JsonNumberValidator() {
    override fun validateNumber(node: KsonNumber, messageSink: MessageSink) {
        val number = node.value.asDouble
        if (number <= exclusiveMinimum) {
            messageSink.error(node.location, MessageType.SCHEMA_VALUE_TOO_SMALL_EXCLUSIVE.create(exclusiveMinimum.toString()))
        }
    }
}

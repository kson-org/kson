package org.kson.schema.validators

import org.kson.value.KsonString
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonStringValidator

class MaxLengthValidator(private val maxLength: Long) : JsonStringValidator() {
    override fun validateString(node: KsonString, messageSink: MessageSink) {
        val str = node.value
        if (countCodePoints(str) > maxLength) {
            messageSink.error(node.location, MessageType.SCHEMA_STRING_LENGTH_TOO_LONG.create(maxLength.toString()))
        }
    }
}

package org.kson.schema.validators

import org.kson.value.KsonString
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonStringValidator

class MinLengthValidator(private val minLength: Long) : JsonStringValidator() {
    override fun validateString(node: KsonString, messageSink: MessageSink) {
        val str = node.value
        minLength.let { min ->
            if (countCodePoints(str) < min) {
                messageSink.error(node.location, MessageType.SCHEMA_STRING_LENGTH_TOO_SHORT.create(min.toString()))
            }
        }
    }
}

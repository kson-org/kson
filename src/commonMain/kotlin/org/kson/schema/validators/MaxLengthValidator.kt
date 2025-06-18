package org.kson.schema.validators

import org.kson.ast.KsonString
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonStringValidator

class MaxLengthValidator(private val maxLength: Long) : JsonStringValidator() {
    override fun validateString(node: KsonString, messageSink: MessageSink) {
        val str = node.value
        if (countCodePoints(str) > maxLength) {
            messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("String length must be <= $maxLength"))
        }
    }
}

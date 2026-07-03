package org.kson.schema.validators

import org.kson.value.KsonString
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonStringValidator
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class MinLengthValidator(private val minLength: Long) : JsonStringValidator() {
    override fun validateString(node: KsonString, messageSink: MessageSink, sourceContext: SourceContext) {
        // a half-typed string hasn't reached its minimum yet — incompleteness, not a contradiction
        if (sourceContext.mode == ValidationMode.PARTIAL) return
        val str = node.value
        if (countCodePoints(str) < minLength) {
            messageSink.error(node.location, MessageType.SCHEMA_STRING_LENGTH_TOO_SHORT.create(minLength.toString()))
        }
    }
}

package org.kson.schema.validators

import org.kson.value.KsonList
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonArrayValidator
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class MinItemsValidator(private val minItems: Long) : JsonArrayValidator() {
    override fun validateArray(node: KsonList, messageSink: MessageSink, sourceContext: SourceContext) {
        // a half-typed array hasn't reached its minimum yet — incompleteness, not a contradiction
        if (sourceContext.mode == ValidationMode.PARTIAL) return
        if (node.elements.size < minItems) {
            messageSink.error(node.location, MessageType.SCHEMA_ARRAY_TOO_SHORT.create(minItems.toString()))
        }
    }
}

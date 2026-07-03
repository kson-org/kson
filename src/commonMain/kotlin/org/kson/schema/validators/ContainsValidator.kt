package org.kson.schema.validators

import org.kson.value.KsonList
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonArrayValidator
import org.kson.schema.JsonSchema
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class ContainsValidator(private val containsSchema: JsonSchema) : JsonArrayValidator() {
    override fun validateArray(node: KsonList, messageSink: MessageSink, sourceContext: SourceContext) {
        // a half-typed array may not contain the required element yet — incompleteness, not a contradiction
        if (sourceContext.mode == ValidationMode.PARTIAL) return
        val foundMatchingElement = node.elements.any { element ->
            val containsMessageSink = MessageSink()
            containsSchema.validate(element, containsMessageSink)
            !containsMessageSink.hasMessages()
        }
        if (!foundMatchingElement) {
            messageSink.error(node.location, MessageType.SCHEMA_CONTAINS_VALIDATION_FAILED.create())
        }
    }
}

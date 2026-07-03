package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class MinPropertiesValidator(private val minProperties: Long) : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink, sourceContext: SourceContext) {
        // a half-typed object hasn't reached its minimum yet — incompleteness, not a contradiction
        if (sourceContext.mode == ValidationMode.PARTIAL) return
        if (node.propertyMap.size < minProperties) {
            messageSink.error(node.location, 
                MessageType.SCHEMA_OBJECT_TOO_FEW_PROPERTIES.create(minProperties.toString())
            )
        }
    }
}

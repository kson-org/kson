package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator

class MaxPropertiesValidator(private val maxProperties: Long) : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        if (node.propertyMap.size > maxProperties) {
            messageSink.error(node.location, MessageType.SCHEMA_OBJECT_TOO_MANY_PROPERTIES.create(maxProperties.toString()))
        }
    }
}

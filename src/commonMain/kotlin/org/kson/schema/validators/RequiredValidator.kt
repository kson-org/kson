package org.kson.schema.validators

import org.kson.ast.KsonObject
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator

class RequiredValidator(private val required: List<String>) : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        val propertyNames = node.propertyMap.keys
        val missingProperties = required.filter { !propertyNames.contains(it) }
        if (missingProperties.isNotEmpty()) {
            messageSink.error(node.location, MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING.create(missingProperties.joinToString(", ")))
        }
    }
}

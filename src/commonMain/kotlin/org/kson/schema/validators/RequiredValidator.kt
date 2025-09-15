package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator

class RequiredValidator(private val required: List<KsonString>) : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        val propertyNames = node.propertyMap.keys
        val missingProperties = required.filter { !propertyNames.contains(it.value) }
        if (missingProperties.isNotEmpty()) {
            val missingPropertyNames = missingProperties.joinToString(", ") { it.value }
            messageSink.error(
                node.location.trimToFirstLine(),
                MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING.create(missingPropertyNames)
            )
        }
    }
}

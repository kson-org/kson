package org.kson.schema.validators

import org.kson.ast.KsonObject
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator

class RequiredValidator(private val required: List<String>) : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        val propertyNames = node.propertyMap.keys
        // schema todo collect missing required properties for the error message
        val valid = required.all {
            propertyNames.contains(it)
        }
        if (!valid) {
            messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("dm todo"))
        }
    }
}

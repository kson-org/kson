package org.kson.schema.validators

import org.kson.ast.KsonList
import org.kson.ast.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator

class EnumValidator(private val enum: KsonList) : JsonSchemaValidator {
    override fun validate(node: KsonValue, messageSink: MessageSink) {
        val enumValues = enum.elements.map { it.ksonValue }
        if (!enumValues.contains(node)) {
            messageSink.error(node.location, MessageType.SCHEMA_ENUM_VALUE_NOT_ALLOWED.create())
        }
    }
}

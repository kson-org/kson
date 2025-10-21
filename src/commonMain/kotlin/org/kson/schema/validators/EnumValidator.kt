package org.kson.schema.validators

import org.kson.value.KsonList
import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator

class EnumValidator(private val enum: KsonList) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val enumValues = enum.elements
        if (!enumValues.contains(ksonValue)) {
            messageSink.error(ksonValue.location, MessageType.SCHEMA_ENUM_VALUE_NOT_ALLOWED.create())
        }
    }
}

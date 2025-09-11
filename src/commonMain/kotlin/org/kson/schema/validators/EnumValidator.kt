package org.kson.schema.validators

import org.kson.KsonList
import org.kson.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator

class EnumValidator(private val enum: KsonList) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val enumMatch = enum.elements.any {
            it.dataEquals(ksonValue)
        }
        if (!enumMatch) {
            messageSink.error(ksonValue.location, MessageType.SCHEMA_ENUM_VALUE_NOT_ALLOWED.create())
        }
    }
}

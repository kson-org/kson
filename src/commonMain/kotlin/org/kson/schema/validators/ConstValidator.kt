package org.kson.schema.validators

import org.kson.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator

class ConstValidator(private val const: KsonValue) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        if (!ksonValue.dataEquals(const)) {
            messageSink.error(ksonValue.location, MessageType.SCHEMA_VALUE_NOT_EQUAL_TO_CONST.create())
        }
    }
}

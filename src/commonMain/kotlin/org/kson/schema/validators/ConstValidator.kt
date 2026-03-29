package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext

class ConstValidator(private val const: KsonValue) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        if (ksonValue != const) {
            messageSink.error(ksonValue.location, MessageType.SCHEMA_VALUE_NOT_EQUAL_TO_CONST.create(const.toDisplayString()))
        }
    }
}

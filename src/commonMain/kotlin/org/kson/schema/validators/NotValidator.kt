package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class NotValidator(private val notSchema: JsonSchema?) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        if (notSchema == null) {
            return
        }
        val notMessageSink = MessageSink()
        if(notSchema.isValid(ksonValue, notMessageSink)) {
            messageSink.error(ksonValue.location, MessageType.SCHEMA_NOT_VALIDATION_FAILED.create())
        }
    }
}

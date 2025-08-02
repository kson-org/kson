package org.kson.schema.validators

import org.kson.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class AllOfValidator(val allOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val allValid = allOf.all {
            val allOfMessageSink = MessageSink()
            it.validate(ksonValue, allOfMessageSink)
            !allOfMessageSink.hasErrors()
        }
        if (!allValid) {
            messageSink.error(ksonValue.location, MessageType.SCHEMA_ALL_OF_VALIDATION_FAILED.create())
        }
    }
}

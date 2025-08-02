package org.kson.schema.validators

import org.kson.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class AnyOfValidator(private val anyOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val anyValid = anyOf.any {
            val anyOfMessageSink = MessageSink()
            it.validate(ksonValue, anyOfMessageSink)
            // were we valid
            !anyOfMessageSink.hasErrors()
        }

        if (!anyValid) {
            messageSink.error(ksonValue.location, MessageType.SCHEMA_ANY_OF_VALIDATION_FAILED.create())
        }
    }
}

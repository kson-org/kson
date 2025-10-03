package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class OneOfValidator(private val oneOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val hasExactlyOneOf = oneOf.count {
            val oneOfMessageSink = MessageSink()
            it.isValid(ksonValue, oneOfMessageSink)
        } == 1
        if (!hasExactlyOneOf) {
            // schema todo maybe merge in some of the errors we found?
            messageSink.error(ksonValue.location, MessageType.SCHEMA_ONE_OF_VALIDATION_FAILED.create())
        }
     }
}

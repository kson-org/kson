package org.kson.schema.validators

import org.kson.ast.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class AnyOfValidator(private val anyOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(node: KsonValue, messageSink: MessageSink) {
        val anyValid = anyOf.any {
            val anyOfMessageSink = MessageSink()
            it.validate(node, anyOfMessageSink)
            // were we valid
            !anyOfMessageSink.hasErrors()
        }

        if (!anyValid) {
            messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("dm todo"))
        }
    }
}

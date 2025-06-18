package org.kson.schema.validators

import org.kson.ast.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class AllOfValidator(val allOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(node: KsonValue, messageSink: MessageSink) {
        val allValid = allOf.all {
            val allOfMessageSink = MessageSink()
            it.validate(node, allOfMessageSink)
            !allOfMessageSink.hasErrors()
        }
        if (!allValid) {
            messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("dm todo"))
        }
    }
}

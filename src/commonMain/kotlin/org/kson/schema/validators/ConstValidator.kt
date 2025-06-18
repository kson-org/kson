package org.kson.schema.validators

import org.kson.ast.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator

class ConstValidator(private val const: KsonValue) : JsonSchemaValidator {
    override fun validate(node: KsonValue, messageSink: MessageSink) {
        if (node != const) {
            messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be exactly equal to const value"))
        }
    }
}

package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class IfValidator(private val ifSchema: JsonSchema?, private val thenSchema: JsonSchema?, private val elseSchema: JsonSchema?) :
    JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        if (ifSchema == null) {
            return
        }

        val tmpMessageSink = MessageSink()
        if (ifSchema.isValid(ksonValue, tmpMessageSink)) {
            // if condition is true, run then schema if it exists
            thenSchema?.validate(ksonValue, messageSink)
        } else {
            // if condition is false, run else schema if it exists
            elseSchema?.validate(ksonValue, messageSink)
        }
    }
}

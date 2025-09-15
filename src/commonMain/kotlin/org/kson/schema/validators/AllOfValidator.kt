package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class AllOfValidator(val allOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        // log and and all error we see from this collection of schemas we must satisfy
        allOf.forEach {
            it.validate(ksonValue, messageSink)
        }
    }
}

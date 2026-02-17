package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType.SCHEMA_ANY_OF_VALIDATION_FAILED
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext

class AnyOfValidator(private val anyOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        val matchAttemptMessageSinks: MutableList<LabelledMessageSink> = mutableListOf()
        val anyValid = anyOf.any {
            val anyOfMessageSink = MessageSink()
            it.validate(ksonValue, anyOfMessageSink)
            matchAttemptMessageSinks.add(LabelledMessageSink(it.descriptionWithDefault(), anyOfMessageSink))
            // were we valid
            !anyOfMessageSink.hasMessages()
        }

        if (!anyValid) {
            reportNoSubSchemaMatchErrors(ksonValue, messageSink, matchAttemptMessageSinks, SCHEMA_ANY_OF_VALIDATION_FAILED.create())
        }
    }
}

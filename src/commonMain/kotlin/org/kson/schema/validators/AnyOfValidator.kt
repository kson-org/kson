package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType.SCHEMA_ANY_OF_VALIDATION_FAILED
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext

/**
 * `anyOf`, validated against the branches that compile.  [declared] holds every branch at its index in the `anyOf`
 * list, with null where a branch doesn't compile.
 */
internal class AnyOfValidator(internal val declared: List<JsonSchema?>) : JsonSchemaValidator {
    /** The branches that compile, in the order they are declared */
    internal val anyOf: List<JsonSchema> = declared.filterNotNull()

    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        val matchAttemptMessageSinks: MutableList<LabelledMessageSink> = mutableListOf()
        val anyValid = anyOf.any {
            val anyOfMessageSink = MessageSink()
            it.validate(ksonValue, anyOfMessageSink, sourceContext)
            matchAttemptMessageSinks.add(LabelledMessageSink(it.descriptionWithDefault(), anyOfMessageSink))
            !anyOfMessageSink.hasMessages()
        }

        if (!anyValid) {
            reportUnionMatchFailure(
                anyOf, ksonValue, messageSink, matchAttemptMessageSinks,
                SCHEMA_ANY_OF_VALIDATION_FAILED.create(), sourceContext
            )
        }
    }
}

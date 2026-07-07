package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType.SCHEMA_ANY_OF_VALIDATION_FAILED
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class AnyOfValidator(internal val anyOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        val matchAttemptMessageSinks: MutableList<LabelledMessageSink> = mutableListOf()
        /**
         * valid when at least one branch holds; in [org.kson.validation.ValidationMode.PARTIAL] mode branches are evaluated partially,
         * so a branch survives unless the document actively contradicts it
         */
        val anyValid = anyOf.any {
            val anyOfMessageSink = MessageSink()
            it.validate(ksonValue, anyOfMessageSink, sourceContext)
            matchAttemptMessageSinks.add(LabelledMessageSink(it.descriptionWithDefault(), anyOfMessageSink))
            // were we valid
            !anyOfMessageSink.hasMessages()
        }

        if (!anyValid) {
            // PARTIAL mode: a half-typed document shouldn't get union narrowing's hard errors
            // (e.g. SCHEMA_ENUM_VALUE_NOT_ALLOWED on a closed union), so skip it and
            // fall back to the plain per-branch dump — mirroring OneOfValidator.
            if (sourceContext.mode == ValidationMode.PARTIAL) {
                reportNoSubSchemaMatchErrors(ksonValue, messageSink, matchAttemptMessageSinks, SCHEMA_ANY_OF_VALIDATION_FAILED.create())
            } else {
                reportUnionMatchFailure(
                    anyOf, ksonValue, messageSink, matchAttemptMessageSinks,
                    SCHEMA_ANY_OF_VALIDATION_FAILED.create(), sourceContext
                )
            }
        }
    }
}

package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType.SCHEMA_ONE_OF_MULTIPLE_MATCHES
import org.kson.parser.messages.MessageType.SCHEMA_ONE_OF_VALIDATION_FAILED
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class OneOfValidator(internal val oneOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        val matchAttemptMessageSinks: MutableList<LabelledMessageSink> = mutableListOf()
        val matchedSchemas: MutableList<JsonSchema> = mutableListOf()

        // Unlike anyOf (which can short-circuit on the first match), oneOf must evaluate all
        // sub-schemas to detect the multiple-match case
        oneOf.forEach {
            val oneOfMessageSink = MessageSink()
            it.validate(ksonValue, oneOfMessageSink, sourceContext)
            matchAttemptMessageSinks.add(LabelledMessageSink(it.descriptionWithDefault(), oneOfMessageSink))
            if (!oneOfMessageSink.hasMessages()) {
                matchedSchemas.add(it)
            }
        }

        /**
         * Exactly-one cardinality is itself incompleteness-sensitive: a half-typed document can
         * match several branches before the disambiguating value is typed.  In [ValidationMode.PARTIAL] mode the
         * branch set is viable as long as at least one branch is not contradicted; only an
         * all-branches-contradicted document fails.
         */
        if (sourceContext.mode == ValidationMode.PARTIAL) {
            if (matchedSchemas.isEmpty()) {
                reportNoSubSchemaMatchErrors(ksonValue, messageSink, matchAttemptMessageSinks, SCHEMA_ONE_OF_VALIDATION_FAILED.create())
            }
            return
        }

        when {
            matchedSchemas.size == 1 -> { /* success */ }

            matchedSchemas.isEmpty() -> {
                reportNoSubSchemaMatchErrors(ksonValue, messageSink, matchAttemptMessageSinks, SCHEMA_ONE_OF_VALIDATION_FAILED.create())
            }

            else -> {
                val matchedDescriptions = matchedSchemas.joinToString(", ") { it.descriptionWithDefault() }
                messageSink.error(ksonValue.location.trimToFirstLine(), SCHEMA_ONE_OF_MULTIPLE_MATCHES.create(matchedDescriptions))
            }
        }
    }
}

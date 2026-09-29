package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class IfValidator(private val ifSchema: JsonSchema, private val thenSchema: JsonSchema?, private val elseSchema: JsonSchema?) :
    JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        when {
            !ifSchema.isValid(ksonValue, MessageSink(), sourceContext) ->
                elseSchema?.validate(ksonValue, messageSink, sourceContext)

            // Partial validation skips `required`, `minProperties` and the like, so the condition can hold here
            // where full validation fails it and takes the `else`.  Partial validation must accept whatever full
            // validation accepts (see [ValidationMode]), so the value passes when that `else` accepts it or is missing.
            sourceContext.mode == ValidationMode.PARTIAL &&
                !ifSchema.isValid(ksonValue, MessageSink(), sourceContext.copy(mode = ValidationMode.FULL)) &&
                elseSchema?.isValid(ksonValue, MessageSink(), sourceContext) != false -> Unit

            else -> thenSchema?.validate(ksonValue, messageSink, sourceContext)
        }
    }
}

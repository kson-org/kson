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
        branchToApply(ksonValue, sourceContext)?.validate(ksonValue, messageSink, sourceContext)
    }

    private fun branchToApply(ksonValue: KsonValue, sourceContext: SourceContext): JsonSchema? =
        when (sourceContext.mode) {
            ValidationMode.FULL -> fullValidationBranch(ksonValue, sourceContext)
            ValidationMode.PARTIAL -> partialValidationBranch(ksonValue, sourceContext)
        }

    /** The `then` if [ksonValue] satisfies the `if`, otherwise the `else`. */
    private fun fullValidationBranch(ksonValue: KsonValue, sourceContext: SourceContext): JsonSchema? =
        if (ifHolds(ksonValue, sourceContext)) thenSchema else elseSchema

    /**
     * As [fullValidationBranch], except where the `if` holds only because partial validation skips constraints
     * such as `required` (`if: {required: [kind]}` holds for a value without `kind`).  Such a value gets the
     * `else`, as under full validation, provided the `else` accepts it or is missing.
     */
    private fun partialValidationBranch(ksonValue: KsonValue, sourceContext: SourceContext): JsonSchema? = when {
        !ifHolds(ksonValue, sourceContext) -> elseSchema
        ifHolds(ksonValue, sourceContext.copy(mode = ValidationMode.FULL)) -> thenSchema
        elseSchema?.isValid(ksonValue, MessageSink(), sourceContext) != false -> elseSchema
        else -> thenSchema
    }

    private fun ifHolds(ksonValue: KsonValue, sourceContext: SourceContext): Boolean =
        ifSchema.isValid(ksonValue, MessageSink(), sourceContext)
}

package org.kson.validation

/**
 * Whether validation must hold for the document as-is ([FULL]), or only requires that the document
 * does not yet actively contradict the schema ([PARTIAL]).
 *
 * [PARTIAL] is the "draft" mode: an incomplete document should still be
 * considered a viable match for a schema branch as long as nothing it has committed so far violates a
 * value constraint.  Incompleteness alone (a missing required property, an array/string/object that
 * hasn't reached its declared minimum) never fails in [PARTIAL] mode; a present value of the wrong
 * type/const/enum/pattern still does.
 */
enum class ValidationMode { FULL, PARTIAL }

/**
 * Context information available during validation.
 * This allows access to metadata about the document being validated,
 * such as its filepath, which can be used to determine which validation rules to apply.
 *
 * Propagated through all combinators ($ref, allOf, anyOf, oneOf, if/then/else) and
 * property/item recursion, so nested branches validate in the same mode.
 *
 * @param filepath The filepath of the document being validated, if available
 * @param mode whether this is a [ValidationMode.FULL] validation or a [ValidationMode.PARTIAL]
 *   (draft) validation; defaults to [ValidationMode.FULL] so all ordinary validation is unaffected
 */
data class SourceContext(
    val filepath: String? = null,
    val mode: ValidationMode = ValidationMode.FULL
)
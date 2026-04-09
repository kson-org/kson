package org.kson.validation

/**
 * Context information available during validation.
 * This allows access to metadata about the document being validated,
 * such as its filepath, which can be used to determine which validation rules to apply.
 *
 * Note: sourceContext is passed to root-level validators from [KsonCore.parseToAst],
 * but nested schema validators (allOf, anyOf, oneOf, $ref, if/then/else) do not
 * yet propagate it. When a nested validator needs this context, propagation should
 * be added to the recursive validate calls in those combinators.
 *
 * @param filepath The filepath of the document being validated, if available
 */
data class SourceContext(
    val filepath: String? = null
)
package org.kson.validation

/**
 * Context information available during validation.
 * This allows access to metadata about the document being validated,
 * such as its filepath, which can be used to determine which validation rules to apply.
 *
 * Note: Currently sourceContext is not propagated to nested schema validators
 * as no validators consume it yet. When a validator needs this context,
 * propagation should be added to the relevant call sites.
 *
 * @param filepath The filepath of the document being validated, if available
 */
data class SourceContext(
    val filepath: String? = null
)
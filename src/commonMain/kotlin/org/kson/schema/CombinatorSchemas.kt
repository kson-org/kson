package org.kson.schema

import org.kson.ast.KsonValue

/**
 * Schema that requires all subschemas to be valid.
 */
data class AllOfSchema(
  val subschemas: List<JsonSchema>,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult {
    val errors = mutableListOf<String>()
    
    subschemas.forEachIndexed { index, schema ->
      when (val result = schema.validate(node)) {
        is ValidationResult.Invalid -> errors.addAll(
          result.errors.map { "Schema $index: $it" }
        )
        ValidationResult.Valid -> {}
      }
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}

/**
 * Schema that requires at least one subschema to be valid.
 */
data class AnyOfSchema(
  val subschemas: List<JsonSchema>,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult {
    val allErrors = mutableListOf<String>()
    
    subschemas.forEachIndexed { index, schema ->
      when (val result = schema.validate(node)) {
        ValidationResult.Valid -> return ValidationResult.Valid
        is ValidationResult.Invalid -> allErrors.addAll(
          result.errors.map { "Schema $index: $it" }
        )
      }
    }
    
    return ValidationResult.Invalid(
      listOf("None of the schemas matched:") + allErrors
    )
  }
}

/**
 * Schema that requires exactly one subschema to be valid.
 */
data class OneOfSchema(
  val subschemas: List<JsonSchema>,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult {
    var validCount = 0
    val allErrors = mutableListOf<String>()
    
    subschemas.forEachIndexed { index, schema ->
      when (val result = schema.validate(node)) {
        ValidationResult.Valid -> validCount++
        is ValidationResult.Invalid -> allErrors.addAll(
          result.errors.map { "Schema $index: $it" }
        )
      }
    }
    
    return when {
      validCount == 0 -> ValidationResult.Invalid(
        listOf("None of the schemas matched:") + allErrors
      )
      validCount > 1 -> ValidationResult.Invalid(
        listOf("Multiple schemas matched ($validCount). Expected exactly one.")
      )
      else -> ValidationResult.Valid
    }
  }
}

/**
 * Schema that requires the subschema to be invalid.
 */
data class NotSchema(
  val schema: JsonSchema,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult =
    when (schema.validate(node)) {
      ValidationResult.Valid -> ValidationResult.Invalid(
        listOf("Schema should not match")
      )
      is ValidationResult.Invalid -> ValidationResult.Valid
    }
}

/**
 * Schema for conditional validation.
 */
data class IfThenElseSchema(
  val ifSchema: JsonSchema,
  val thenSchema: JsonSchema? = null,
  val elseSchema: JsonSchema? = null,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult {
    return when (ifSchema.validate(node)) {
      ValidationResult.Valid -> {
        thenSchema?.validate(node) ?: ValidationResult.Valid
      }
      is ValidationResult.Invalid -> {
        elseSchema?.validate(node) ?: ValidationResult.Valid
      }
    }
  }
}

/**
 * Schema for references to other schemas.
 */
data class RefSchema(
  val ref: String,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult =
    error("\$ref must be resolved before validation")
} 

package org.kson.schema
import org.kson.ast.KsonValue

/**
 * Base interface for all JSON Schema types.
 */
sealed interface JsonSchema {
  val title: String?
  val description: String?
  val default: KsonValue?
  val definitions: Map<String, JsonSchema>?

  /**
   * Validates a [KsonValue] node against this schema
   */
  fun validate(node: KsonValue): ValidationResult
}

/**
 * Result of schema validation.
 */
sealed interface ValidationResult {
  /** Indicates successful validation */
  data object Valid : ValidationResult
  
  /** Indicates validation failure with list of error messages */
  data class Invalid(val errors: List<String>) : ValidationResult
}

/**
 * Models the additionalProperties keyword behavior in object schemas.
 */
sealed interface AdditionalProperties {
  /** Additional properties are allowed */
  data object Allowed : AdditionalProperties
  
  /** Additional properties are forbidden */
  data object Forbidden : AdditionalProperties
  
  /** Additional properties must validate against the given schema */
  data class Schema(val schema: JsonSchema) : AdditionalProperties
}

/**
 * Models the additionalItems keyword behavior in array schemas.
 */
sealed interface AdditionalItems {
  /** Additional items are allowed */
  data object Allowed : AdditionalItems
  
  /** Additional items are forbidden */
  data object Forbidden : AdditionalItems
  
  /** Additional items must validate against the given schema */
  data class Schema(val schema: JsonSchema) : AdditionalItems
} 

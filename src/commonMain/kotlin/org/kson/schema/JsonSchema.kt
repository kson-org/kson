package org.kson.schema
import org.kson.ast.KsonValue
import org.kson.parser.MessageSink

/**
 * Base interface for all JSON Schema types.
 */
sealed interface JsonSchema {
  val title: String?
  val description: String?
  val default: KsonValue?
  val definitions: Map<String, JsonSchema>?

  /**
   * Validates a [KsonValue] node against this schema, logging any validation errors to the [messageSink]
   */
  fun validate(node: KsonValue, messageSink: MessageSink)
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

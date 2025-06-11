package org.kson.schema

import org.kson.ast.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType.*

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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    subschemas.forEachIndexed { index, schema ->
      schema.validate(node, messageSink)
    }
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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    subschemas.forEach { schema ->
      val tempMessageSink = MessageSink()
      schema.validate(node, tempMessageSink)
      if (!tempMessageSink.hasErrors()) {
        // At least one schema matched, validation succeeds
        return
      }
    }
    
    // If we get here, none of the schemas matched
    messageSink.error(node.location, SCHEMA_VALIDATION_ERROR.create("None of the schemas matched"))
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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    var validCount = 0
    
    subschemas.forEach { schema ->
      val tempMessageSink = MessageSink()
      schema.validate(node, tempMessageSink)
      if (!tempMessageSink.hasErrors()) {
        validCount++
      }
    }
    
    when {
      validCount == 0 -> {
        messageSink.error(node.location, SCHEMA_VALIDATION_ERROR.create("None of the schemas matched"))
      }
      // must have exactly one valid schema, not more
      validCount > 1 -> {
        messageSink.error(node.location, SCHEMA_VALIDATION_ERROR.create("Multiple schemas matched ($validCount). Expected exactly one."))
      }
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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    val tempMessageSink = MessageSink()
    schema.validate(node, tempMessageSink)
    if (!tempMessageSink.hasErrors()) {
      messageSink.error(node.location, SCHEMA_VALIDATION_ERROR.create("Schema should not match"))
    }
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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    val tempMessageSink = MessageSink()
    ifSchema.validate(node, tempMessageSink)
    if (!tempMessageSink.hasErrors()) {
      // If condition passed, apply then schema if present
      thenSchema?.validate(node, messageSink)
    } else {
      // If condition failed, apply else schema if present
      elseSchema?.validate(node, messageSink)
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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    messageSink.error(node.location, SCHEMA_VALIDATION_ERROR.create("\$ref must be resolved before validation"))
  }
} 

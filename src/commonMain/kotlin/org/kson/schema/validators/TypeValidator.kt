package org.kson.schema.validators

import org.kson.ast.*
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator
import org.kson.schema.asSchemaInteger

class TypeValidator(private val allowedTypes: List<String>) : JsonSchemaValidator {
  constructor(type: String) : this(listOf(type))

  override fun validate(node: KsonValue, messageSink: MessageSink) {
    val nodeType = when (node) {
      is KsonBoolean -> "boolean"
      is KsonNull -> "null"
      is KsonNumber -> {
        if (asSchemaInteger(node) != null) {
          "integer"
        } else {
          "number"
        }
      }
      is KsonString -> "string"
      is KsonList -> "array"
      is KsonObject -> "object"
      else -> "unknown"
    }
    
    if (!allowedTypes.contains(nodeType)
      // if our node is an integer, this type is valid if the more-general "number" is an allowedType
      && !(nodeType == "integer" && allowedTypes.contains("number"))) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected one of: ${allowedTypes.joinToString()}, but got: $nodeType"))
    }
  }
}

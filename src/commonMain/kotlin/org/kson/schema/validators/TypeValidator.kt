package org.kson.schema.validators

import org.kson.ast.*
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.asSchemaInteger

class TypeValidator(private val allowedTypes: List<String>) {
  constructor(type: String) : this(listOf(type))

  fun validate(node: KsonValue, messageSink: MessageSink): Boolean {
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
      messageSink.error(node.location, MessageType.SCHEMA_VALUE_TYPE_MISMATCH.create(allowedTypes.joinToString(), nodeType))
      return false
    }

    return true
  }
}

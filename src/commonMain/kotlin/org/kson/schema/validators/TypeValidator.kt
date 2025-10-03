package org.kson.schema.validators

import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.asSchemaInteger
import org.kson.value.*

/**
 * Json Schema `type:` validator
 */
class TypeValidator(private val allowedTypes: List<String>) {
  constructor(type: String) : this(listOf(type))

  /**
   * Validates whether the given [ksonValue] is valid according to this [TypeValidator].
   * If [ksonValue] is invalid, validation errors are written to [messageSink] and this method returns false
   *
   * @return true if [ksonValue] is valid, [false otherwise]
   */
  fun validate(ksonValue: KsonValue, messageSink: MessageSink): Boolean {
    val nodeType = when (ksonValue) {
      is KsonBoolean -> "boolean"
      is KsonNull -> "null"
      is KsonNumber -> {
        if (asSchemaInteger(ksonValue) != null) {
          "integer"
        } else {
          "number"
        }
      }
      is KsonString -> "string"
      is KsonList -> "array"
      is KsonObject -> "object"
      is EmbedBlock -> "object"
    }
    
    if (!allowedTypes.contains(nodeType)
      // if our node is an integer, this type is valid if the more-general "number" is an allowedType
      && !(nodeType == "integer" && allowedTypes.contains("number"))) {
      messageSink.error(ksonValue.location, MessageType.SCHEMA_VALUE_TYPE_MISMATCH.create(allowedTypes.joinToString(), nodeType))
      return false
    }

    return true
  }
}

package org.kson.schema

import org.kson.ast.*
import org.kson.parser.NumberParser
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType

/**
 * Schema for boolean values.
 */
data class BooleanSchema(
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (node !is KsonBoolean) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected boolean"))
    }
  }
}

/**
 * Schema for null values.
 */
data class NullSchema(
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (node !is KsonNull) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected null"))
    }
  }
}

/**
 * Schema for const values - validates that the value is exactly equal to a specific constant.
 */
data class ConstSchema(
  val constValue: KsonValue,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (!valuesEqual(node, constValue)) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be exactly equal to const value"))
    }
  }

  /**
   * JSON Schema equality comparison that handles special cases for numbers.
   * In JSON Schema, integer and decimal representations of the same value are considered equal.
   */
  private fun valuesEqual(a: KsonValue, b: KsonValue): Boolean {
    return when {
      // Same type comparison
      a::class == b::class -> when (a) {
        is KsonString -> a.value == (b as KsonString).value
        is KsonBoolean -> a.value == (b as KsonBoolean).value
        is KsonNull -> true  // Both are null
        is KsonNumber -> numbersEqual(a, b as KsonNumber)
        is KsonObject -> objectsEqual(a, b as KsonObject)
        is KsonList -> listsEqual(a, b as KsonList)
        else -> false
      }
      // Special case: Numbers can be equal across integer/decimal representations
      a is KsonNumber && b is KsonNumber -> numbersEqual(a, b)
      else -> false
    }
  }

  private fun numbersEqual(a: KsonNumber, b: KsonNumber): Boolean {
    val aValue = when (a.value) {
      is NumberParser.ParsedNumber.Integer -> a.value.value.toDouble()
      is NumberParser.ParsedNumber.Decimal -> a.value.value
    }
    val bValue = when (b.value) {
      is NumberParser.ParsedNumber.Integer -> b.value.value.toDouble()
      is NumberParser.ParsedNumber.Decimal -> b.value.value
    }
    return aValue == bValue
  }

  private fun objectsEqual(a: KsonObject, b: KsonObject): Boolean {
    val aProps = a.propertyMap
    val bProps = b.propertyMap
    
    if (aProps.size != bProps.size) return false
    
    return aProps.all { (key, value) ->
      bProps[key]?.let { valuesEqual(value, it) } ?: false
    }
  }

  private fun listsEqual(a: KsonList, b: KsonList): Boolean {
    if (a.elements.size != b.elements.size) return false
    
    return a.elements.zip(b.elements).all { (aElement, bElement) ->
      valuesEqual(aElement.ksonValue, bElement.ksonValue)
    }
  }
}

/**
 * Schema for number values.
 */
data class NumberSchema(
  val minimum: Double? = null,
  val maximum: Double? = null,
  val multipleOf: Double? = null,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (node !is KsonNumber) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected number"))
      return
    }

    val number = when (val value = node.value) {
      is NumberParser.ParsedNumber.Decimal -> value.value
      is NumberParser.ParsedNumber.Integer -> value.value.toDouble()
    }
    
    minimum?.let { min ->
      if (number < min) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be >= $min"))
      }
    }
    
    maximum?.let { max ->
      if (number > max) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be <= $max"))
      }
    }
    
    multipleOf?.let { multiple ->
      if (multiple != 0.0) {
        val remainder = number % multiple
        val epsilon = 1e-10 * kotlin.math.max(kotlin.math.abs(number), kotlin.math.abs(multiple))
        if (kotlin.math.abs(remainder) > epsilon && kotlin.math.abs(remainder - multiple) > epsilon) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be multiple of $multiple"))
        }
      }
    }
  }
}

/**
 * Schema for integer values.
 */
data class IntegerSchema(
  val minimum: Long? = null,
  val maximum: Long? = null,
  val multipleOf: Long? = null,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (node !is KsonNumber) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected integer"))
      return
    }

    val number = when(node.value) {
      is NumberParser.ParsedNumber.Integer -> {
        node.value.value
      }
      is NumberParser.ParsedNumber.Decimal -> {
        if (node.value.asString.matches(allZerosDecimalRegex)) {
          node.value.value.toLong()
        } else {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected integer"))
          return
        }
      }
    }
    
    minimum?.let { min ->
      if (number < min) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be >= $min"))
      }
    }
    
    maximum?.let { max ->
      if (number > max) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be <= $max"))
      }
    }
    
    multipleOf?.let { multiple ->
      if (multiple != 0L && (number % multiple) != 0L) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be multiple of $multiple"))
      }
    }
  }
}

/**
 * Schema for string values.
 */
data class StringSchema(
  val minLength: Int? = null,
  val maxLength: Int? = null,
  val pattern: Regex? = null,
  val enum: Set<String>? = null,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (node !is KsonString) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected string"))
      return
    }
    
    val str = node.value
    
    minLength?.let { min ->
      if (countCodePoints(str) < min) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("String length must be >= $min"))
      }
    }
    
    maxLength?.let { max ->
      if (countCodePoints(str) > max) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("String length must be <= $max"))
      }
    }
    
    pattern?.let { regex ->
      if (!regex.containsMatchIn(str)) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("String must match pattern: ${regex.pattern}"))
      }
    }
    
    enum?.let { values ->
      if (!values.contains(str)) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("String must be one of: ${values.joinToString()}"))
      }
    }
  }
  
  /**
   * Count Unicode code points in a string, not UTF-16 code units.
   * This is required by JSON Schema specification.
   */
  private fun countCodePoints(str: String): Int {
    var count = 0
    var i = 0
    while (i < str.length) {
      val char = str[i]
      if (char.isHighSurrogate() && i + 1 < str.length && str[i + 1].isLowSurrogate()) {
        // This is a surrogate pair, count as one code point
        i += 2
      } else {
        // Regular character, count as one code point
        i += 1
      }
      count++
    }
    return count
  }
}

// cached regex for testing if all the digits after the decimal are zero in a decimal string
private val allZerosDecimalRegex = Regex(".*\\.0*")

/**
 * Schema that accepts any value (used for empty schemas).
 */
data class UniversalSchema(
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    // Always valid, nothing to do here
  }
}

/**
 * Schema that always accepts any value (equivalent to schema `true`).
 */
data class TrueSchema(
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    // Always valid, nothing to do here
  }
}

/**
 * Schema that always rejects any value (equivalent to schema `false`).
 */
data class FalseSchema(
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Schema always fails"))
  }
}

/**
 * Schema for enum values - validates that the value is exactly equal to one of the specified values.
 */
data class EnumSchema(
  val enumValues: Set<KsonValue>,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (!enumValues.any { valuesEqual(node, it) }) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be one of the enum values"))
    }
  }

  /**
   * JSON Schema equality comparison that handles special cases for numbers.
   * In JSON Schema, integer and decimal representations of the same value are considered equal.
   */
  private fun valuesEqual(a: KsonValue, b: KsonValue): Boolean {
    return when {
      // Same type comparison
      a::class == b::class -> when (a) {
        is KsonString -> a.value == (b as KsonString).value
        is KsonBoolean -> a.value == (b as KsonBoolean).value
        is KsonNull -> true  // Both are null
        is KsonNumber -> numbersEqual(a, b as KsonNumber)
        is KsonObject -> objectsEqual(a, b as KsonObject)
        is KsonList -> listsEqual(a, b as KsonList)
        else -> false
      }
      // Special case: Numbers can be equal across integer/decimal representations
      a is KsonNumber && b is KsonNumber -> numbersEqual(a, b)
      else -> false
    }
  }

  private fun numbersEqual(a: KsonNumber, b: KsonNumber): Boolean {
    val aValue = when (a.value) {
      is NumberParser.ParsedNumber.Integer -> a.value.value.toDouble()
      is NumberParser.ParsedNumber.Decimal -> a.value.value
    }
    val bValue = when (b.value) {
      is NumberParser.ParsedNumber.Integer -> b.value.value.toDouble()
      is NumberParser.ParsedNumber.Decimal -> b.value.value
    }
    return aValue == bValue
  }

  private fun objectsEqual(a: KsonObject, b: KsonObject): Boolean {
    val aProps = a.propertyMap
    val bProps = b.propertyMap
    
    if (aProps.size != bProps.size) return false
    
    return aProps.all { (key, value) ->
      bProps[key]?.let { valuesEqual(value, it) } ?: false
    }
  }

  private fun listsEqual(a: KsonList, b: KsonList): Boolean {
    if (a.elements.size != b.elements.size) return false
    
    return a.elements.zip(b.elements).all { (aElement, bElement) ->
      valuesEqual(aElement.ksonValue, bElement.ksonValue)
    }
  }
}

/**
 * Schema that applies constraints to appropriate types without enforcing the type itself.
 * Used when constraints are present without an explicit type declaration.
 */
data class ConstraintSchema(
  // Number constraints
  val minimum: Double? = null,
  val maximum: Double? = null,
  val multipleOf: Double? = null,
  val exclusiveMinimum: Double? = null,
  val exclusiveMaximum: Double? = null,
  // String constraints
  val minLength: Int? = null,
  val maxLength: Int? = null,
  val pattern: Regex? = null,
  // Array constraints
  val minItems: Int? = null,
  val maxItems: Int? = null,
  val uniqueItems: Boolean? = null,
  // Object constraints
  val minProperties: Int? = null,
  val maxProperties: Int? = null,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    // Apply number constraints only to numbers
    if (node is KsonNumber) {
      val number = when (val value = node.value) {
        is NumberParser.ParsedNumber.Decimal -> value.value
        is NumberParser.ParsedNumber.Integer -> value.value.toDouble()
      }
      
      minimum?.let { min ->
        if (number < min) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be >= $min"))
        }
      }
      
      maximum?.let { max ->
        if (number > max) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be <= $max"))
        }
      }
      
      exclusiveMinimum?.let { min ->
        if (number <= min) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be > $min"))
        }
      }
      
      exclusiveMaximum?.let { max ->
        if (number >= max) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be < $max"))
        }
      }
      
      multipleOf?.let { multiple ->
        if (multiple != 0.0) {
          val remainder = number % multiple
          val epsilon = 1e-10 * kotlin.math.max(kotlin.math.abs(number), kotlin.math.abs(multiple))
          if (kotlin.math.abs(remainder) > epsilon && kotlin.math.abs(remainder - multiple) > epsilon) {
            messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Value must be multiple of $multiple"))
          }
        }
      }
    }
    
    // Apply string constraints only to strings
    if (node is KsonString) {
      val str = node.value
      
      minLength?.let { min ->
        if (countCodePoints(str) < min) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("String length must be >= $min"))
        }
      }
      
      maxLength?.let { max ->
        if (countCodePoints(str) > max) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("String length must be <= $max"))
        }
      }
      
      pattern?.let { regex ->
        if (!regex.containsMatchIn(str)) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("String must match pattern: ${regex.pattern}"))
        }
      }
    }
    
    // Apply array constraints only to arrays
    if (node is KsonList) {
      val listElements = node.elements
      
      minItems?.let { min ->
        if (listElements.size < min) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Array length must be >= $min"))
        }
      }
      
      maxItems?.let { max ->
        if (listElements.size > max) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Array length must be <= $max"))
        }
      }
      
      uniqueItems?.let { unique ->
        if (unique && !areItemsUnique(listElements)) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Array items must be unique"))
        }
      }
    }
    
    // Apply object constraints only to objects
    if (node is KsonObject) {
      val objectProperties = node.propertyMap
      
      minProperties?.let { min ->
        if (objectProperties.size < min) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Object must have >= $min properties"))
        }
      }
      
      maxProperties?.let { max ->
        if (objectProperties.size > max) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Object must have <= $max properties"))
        }
      }
    }
  }
  
  /**
   * Count Unicode code points in a string, not UTF-16 code units.
   * This is required by JSON Schema specification.
   */
  private fun countCodePoints(str: String): Int {
    var count = 0
    var i = 0
    while (i < str.length) {
      val char = str[i]
      if (char.isHighSurrogate() && i + 1 < str.length && str[i + 1].isLowSurrogate()) {
        // This is a surrogate pair, count as one code point
        i += 2
      } else {
        // Regular character, count as one code point
        i += 1
      }
      count++
    }
    return count
  }
  
  /**
   * Check if all items in a list are unique using JSON Schema equality semantics.
   */
  private fun areItemsUnique(elements: List<org.kson.ast.KsonListElement>): Boolean {
    for (i in elements.indices) {
      for (j in i + 1 until elements.size) {
        if (valuesEqual(elements[i].ksonValue, elements[j].ksonValue)) {
          return false
        }
      }
    }
    return true
  }

  /**
   * JSON Schema equality comparison that handles special cases for numbers.
   * In JSON Schema, integer and decimal representations of the same value are considered equal.
   */
  private fun valuesEqual(a: KsonValue, b: KsonValue): Boolean {
    return when {
      // Same type comparison
      a::class == b::class -> when (a) {
        is KsonString -> a.value == (b as KsonString).value
        is KsonBoolean -> a.value == (b as KsonBoolean).value
        is KsonNull -> true  // Both are null
        is KsonNumber -> numbersEqual(a, b as KsonNumber)
        is KsonObject -> objectsEqual(a, b as KsonObject)
        is KsonList -> listsEqual(a, b as KsonList)
        else -> false
      }
      // Special case: Numbers can be equal across integer/decimal representations
      a is KsonNumber && b is KsonNumber -> numbersEqual(a, b)
      else -> false
    }
  }

  private fun numbersEqual(a: KsonNumber, b: KsonNumber): Boolean {
    val aValue = when (a.value) {
      is NumberParser.ParsedNumber.Integer -> a.value.value.toDouble()
      is NumberParser.ParsedNumber.Decimal -> a.value.value
    }
    val bValue = when (b.value) {
      is NumberParser.ParsedNumber.Integer -> b.value.value.toDouble()
      is NumberParser.ParsedNumber.Decimal -> b.value.value
    }
    return aValue == bValue
  }

  private fun objectsEqual(a: org.kson.ast.KsonObject, b: org.kson.ast.KsonObject): Boolean {
    val aProps = a.propertyMap
    val bProps = b.propertyMap
    
    if (aProps.size != bProps.size) return false
    
    return aProps.all { (key, value) ->
      bProps[key]?.let { valuesEqual(value, it) } ?: false
    }
  }

  private fun listsEqual(a: org.kson.ast.KsonList, b: org.kson.ast.KsonList): Boolean {
    if (a.elements.size != b.elements.size) return false
    
    return a.elements.zip(b.elements).all { (aElement, bElement) ->
      valuesEqual(aElement.ksonValue, bElement.ksonValue)
    }
  }
}

/**
 * Schema that validates against multiple allowed types.
 */
data class MultipleTypeSchema(
  val allowedTypes: Set<String>,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    val nodeType = when (node) {
      is KsonBoolean -> "boolean"
      is KsonNull -> "null"
      is KsonNumber -> {
        // Check if it's an integer (no decimal part)
        when (node.value) {
          is NumberParser.ParsedNumber.Integer -> "integer"
          is NumberParser.ParsedNumber.Decimal -> {
            if (node.value.asString.matches(allZerosDecimalRegex)) {
              "integer" // 1.0 is considered an integer
            } else {
              "number"
            }
          }
        }
      }
      is KsonString -> "string"
      is KsonList -> "array"
      is KsonObject -> "object"
      else -> "unknown"
    }
    
    if (!allowedTypes.contains(nodeType) && !(nodeType == "integer" && allowedTypes.contains("number"))) {
      messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected one of: ${allowedTypes.joinToString()}, but got: $nodeType"))
    }
  }
}

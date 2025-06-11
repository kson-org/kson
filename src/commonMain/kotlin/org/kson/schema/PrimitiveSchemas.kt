package org.kson.schema

import org.kson.ast.*
import org.kson.parser.NumberParser

/**
 * Schema for boolean values.
 */
data class BooleanSchema(
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult =
    if (node is KsonBoolean) ValidationResult.Valid
    else ValidationResult.Invalid(listOf("Expected boolean"))
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
  override fun validate(node: KsonValue): ValidationResult =
    if (node is KsonNull) ValidationResult.Valid
    else ValidationResult.Invalid(listOf("Expected null"))
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
  override fun validate(node: KsonValue): ValidationResult {
    if (valuesEqual(node, constValue)) {
      return ValidationResult.Valid
    } else {
      return ValidationResult.Invalid(listOf("Value must be exactly equal to const value"))
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
  override fun validate(node: KsonValue): ValidationResult {
    if (node !is KsonNumber) {
      return ValidationResult.Invalid(listOf("Expected number"))
    }

    val errors = mutableListOf<String>()
    val number = when (val value = node.value) {
      is NumberParser.ParsedNumber.Decimal -> value.value
      is NumberParser.ParsedNumber.Integer -> value.value.toDouble()
    }
    
    minimum?.let { min ->
      if (number < min) errors.add("Value must be >= $min")
    }
    
    maximum?.let { max ->
      if (number > max) errors.add("Value must be <= $max")
    }
    
    multipleOf?.let { multiple ->
      if (multiple != 0.0) {
        val remainder = number % multiple
        val epsilon = 1e-10 * kotlin.math.max(kotlin.math.abs(number), kotlin.math.abs(multiple))
        if (kotlin.math.abs(remainder) > epsilon && kotlin.math.abs(remainder - multiple) > epsilon) {
          errors.add("Value must be multiple of $multiple")
        }
      }
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
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
  override fun validate(node: KsonValue): ValidationResult {
    if (node !is KsonNumber) {
      return ValidationResult.Invalid(listOf("Expected integer"))
    }

    val number = when(node.value) {
      is NumberParser.ParsedNumber.Integer -> {
        node.value.value
      }
      is NumberParser.ParsedNumber.Decimal -> {
        if (node.value.asString.matches(allZerosDecimalRegex)) {
          node.value.value.toLong()
        } else {
          return ValidationResult.Invalid(listOf("Expected integer"))
        }
      }
    }
    
    val errors = mutableListOf<String>()
    
    minimum?.let { min ->
      if (number < min) errors.add("Value must be >= $min")
    }
    
    maximum?.let { max ->
      if (number > max) errors.add("Value must be <= $max")
    }
    
    multipleOf?.let { multiple ->
      if (multiple != 0L && (number % multiple) != 0L) errors.add("Value must be multiple of $multiple")
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
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
  override fun validate(node: KsonValue): ValidationResult {
    if (node !is KsonString) {
      return ValidationResult.Invalid(listOf("Expected string"))
    }
    
    val errors = mutableListOf<String>()
    val str = node.value
    
    minLength?.let { min ->
      if (countCodePoints(str) < min) errors.add("String length must be >= $min")
    }
    
    maxLength?.let { max ->
      if (countCodePoints(str) > max) errors.add("String length must be <= $max")
    }
    
    pattern?.let { regex ->
      if (!regex.containsMatchIn(str)) errors.add("String must match pattern: ${regex.pattern}")
    }
    
    enum?.let { values ->
      if (!values.contains(str)) errors.add("String must be one of: ${values.joinToString()}")
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
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
  override fun validate(node: KsonValue): ValidationResult = ValidationResult.Valid
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
  override fun validate(node: KsonValue): ValidationResult = ValidationResult.Valid
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
  override fun validate(node: KsonValue): ValidationResult = ValidationResult.Invalid(listOf("Schema always fails"))
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
  override fun validate(node: KsonValue): ValidationResult {
    if (enumValues.any { valuesEqual(node, it) }) {
      return ValidationResult.Valid
    } else {
      return ValidationResult.Invalid(listOf("Value must be one of the enum values"))
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
  override fun validate(node: KsonValue): ValidationResult {
    val errors = mutableListOf<String>()
    
    // Apply number constraints only to numbers
    if (node is KsonNumber) {
      val number = when (val value = node.value) {
        is NumberParser.ParsedNumber.Decimal -> value.value
        is NumberParser.ParsedNumber.Integer -> value.value.toDouble()
      }
      
      minimum?.let { min ->
        if (number < min) errors.add("Value must be >= $min")
      }
      
      maximum?.let { max ->
        if (number > max) errors.add("Value must be <= $max")
      }
      
      exclusiveMinimum?.let { min ->
        if (number <= min) errors.add("Value must be > $min")
      }
      
      exclusiveMaximum?.let { max ->
        if (number >= max) errors.add("Value must be < $max")
      }
      
      multipleOf?.let { multiple ->
        if (multiple != 0.0) {
          val remainder = number % multiple
          val epsilon = 1e-10 * kotlin.math.max(kotlin.math.abs(number), kotlin.math.abs(multiple))
          if (kotlin.math.abs(remainder) > epsilon && kotlin.math.abs(remainder - multiple) > epsilon) {
            errors.add("Value must be multiple of $multiple")
          }
        }
      }
    }
    
    // Apply string constraints only to strings
    if (node is KsonString) {
      val str = node.value
      
      minLength?.let { min ->
        if (countCodePoints(str) < min) errors.add("String length must be >= $min")
      }
      
      maxLength?.let { max ->
        if (countCodePoints(str) > max) errors.add("String length must be <= $max")
      }
      
      pattern?.let { regex ->
        if (!regex.containsMatchIn(str)) errors.add("String must match pattern: ${regex.pattern}")
      }
    }
    
    // Apply array constraints only to arrays
    if (node is KsonList) {
      val listElements = node.elements
      
      minItems?.let { min ->
        if (listElements.size < min) errors.add("Array length must be >= $min")
      }
      
      maxItems?.let { max ->
        if (listElements.size > max) errors.add("Array length must be <= $max")
      }
      
      uniqueItems?.let { unique ->
        if (unique && !areItemsUnique(listElements)) {
          errors.add("Array items must be unique")
        }
      }
    }
    
    // Apply object constraints only to objects
    if (node is KsonObject) {
      val objectProperties = node.propertyMap
      
      minProperties?.let { min ->
        if (objectProperties.size < min) errors.add("Object must have >= $min properties")
      }
      
      maxProperties?.let { max ->
        if (objectProperties.size > max) errors.add("Object must have <= $max properties")
      }
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
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
        is org.kson.ast.KsonString -> a.value == (b as org.kson.ast.KsonString).value
        is org.kson.ast.KsonBoolean -> a.value == (b as org.kson.ast.KsonBoolean).value
        is org.kson.ast.KsonNull -> true  // Both are null
        is org.kson.ast.KsonNumber -> numbersEqual(a, b as org.kson.ast.KsonNumber)
        is org.kson.ast.KsonObject -> objectsEqual(a, b as org.kson.ast.KsonObject)
        is org.kson.ast.KsonList -> listsEqual(a, b as org.kson.ast.KsonList)
        else -> false
      }
      // Special case: Numbers can be equal across integer/decimal representations
      a is org.kson.ast.KsonNumber && b is org.kson.ast.KsonNumber -> numbersEqual(a, b)
      else -> false
    }
  }

  private fun numbersEqual(a: org.kson.ast.KsonNumber, b: org.kson.ast.KsonNumber): Boolean {
    val aValue = when (a.value) {
      is org.kson.parser.NumberParser.ParsedNumber.Integer -> a.value.value.toDouble()
      is org.kson.parser.NumberParser.ParsedNumber.Decimal -> a.value.value
    }
    val bValue = when (b.value) {
      is org.kson.parser.NumberParser.ParsedNumber.Integer -> b.value.value.toDouble()
      is org.kson.parser.NumberParser.ParsedNumber.Decimal -> b.value.value
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
  override fun validate(node: KsonValue): ValidationResult {
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
    
    return if (allowedTypes.contains(nodeType) || (nodeType == "integer" && allowedTypes.contains("number"))) {
      ValidationResult.Valid
    } else {
      ValidationResult.Invalid(listOf("Expected one of: ${allowedTypes.joinToString()}, but got: $nodeType"))
    }
  }
}

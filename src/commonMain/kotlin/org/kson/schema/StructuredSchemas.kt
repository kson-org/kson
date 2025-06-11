package org.kson.schema

import org.kson.ast.*

/**
 * Schema for array values.
 */
data class ArraySchema(
  val items: JsonSchema? = null,
  val prefixItems: List<JsonSchema>? = null,
  val additionalItems: AdditionalItems = AdditionalItems.Allowed,
  val contains: JsonSchema? = null,
  val minItems: Int? = null,
  val maxItems: Int? = null,
  val uniqueItems: Boolean? = null,
  val enforceArrayType: Boolean = false,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult {
    if (node !is KsonList) {
      return if (enforceArrayType) {
        ValidationResult.Invalid(listOf("Expected array"))
      } else {
        ValidationResult.Valid // Array schemas without explicit type ignore non-arrays
      }
    }
    
    val errors = mutableListOf<String>()
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

    // Validate contains (at least one item must match)
    contains?.let { schema ->
      if (listElements.isEmpty()) {
        errors.add("Array must contain at least one item matching the contains schema")
      } else {
        val hasMatch = listElements.any { element ->
          schema.validate(element.ksonValue) is ValidationResult.Valid
        }
        if (hasMatch) {
          // no-op: IntelliJ was complaining that this needed both if and else
        } else {
          errors.add("Array must contain at least one item matching the contains schema")
        }
      }
    }
    
    // Validate prefix items (tuple validation)
    prefixItems?.forEachIndexed { index, schema ->
      if (index < listElements.size) {
        when (val result = schema.validate(listElements[index].ksonValue)) {
          is ValidationResult.Invalid -> errors.addAll(
            result.errors.map { "Item at index $index: $it" }
          )
          ValidationResult.Valid -> {}
        }
      }
    }
    
    // Validate remaining items if items schema is provided
    items?.let { schema ->
      listElements.forEachIndexed { index, element ->
        if (prefixItems == null || index >= prefixItems.size) {
          when (val result = schema.validate(element.ksonValue)) {
            is ValidationResult.Invalid -> errors.addAll(
              result.errors.map { "Item at index $index: $it" }
            )
            ValidationResult.Valid -> {}
          }
        }
      }
    }
    
    // Handle additional items beyond what prefixItems covers
    // Only applies when items is not defined or when prefixItems is used
    if (items == null && prefixItems != null) {
      val additionalItemsStartIndex = prefixItems.size
      if (additionalItemsStartIndex < listElements.size) {
        when (additionalItems) {
          AdditionalItems.Forbidden -> {
            errors.add("Additional items not allowed beyond index ${additionalItemsStartIndex - 1}")
          }
          is AdditionalItems.Schema -> {
            for (index in additionalItemsStartIndex until listElements.size) {
              when (val result = additionalItems.schema.validate(listElements[index].ksonValue)) {
                is ValidationResult.Invalid -> errors.addAll(
                  result.errors.map { "Item at index $index: $it" }
                )
                ValidationResult.Valid -> {}
              }
            }
          }
          AdditionalItems.Allowed -> {}
        }
      }
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}

/**
 * Schema for object values.
 */
data class ObjectSchema(
  val properties: Map<String, JsonSchema> = emptyMap(),
  val required: Set<String> = emptySet(),
  val additionalProperties: AdditionalProperties = AdditionalProperties.Allowed,
  val minProperties: Int? = null,
  val maxProperties: Int? = null,
  val patternProperties: Map<Regex, JsonSchema> = emptyMap(),
  val enforceObjectType: Boolean = false,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult {
    if (node !is KsonObject) {
      return if (enforceObjectType) {
        ValidationResult.Invalid(listOf("Expected object"))
      } else {
        ValidationResult.Valid // Object schemas without explicit type ignore non-objects
      }
    }
    
    val errors = mutableListOf<String>()
    val objectProperties = node.propertyMap
    
    // Check required properties
    required.forEach { prop ->
      if (!objectProperties.containsKey(prop)) {
        errors.add("Missing required property: $prop")
      }
    }
    
    minProperties?.let { min ->
      if (objectProperties.size < min) errors.add("Object must have >= $min properties")
    }
    
    maxProperties?.let { max ->
      if (objectProperties.size > max) errors.add("Object must have <= $max properties")
    }
    
    // Track which properties have been validated
    val validatedProps = mutableSetOf<String>()
    
    // Validate declared properties
    objectProperties.forEach { (key, value) ->
      properties[key]?.let { schema ->
        when (val result = schema.validate(value)) {
          is ValidationResult.Invalid -> errors.addAll(
            result.errors.map { "Property '$key': $it" }
          )
          ValidationResult.Valid -> {}
        }
        validatedProps.add(key)
      }
    }
    
    // Validate pattern properties
    objectProperties.forEach { (key, value) ->
      patternProperties.forEach { (pattern, schema) ->
        if (pattern.containsMatchIn(key)) {
          when (val result = schema.validate(value)) {
            is ValidationResult.Invalid -> errors.addAll(
              result.errors.map { "Property '$key': $it" }
            )
            ValidationResult.Valid -> {}
          }
          validatedProps.add(key)
        }
      }
    }
    
    // Handle additional properties
    val additionalProps = objectProperties.keys - validatedProps
    if (additionalProps.isNotEmpty()) {
      when (additionalProperties) {
        AdditionalProperties.Forbidden -> {
          errors.add("Additional properties not allowed: ${additionalProps.joinToString()}")
        }
        is AdditionalProperties.Schema -> {
          additionalProps.forEach { prop ->
            when (val result = additionalProperties.schema.validate(objectProperties[prop]!!)) {
              is ValidationResult.Invalid -> errors.addAll(
                result.errors.map { "Property '$prop': $it" }
              )
              ValidationResult.Valid -> {}
            }
          }
        }
        AdditionalProperties.Allowed -> {}
      }
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}

/**
 * Check if all items in a list are unique using JSON Schema equality semantics.
 */
private fun areItemsUnique(elements: List<KsonListElement>): Boolean {
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
    a is KsonNumber && b is KsonNumber -> {
      val aDouble = when (a.value) {
        is org.kson.parser.NumberParser.ParsedNumber.Decimal -> a.value.value
        is org.kson.parser.NumberParser.ParsedNumber.Integer -> a.value.value.toDouble()
      }
      val bDouble = when (b.value) {
        is org.kson.parser.NumberParser.ParsedNumber.Decimal -> b.value.value
        is org.kson.parser.NumberParser.ParsedNumber.Integer -> b.value.value.toDouble()
      }
      aDouble == bDouble
    }
    a is KsonString && b is KsonString -> a.value == b.value
    a is KsonBoolean && b is KsonBoolean -> a.value == b.value
    a is KsonNull && b is KsonNull -> true
    a is KsonList && b is KsonList -> listsEqual(a, b)
    a is KsonObject && b is KsonObject -> objectsEqual(a, b)
    else -> false
  }
}

private fun objectsEqual(a: KsonObject, b: KsonObject): Boolean {
  if (a.propertyMap.size != b.propertyMap.size) return false
  
  return a.propertyMap.all { (key, value) ->
    b.propertyMap[key]?.let { bValue ->
      valuesEqual(value, bValue)
    } ?: false
  }
}

private fun listsEqual(a: KsonList, b: KsonList): Boolean {
  if (a.elements.size != b.elements.size) return false
  
  return a.elements.zip(b.elements).all { (aElement, bElement) ->
    valuesEqual(aElement.ksonValue, bElement.ksonValue)
  }
}

/**
 * Schema for property dependencies and schema dependencies.
 */
data class DependenciesSchema(
  val dependencies: Map<String, DependencyType>,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult {
    // Dependencies only apply to objects
    if (node !is KsonObject) {
      return ValidationResult.Valid
    }
    
    val errors = mutableListOf<String>()
    val objectProperties = node.propertyMap
    
    dependencies.forEach { (propertyName, dependency) ->
      if (objectProperties.containsKey(propertyName)) {
        when (dependency) {
          is DependencyType.PropertyDependency -> {
            // Check that all required properties are present
            dependency.requiredProperties.forEach { requiredProp ->
              if (!objectProperties.containsKey(requiredProp)) {
                errors.add("Property '$propertyName' requires property '$requiredProp' to be present")
              }
            }
          }
          is DependencyType.SchemaDependency -> {
            // Validate the object against the dependency schema
            when (val result = dependency.schema.validate(node)) {
              is ValidationResult.Invalid -> errors.addAll(
                result.errors.map { "Dependency for property '$propertyName': $it" }
              )
              ValidationResult.Valid -> {}
            }
          }
        }
      }
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
}

/**
 * Represents different types of dependencies in JSON Schema.
 */
sealed interface DependencyType {
  /**
   * Property dependency: if the key property exists, all properties in the list must also exist.
   */
  data class PropertyDependency(val requiredProperties: List<String>) : DependencyType
  
  /**
   * Schema dependency: if the key property exists, the object must validate against the schema.
   */
  data class SchemaDependency(val schema: JsonSchema) : DependencyType
}

/**
 * Schema for validating property names in objects.
 */
data class PropertyNamesSchema(
  val nameSchema: JsonSchema,
  override val title: String? = null,
  override val description: String? = null,
  override val default: KsonValue? = null,
  override val definitions: Map<String, JsonSchema>? = null
) : JsonSchema {
  override fun validate(node: KsonValue): ValidationResult {
    // PropertyNames only applies to objects
    if (node !is KsonObject) {
      return ValidationResult.Valid
    }
    
    val errors = mutableListOf<String>()
    val objectProperties = node.propertyMap
    
    objectProperties.keys.forEach { propertyName ->
      // Validate each property name as a string against the name schema
      val nameValue = KsonString(propertyName, org.kson.parser.Location(0, 0, 0, 0, 0, 0))
      when (val result = nameSchema.validate(nameValue)) {
        is ValidationResult.Invalid -> errors.addAll(
          result.errors.map { "Property name '$propertyName': $it" }
        )
        ValidationResult.Valid -> {}
      }
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
  }
} 

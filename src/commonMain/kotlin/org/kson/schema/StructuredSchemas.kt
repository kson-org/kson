package org.kson.schema

import org.kson.ast.*
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType

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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (node !is KsonList) {
      if (enforceArrayType) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected array"))
      }
      // Array schemas without explicit type ignore non-arrays
      return
    }

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

    // Validate contains (at least one item must match)
    contains?.let { schema ->
      if (listElements.isEmpty()) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Array must contain at least one item matching the contains schema"))
      } else {
        val hasMatch = listElements.any { element ->
          val tempMessageSink = MessageSink()
          schema.validate(element.ksonValue, tempMessageSink)
          !tempMessageSink.hasErrors()
        }
        if (!hasMatch) {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Array must contain at least one item matching the contains schema"))
        }
      }
    }
    
    // Validate prefix items (tuple validation)
    prefixItems?.forEachIndexed { index, schema ->
      if (index < listElements.size) {
        schema.validate(listElements[index].ksonValue, messageSink)
      }
    }
    
    // Validate remaining items if items schema is provided
    items?.let { schema ->
      listElements.forEachIndexed { index, element ->
        if (prefixItems == null || index >= prefixItems.size) {
          schema.validate(element.ksonValue, messageSink)
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
            messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Additional items not allowed beyond index ${additionalItemsStartIndex - 1}"))
          }
          is AdditionalItems.Schema -> {
            for (index in additionalItemsStartIndex until listElements.size) {
              additionalItems.schema.validate(listElements[index].ksonValue, messageSink)
            }
          }
          AdditionalItems.Allowed -> {}
        }
      }
    }
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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    if (node !is KsonObject) {
      if (enforceObjectType) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Expected object"))
      }
      // Object schemas without explicit type ignore non-objects
      return
    }

    val objectProperties = node.propertyMap
    
    // Check required properties
    required.forEach { prop ->
      if (!objectProperties.containsKey(prop)) {
        messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Missing required property: $prop"))
      }
    }
    
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
    
    // Track which properties have been validated
    val validatedProps = mutableSetOf<String>()
    
    // Validate declared properties
    objectProperties.forEach { (key, value) ->
      properties[key]?.let { schema ->
        schema.validate(value, messageSink)
        validatedProps.add(key)
      }
    }
    
    // Validate pattern properties
    objectProperties.forEach { (key, value) ->
      patternProperties.forEach { (pattern, schema) ->
        if (pattern.containsMatchIn(key)) {
          schema.validate(value, messageSink)
          validatedProps.add(key)
        }
      }
    }
    
    // Handle additional properties
    val additionalProps = objectProperties.keys - validatedProps
    if (additionalProps.isNotEmpty()) {
      when (additionalProperties) {
        AdditionalProperties.Forbidden -> {
          messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Additional properties not allowed: ${additionalProps.joinToString()}"))
        }
        is AdditionalProperties.Schema -> {
          additionalProps.forEach { prop ->
            additionalProperties.schema.validate(objectProperties[prop]!!, messageSink)
          }
        }
        AdditionalProperties.Allowed -> {}
      }
    }
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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    // Dependencies only apply to objects
    if (node !is KsonObject) {
      return
    }

    val objectProperties = node.propertyMap
    
    dependencies.forEach { (propertyName, dependency) ->
      if (objectProperties.containsKey(propertyName)) {
        when (dependency) {
          is DependencyType.PropertyDependency -> {
            // Check that all required properties are present
            dependency.requiredProperties.forEach { requiredProp ->
              if (!objectProperties.containsKey(requiredProp)) {
                messageSink.error(node.location, MessageType.SCHEMA_VALIDATION_ERROR.create("Property '$propertyName' requires property '$requiredProp' to be present"))
              }
            }
          }
          is DependencyType.SchemaDependency -> {
            // Validate the object against the dependency schema
            dependency.schema.validate(node, messageSink)
          }
        }
      }
    }
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
  override fun validate(node: KsonValue, messageSink: MessageSink) {
    // PropertyNames only applies to objects
    if (node !is KsonObject) {
      return
    }

    val objectProperties = node.propertyMap
    
    objectProperties.keys.forEach { propertyName ->
      // Validate each property name as a string against the name schema
      val nameValue = KsonString(propertyName, org.kson.parser.Location(0, 0, 0, 0, 0, 0))
      nameSchema.validate(nameValue, messageSink)
    }
  }
} 

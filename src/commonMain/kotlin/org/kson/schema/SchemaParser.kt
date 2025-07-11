package org.kson.schema

import org.kson.ast.*
import org.kson.parser.*
import org.kson.parser.messages.MessageType.*
import org.kson.schema.validators.*

/**
 * Parses a JSON Schema string into our JsonSchema model.
 */
object SchemaParser {
  fun parseSchemaElement(schemaValue: KsonValue, messageSink: MessageSink): JsonSchema? {
    return when (schemaValue) {
      is KsonBoolean -> JsonBooleanSchema(schemaValue.value)
      is KsonObject -> parseObjectSchema(schemaValue, messageSink)
      else -> {
        messageSink.error(schemaValue.location, SCHEMA_OBJECT_OR_BOOLEAN.create())
        null
      }
    }
  }

  private fun parseObjectSchema(schemaObject: KsonObject, messageSink: MessageSink): JsonSchema {
    val schemaProperties = schemaObject.propertyMap

    val title = schemaProperties["title"] ?.let { title ->
      if (title is KsonString) {
        title.value
      } else {
        messageSink.error(title.location, SCHEMA_STRING_REQUIRED.create("title"))
        null
      }
    }
    val description = schemaProperties["description"] ?.let { description ->
      if (description is KsonString) {
        description.value
      } else {
        messageSink.error(description.location, SCHEMA_STRING_REQUIRED.create("description"))
        null
      }
    }
    val default = schemaProperties["default"]
    val definitions = schemaProperties["definitions"] ?.let { definitions ->
      if (definitions is KsonObject) {
        definitions.propertyMap.mapValues { (_, value) ->
          parseSchemaElement(value, messageSink)
        }
      } else {
        messageSink.error(definitions.location, SCHEMA_OBJECT_REQUIRED.create("definitions"))
        null
      }
    }

    val typeValidator = schemaProperties["type"] ?.let { typeValue ->
      when (typeValue) {
        is KsonString -> TypeValidator(typeValue.value)
        is KsonList -> {
          val typeArrayEntries = ArrayList<String>()
          for (element in typeValue.elements) {
            if (element.ksonValue is KsonString) {
              typeArrayEntries.add(element.ksonValue.value)
            } else {
              messageSink.error(element.location, SCHEMA_TYPE_ARRAY_ENTRY_ERROR.create())
            }
          }
          TypeValidator(typeArrayEntries)
        }
        else -> {
          messageSink.error(typeValue.location, SCHEMA_TYPE_TYPE_ERROR.create())
          null
        }
      }
    }

    // Collect all declared validators in this schema
    val validators = mutableListOf<JsonSchemaValidator>()
    schemaProperties["minimum"] ?.let { minimum ->
      if (minimum is KsonNumber) {
        validators.add(MinimumValidator(minimum.value.asDouble))
      } else {
        messageSink.error(minimum.location, SCHEMA_NUMBER_REQUIRED.create("minimum"))
      }
    }
    schemaProperties["maximum"] ?.let { maximum ->
      if (maximum is KsonNumber) {
        validators.add(MaximumValidator(maximum.value.asDouble))
      } else {
        messageSink.error(maximum.location, SCHEMA_NUMBER_REQUIRED.create("maximum"))
      }
    }

    schemaProperties["multipleOf"] ?.let { multipleOf ->
      if (multipleOf is KsonNumber) {
        validators.add(MultipleOfValidator(multipleOf.value.asDouble))
      } else {
        messageSink.error(multipleOf.location, SCHEMA_NUMBER_REQUIRED.create("multipleOf"))
      }
    }

    schemaProperties["exclusiveMinimum"] ?.let { exclusiveMinimum ->
      if (exclusiveMinimum is KsonNumber) {
        validators.add(ExclusiveMinimumValidator(exclusiveMinimum.value.asDouble))
      } else {
        messageSink.error(exclusiveMinimum.location, SCHEMA_NUMBER_REQUIRED.create("exclusiveMinimum"))
      }
    }

    schemaProperties["exclusiveMaximum"] ?.let { exclusiveMaximum ->
      if (exclusiveMaximum is KsonNumber) {
        validators.add(ExclusiveMaximumValidator(exclusiveMaximum.value.asDouble))
      } else {
        messageSink.error(exclusiveMaximum.location, SCHEMA_NUMBER_REQUIRED.create("exclusiveMaximum"))
      }
    }

    schemaProperties["minLength"] ?.let { minLength ->
      if (minLength is KsonNumber) {
        asSchemaInteger(minLength) ?.let { minLengthLong ->
          validators.add(MinLengthValidator(minLengthLong))
        }
      } else {
        messageSink.error(minLength.location, SCHEMA_INTEGER_REQUIRED.create("minLength"))
      }
    }

    schemaProperties["maxLength"] ?.let { maxLength ->
      if (maxLength is KsonNumber) {
        asSchemaInteger(maxLength) ?.let { maxLengthLong ->
          validators.add(MaxLengthValidator(maxLengthLong))
        }
      } else {
        messageSink.error(maxLength.location, SCHEMA_INTEGER_REQUIRED.create("maxLength"))
      }
    }

    schemaProperties["pattern"] ?.let { pattern ->
      if (pattern is KsonString) {
        validators.add(PatternValidator(pattern.value))
      } else {
        messageSink.error(pattern.location, SCHEMA_STRING_REQUIRED.create("pattern"))
      }
    }

    schemaProperties["enum"] ?.let { enum ->
      if (enum is KsonList) {
        validators.add(EnumValidator(enum))
      } else {
        messageSink.error(enum.location, SCHEMA_ARRAY_REQUIRED.create("enum"))
      }
    }

    schemaProperties["items"]?.let { itemsValue ->
      val additionalItemsValidator = schemaProperties["additionalItems"]?.let { additionalItems ->
        when (additionalItems) {
          is KsonBoolean -> AdditionalItemsBooleanValidator(additionalItems.value)
          else -> AdditionalItemsSchemaValidator(parseSchemaElement(additionalItems, messageSink))
        }
      }

      when (itemsValue) {
        is KsonList -> {
          val leadingItemsValidator = LeadingItemsTupleValidator(itemsValue.elements.mapNotNull {
            parseSchemaElement(it.ksonValue, messageSink)
          })
          validators.add(ItemsValidator(leadingItemsValidator, additionalItemsValidator))
        }
        else -> {
          val itemsSchema = parseSchemaElement(itemsValue, messageSink)
          if (itemsSchema != null) {
            val leadingItemsValidator = LeadingItemsSchemaValidator(itemsSchema)
            validators.add(ItemsValidator(leadingItemsValidator, additionalItemsValidator))
          } else {
            // no-op todo this shouldn't be necessary - bug in Intellij inspections?
          }
        }
      }
    }

    schemaProperties["contains"]?.let { contains ->
      parseSchemaElement(contains, messageSink) ?.let {
        validators.add(ContainsValidator(it))
      }
    }

    schemaProperties["minItems"] ?.let { minItems ->
      if (minItems is KsonNumber) {
        asSchemaInteger(minItems) ?.let { minItemsLong ->
          validators.add(MinItemsValidator(minItemsLong))
        }
      } else {
        messageSink.error(minItems.location, SCHEMA_INTEGER_REQUIRED.create("minItems"))
      }
    }

    schemaProperties["maxItems"] ?.let { maxItems ->
      if (maxItems is KsonNumber) {
        asSchemaInteger(maxItems) ?.let { maxItemsLong ->
          validators.add(MaxItemsValidator(maxItemsLong))
        }
      } else {
        messageSink.error(maxItems.location, SCHEMA_INTEGER_REQUIRED.create("maxItems"))
      }
    }

    schemaProperties["uniqueItems"] ?.let { uniqueItems ->
      if (uniqueItems is KsonBoolean) {
        validators.add(UniqueItemsValidator(uniqueItems.value))
      } else {
        messageSink.error(uniqueItems.location, SCHEMA_BOOLEAN_REQUIRED.create("uniqueItems"))
      }
    }

    val propertySchemas = schemaProperties["properties"] ?.let { properties ->
      if (properties is KsonObject) {
        properties.propertyMap.mapValues {
          parseSchemaElement(it.value, messageSink)
        }
      } else {
        messageSink.error(properties.location, SCHEMA_OBJECT_REQUIRED.create("properties"))
        null
      }
    }

    val patternPropertySchemas = schemaProperties["patternProperties"] ?.let { patternProperties ->
      if (patternProperties is KsonObject) {
        patternProperties.propertyMap.mapValues {
          parseSchemaElement(it.value, messageSink) ?: return@mapValues null
        }
      } else {
        messageSink.error(patternProperties.location, SCHEMA_OBJECT_REQUIRED.create("patternProperties"))
        null
      }
    }

    val additionalPropertiesValidator = schemaProperties["additionalProperties"]?.let { additionalProperties ->
      when (additionalProperties) {
        is KsonBoolean -> AdditionalPropertiesBooleanValidator(additionalProperties.value)
        else -> AdditionalPropertiesSchemaValidator(parseSchemaElement(additionalProperties, messageSink))
      }
    }

    if (propertySchemas != null || patternPropertySchemas != null || additionalPropertiesValidator != null) {
      validators.add(PropertiesValidator(propertySchemas, patternPropertySchemas, additionalPropertiesValidator))
    }

    schemaProperties["required"] ?.let { required ->
      if (required is KsonList) {
        val requiredArrayEntries = ArrayList<String>()
        for (element in required.elements) {
          if (element.ksonValue is KsonString) {
            requiredArrayEntries.add(element.ksonValue.value)
          } else {
            messageSink.error(element.location, SCHEMA_STRING_ARRAY_ENTRY_ERROR.create("required"))
          }
        }
        validators.add(RequiredValidator(requiredArrayEntries))
      } else {
        messageSink.error(required.location, SCHEMA_ARRAY_REQUIRED.create("required"))
      }
    }

    schemaProperties["minProperties"] ?.let { minProperties ->
      if (minProperties is KsonNumber) {
        asSchemaInteger(minProperties) ?.let { minPropertiesLong ->
          validators.add(MinPropertiesValidator(minPropertiesLong))
        }
      } else {
        messageSink.error(minProperties.location, SCHEMA_INTEGER_REQUIRED.create("minProperties"))
      }
    }

    schemaProperties["maxProperties"] ?.let { maxProperties ->
      if (maxProperties is KsonNumber) {
        asSchemaInteger(maxProperties) ?.let { maxPropertiesLong ->
          validators.add(MaxPropertiesValidator(maxPropertiesLong))
        }
      } else {
        messageSink.error(maxProperties.location, SCHEMA_INTEGER_REQUIRED.create("maxProperties"))
      }
    }

    schemaProperties["const"] ?.let { const ->
      validators.add(ConstValidator(const))
    }

    schemaProperties["allOf"] ?.let { allOf ->
      if (allOf is KsonList) {
        val allOfArrayEntries = ArrayList<JsonSchema>()
        for (element in allOf.elements) {
          allOfArrayEntries.add(parseSchemaElement(element.ksonValue, messageSink) ?: continue)
        }
        validators.add(AllOfValidator(allOfArrayEntries))
      } else {
        messageSink.error(allOf.location, SCHEMA_ARRAY_REQUIRED.create("allOf"))
      }
    }

    schemaProperties["anyOf"] ?.let { anyOf ->
      if (anyOf is KsonList) {
        val anyOfArrayEntries = ArrayList<JsonSchema>()
        for (element in anyOf.elements) {
          anyOfArrayEntries.add(parseSchemaElement(element.ksonValue, messageSink) ?: continue)
        }
        validators.add(AnyOfValidator(anyOfArrayEntries))
      } else {
        messageSink.error(anyOf.location, SCHEMA_ARRAY_REQUIRED.create("anyOf"))
      }
    }

    schemaProperties["oneOf"] ?.let { oneOf ->
      if (oneOf is KsonList) {
        val oneOfArrayEntries = ArrayList<JsonSchema>()
        for (element in oneOf.elements) {
          oneOfArrayEntries.add(parseSchemaElement(element.ksonValue, messageSink) ?: continue)
        }
        validators.add(OneOfValidator(oneOfArrayEntries))
      } else {
        messageSink.error(oneOf.location, SCHEMA_ARRAY_REQUIRED.create("oneOf"))
      }
    }

    schemaProperties["not"]?.let { not ->
      validators.add(NotValidator(parseSchemaElement(not, messageSink)))
    }

    schemaProperties["if"]?.let { ifElement ->
      val ifSchema = parseSchemaElement(ifElement, messageSink)
      val thenSchema = schemaProperties["then"]?.let { parseSchemaElement(it, messageSink) }
      val elseSchema = schemaProperties["else"]?.let { parseSchemaElement(it, messageSink) }
      validators.add(IfValidator(ifSchema, thenSchema, elseSchema))
    }

    schemaProperties["dependencies"] ?.let { dependencies ->
      if (dependencies !is KsonObject) {
        messageSink.error(dependencies.location, SCHEMA_OBJECT_REQUIRED.create("dependencies"))
        return@let
      }

      val dependencyMap = dependencies.propertyMap.mapValues { (_, value) ->
        if (value is KsonList) {
          val dependencyArrayEntries = mutableSetOf<String>()
          for (element in value.elements) {
            if (element.ksonValue is KsonString) {
              dependencyArrayEntries.add(element.ksonValue.value)
            } else {
              messageSink.error(element.location, SCHEMA_DEPENDENCIES_ARRAY_STRING_REQUIRED.create())
            }
          }
          return@mapValues DependencyValidatorArray(dependencyArrayEntries)
        } else {
          return@mapValues DependencyValidatorSchema(parseSchemaElement(value, messageSink))
        }
      }
      validators.add(DependenciesValidator(dependencyMap))
    }

    schemaProperties["propertyNames"] ?.let { propertyNames ->
      validators.add(PropertyNamesValidator(parseSchemaElement(propertyNames, messageSink)))
    }

    return JsonObjectSchema(title, description, default, definitions, typeValidator, validators)
  }
}

package org.kson.schema

import org.kson.ast.*
import org.kson.parser.*
import org.kson.parser.NumberParser

/**
 * Parses a JSON Schema string into our JsonSchema model.
 */
object SchemaParser {
  /**
   * Parse a JSON schema string into a JsonSchema object.
   */
  fun parse(schemaJson: String): JsonSchema {
    // Parse JSON directly using Lexer and Parser to avoid circular dependency
    val messageSink = MessageSink()
    val tokens = Lexer(schemaJson).tokenize()
    
    if (tokens[0].tokenType == TokenType.EOF) {
      throw IllegalArgumentException("Invalid schema: empty schema")
    }

    val builder = KsonBuilder(tokens)
    Parser(builder).parse()
    val schemaAst = builder.buildTree(messageSink)
    
    if (schemaAst == null || messageSink.hasErrors()) {
      throw IllegalArgumentException("Invalid schema: unable to parse")
    }
    
    return parseSchemaElement(schemaAst.toKsonApi() as KsonValue)
  }

  // Helper functions for type-safe extraction and conversion
  private fun extractDouble(value: KsonValue?): Double? {
    return (value as? KsonNumber)?.value?.let { parsedNumber ->
      when (parsedNumber) {
        is NumberParser.ParsedNumber.Decimal -> parsedNumber.value
        is NumberParser.ParsedNumber.Integer -> parsedNumber.value.toDouble()
      }
    }
  }

  private fun extractLong(value: KsonValue?): Long? {
    return (value as? KsonNumber)?.value?.let { parsedNumber ->
      when (parsedNumber) {
        is NumberParser.ParsedNumber.Integer -> parsedNumber.value
        is NumberParser.ParsedNumber.Decimal -> parsedNumber.value.toLong()
      }
    }
  }

  private fun extractInt(value: KsonValue?): Int? {
    return (value as? KsonNumber)?.value?.let { parsedNumber ->
      when (parsedNumber) {
        is NumberParser.ParsedNumber.Integer -> parsedNumber.value.toInt()
        is NumberParser.ParsedNumber.Decimal -> parsedNumber.value.toInt()
      }
    }
  }

  private fun extractString(value: KsonValue?): String? {
    return (value as? KsonString)?.value
  }

  private fun extractBoolean(value: KsonValue?): Boolean? {
    return (value as? KsonBoolean)?.value
  }

  private fun extractObject(value: KsonValue?): KsonObject? {
    return value as? KsonObject
  }

  private fun extractList(value: KsonValue?): KsonList? {
    return value as? KsonList
  }

  private fun parseSchemaElement(schemaValue: KsonValue): JsonSchema {
    return when (schemaValue) {
      is KsonBoolean -> if (schemaValue.value) TrueSchema() else FalseSchema()
      is KsonObject -> parseObjectSchema(schemaValue)
      else -> throw IllegalArgumentException("Invalid schema: schema must be a boolean or object")
    }
  }

  private fun parseObjectSchema(schemaObject: KsonObject): JsonSchema {
    val schemaProperties = schemaObject.propertyMap
    
    // Extract common properties with type safety
    val title = extractString(schemaProperties["title"])
    val description = extractString(schemaProperties["description"])
    val default = schemaProperties["default"]
    val definitions = extractObject(schemaProperties["definitions"])?.propertyMap?.map { (name, value) ->
      name to parseSchemaElement(value)
    }?.toMap()

    // Collect all applicable schemas
    val schemas = mutableListOf<JsonSchema>()

    // Handle type constraints
    val typeProperty = schemaProperties["type"]
    val typeValue = extractString(typeProperty)
    val typeArray = extractList(typeProperty)?.elements?.mapNotNull { extractString(it.ksonValue) }
    
    when {
      // Handle array of types
      typeArray != null -> {
        schemas.add(MultipleTypeSchema(
          allowedTypes = typeArray.toSet(),
          title = title,
          description = description,
          default = default,
          definitions = definitions
        ))
      }
      // Handle single type
      typeValue == "boolean" -> schemas.add(BooleanSchema(title, description, default, definitions))
      typeValue == "null" -> schemas.add(NullSchema(title, description, default, definitions))
      typeValue == "number" -> schemas.add(NumberSchema(
        minimum = extractDouble(schemaProperties["minimum"]),
        maximum = extractDouble(schemaProperties["maximum"]),
        multipleOf = extractDouble(schemaProperties["multipleOf"]),
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
      typeValue == "integer" -> schemas.add(IntegerSchema(
        minimum = extractLong(schemaProperties["minimum"]),
        maximum = extractLong(schemaProperties["maximum"]),
        multipleOf = extractLong(schemaProperties["multipleOf"]),
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
      typeValue == "string" -> schemas.add(StringSchema(
        minLength = extractInt(schemaProperties["minLength"]),
        maxLength = extractInt(schemaProperties["maxLength"]),
        pattern = extractString(schemaProperties["pattern"])?.let { Regex(it) },
        enum = extractList(schemaProperties["enum"])?.elements?.mapNotNull { 
          extractString(it.ksonValue)
        }?.toSet(),
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
      typeValue == "array" -> schemas.add(ArraySchema(
        items = schemaProperties["items"]?.let { itemsValue ->
          when (itemsValue) {
            is KsonList -> null // This is array form, handled as prefixItems
            else -> parseSchemaElement(itemsValue) // This is schema form
          }
        },
        prefixItems = schemaProperties["items"]?.let { itemsValue ->
          when (itemsValue) {
            is KsonList -> itemsValue.elements.map { parseSchemaElement(it.ksonValue) }
            else -> null // This is schema form, not array form
          }
        } ?: extractList(schemaProperties["prefixItems"])?.elements?.map { 
          parseSchemaElement(it.ksonValue)
        },
        additionalItems = when {
          extractBoolean(schemaProperties["additionalItems"]) == false -> 
            AdditionalItems.Forbidden
          schemaProperties["additionalItems"] != null -> 
            AdditionalItems.Schema(
              parseSchemaElement(schemaProperties["additionalItems"]!!)
            )
          else -> AdditionalItems.Allowed
        },
        contains = schemaProperties["contains"]?.let { parseSchemaElement(it) },
        minItems = extractInt(schemaProperties["minItems"]),
        maxItems = extractInt(schemaProperties["maxItems"]),
        uniqueItems = extractBoolean(schemaProperties["uniqueItems"]),
        enforceArrayType = true,
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
      typeValue == "object" -> schemas.add(ObjectSchema(
        properties = extractObject(schemaProperties["properties"])?.propertyMap?.mapValues { 
          parseSchemaElement(it.value)
        } ?: emptyMap(),
        required = extractList(schemaProperties["required"])?.elements?.mapNotNull { 
          extractString(it.ksonValue)
        }?.toSet() ?: emptySet(),
        additionalProperties = when {
          extractBoolean(schemaProperties["additionalProperties"]) == false -> 
            AdditionalProperties.Forbidden
          schemaProperties["additionalProperties"] != null -> 
            AdditionalProperties.Schema(
              parseSchemaElement(schemaProperties["additionalProperties"]!!)
            )
          else -> AdditionalProperties.Allowed
        },
        minProperties = extractInt(schemaProperties["minProperties"]),
        maxProperties = extractInt(schemaProperties["maxProperties"]),
        patternProperties = extractObject(schemaProperties["patternProperties"])?.propertyMap?.mapKeys {
          Regex(it.key)
        }?.mapValues {
          parseSchemaElement(it.value)
        } ?: emptyMap(),
        enforceObjectType = true,
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
    }

    // Handle const schema
    if (schemaProperties.containsKey("const")) {
      val constValue = schemaProperties["const"]
        ?: throw IllegalArgumentException("Invalid schema: const value is null")
      schemas.add(ConstSchema(
        constValue,
        title, description, default, definitions
      ))
    }

    // Handle enum schema
    if (schemaProperties.containsKey("enum")) {
      val enumList = extractList(schemaProperties["enum"])
        ?: throw IllegalArgumentException("Invalid schema: enum is not an array")
      schemas.add(EnumSchema(
        enumList.elements.map { it.ksonValue }.toSet(),
        title, description, default, definitions
      ))
    }

    // Handle constraint schemas (when no explicit type but constraints are present)
    val constraintKeywords = setOf("minimum", "maximum", "multipleOf", "exclusiveMinimum", "exclusiveMaximum", "minLength", "maxLength", "pattern", "minItems", "maxItems", "uniqueItems", "minProperties", "maxProperties")
    if (typeValue == null && typeArray == null && constraintKeywords.any { schemaProperties.containsKey(it) }) {
      schemas.add(ConstraintSchema(
        // Number constraints
        minimum = schemaProperties["minimum"]?.let { extractDouble(it) },
        maximum = schemaProperties["maximum"]?.let { extractDouble(it) },
        multipleOf = schemaProperties["multipleOf"]?.let { extractDouble(it) },
        exclusiveMinimum = schemaProperties["exclusiveMinimum"]?.let { extractDouble(it) },
        exclusiveMaximum = schemaProperties["exclusiveMaximum"]?.let { extractDouble(it) },
        // String constraints
        minLength = schemaProperties["minLength"]?.let { extractInt(it) },
        maxLength = schemaProperties["maxLength"]?.let { extractInt(it) },
        pattern = schemaProperties["pattern"]?.let { extractString(it)?.let { Regex(it) } },
        // Array constraints
        minItems = schemaProperties["minItems"]?.let { extractInt(it) },
        maxItems = schemaProperties["maxItems"]?.let { extractInt(it) },
        uniqueItems = schemaProperties["uniqueItems"]?.let { extractBoolean(it) },
        // Object constraints
        minProperties = schemaProperties["minProperties"]?.let { extractInt(it) },
        maxProperties = schemaProperties["maxProperties"]?.let { extractInt(it) },
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
    }

    // Handle allOf
    if (schemaProperties.containsKey("allOf")) {
      val allOfList = extractList(schemaProperties["allOf"])
        ?: throw IllegalArgumentException("Invalid schema: allOf is not an array")
      schemas.add(AllOfSchema(
        allOfList.elements.map { 
          parseSchemaElement(it.ksonValue)
        },
        title, description, default, definitions
      ))
    }

    // Handle anyOf
    if (schemaProperties.containsKey("anyOf")) {
      val anyOfList = extractList(schemaProperties["anyOf"])
        ?: throw IllegalArgumentException("Invalid schema: anyOf is not an array")
      schemas.add(AnyOfSchema(
        anyOfList.elements.map { 
          parseSchemaElement(it.ksonValue)
        },
        title, description, default, definitions
      ))
    }

    // Handle oneOf
    if (schemaProperties.containsKey("oneOf")) {
      val oneOfList = extractList(schemaProperties["oneOf"])
        ?: throw IllegalArgumentException("Invalid schema: oneOf is not an array")
      schemas.add(OneOfSchema(
        oneOfList.elements.map { 
          parseSchemaElement(it.ksonValue)
        },
        title, description, default, definitions
      ))
    }

    // Handle not
    if (schemaProperties.containsKey("not")) {
      schemas.add(NotSchema(
        parseSchemaElement(schemaProperties["not"]!!),
        title, description, default, definitions
      ))
    }

    // Handle if-then-else
    if (schemaProperties.containsKey("if")) {
      schemas.add(IfThenElseSchema(
        ifSchema = parseSchemaElement(schemaProperties["if"]!!),
        thenSchema = schemaProperties["then"]?.let { parseSchemaElement(it) },
        elseSchema = schemaProperties["else"]?.let { parseSchemaElement(it) },
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
    }

    // Handle $ref
    if (schemaProperties.containsKey("\$ref")) {
      val refString = extractString(schemaProperties["\$ref"])
        ?: throw IllegalArgumentException("Invalid schema: \$ref is not a string")
      schemas.add(RefSchema(
        refString,
        title, description, default, definitions
      ))
    }

    // Handle dependencies
    if (schemaProperties.containsKey("dependencies")) {
      val dependenciesObj = extractObject(schemaProperties["dependencies"])
        ?: throw IllegalArgumentException("Invalid schema: dependencies is not an object")
      
      val dependencies = dependenciesObj.propertyMap.mapValues { (_, value) ->
        when (value) {
          is KsonList -> {
            // Property dependency: array of required property names
            DependencyType.PropertyDependency(
              value.elements.mapNotNull { extractString(it.ksonValue) }
            )
          }
          is KsonBoolean -> {
            // Boolean schema dependency
            DependencyType.SchemaDependency(
              if (value.value) TrueSchema() else FalseSchema()
            )
          }
          else -> {
            // Schema dependency: object schema
            DependencyType.SchemaDependency(parseSchemaElement(value))
          }
        }
      }
      
      schemas.add(DependenciesSchema(
        dependencies,
        title, description, default, definitions
      ))
    }

    // Handle propertyNames
    if (schemaProperties.containsKey("propertyNames")) {
      val propertyNamesValue = schemaProperties["propertyNames"]!!
      val nameSchema = when (propertyNamesValue) {
        is KsonBoolean -> {
          if (propertyNamesValue.value) TrueSchema() else FalseSchema()
        }
        else -> parseSchemaElement(propertyNamesValue)
      }
      
      schemas.add(PropertyNamesSchema(
        nameSchema,
        title, description, default, definitions
      ))
    }

    // Handle implicit object schema keywords (when no explicit type)
    if (typeValue == null && typeArray == null && schemaProperties.keys.any { it in setOf("properties", "required", "additionalProperties", "patternProperties") }) {
      schemas.add(ObjectSchema(
        properties = extractObject(schemaProperties["properties"])?.propertyMap?.mapValues { 
          parseSchemaElement(it.value)
        } ?: emptyMap(),
        required = extractList(schemaProperties["required"])?.elements?.mapNotNull { 
          extractString(it.ksonValue)
        }?.toSet() ?: emptySet(),
        additionalProperties = when {
          extractBoolean(schemaProperties["additionalProperties"]) == false -> 
            AdditionalProperties.Forbidden
          schemaProperties["additionalProperties"] != null -> 
            AdditionalProperties.Schema(
              parseSchemaElement(schemaProperties["additionalProperties"]!!)
            )
          else -> AdditionalProperties.Allowed
        },
        minProperties = extractInt(schemaProperties["minProperties"]),
        maxProperties = extractInt(schemaProperties["maxProperties"]),
        patternProperties = extractObject(schemaProperties["patternProperties"])?.propertyMap?.mapKeys { 
          Regex(it.key)
        }?.mapValues {
          parseSchemaElement(it.value)
        } ?: emptyMap(),
        enforceObjectType = false,
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
    }

    // Handle implicit array schema keywords (when no explicit type)
    if (typeValue == null && typeArray == null && schemaProperties.keys.any { it in setOf("items", "additionalItems", "prefixItems", "contains") }) {
      schemas.add(ArraySchema(
        items = schemaProperties["items"]?.let { itemsValue ->
          when (itemsValue) {
            is KsonList -> null // This is array form, handled as prefixItems
            else -> parseSchemaElement(itemsValue) // This is schema form
          }
        },
        prefixItems = schemaProperties["items"]?.let { itemsValue ->
          when (itemsValue) {
            is KsonList -> itemsValue.elements.map { parseSchemaElement(it.ksonValue) }
            else -> null // This is schema form, not array form
          }
        } ?: extractList(schemaProperties["prefixItems"])?.elements?.map { 
          parseSchemaElement(it.ksonValue)
        },
        additionalItems = when {
          extractBoolean(schemaProperties["additionalItems"]) == false -> 
            AdditionalItems.Forbidden
          schemaProperties["additionalItems"] != null -> 
            AdditionalItems.Schema(
              parseSchemaElement(schemaProperties["additionalItems"]!!)
            )
          else -> AdditionalItems.Allowed
        },
        contains = schemaProperties["contains"]?.let { parseSchemaElement(it) },
        minItems = extractInt(schemaProperties["minItems"]),
        maxItems = extractInt(schemaProperties["maxItems"]),
        uniqueItems = extractBoolean(schemaProperties["uniqueItems"]),
        enforceArrayType = false,
        title = title,
        description = description,
        default = default,
        definitions = definitions
      ))
    }

    // Return the appropriate schema based on how many schemas we collected
    return when (schemas.size) {
      0 -> {
        // Empty schema (no type or known keywords) - accepts any value
        UniversalSchema(title, description, default, definitions)
      }
      1 -> {
        // Single schema - return it directly
        schemas[0]
      }
      else -> {
        // Multiple schemas - combine them with allOf
        AllOfSchema(
          subschemas = schemas,
          title = title,
          description = description,
          default = default,
          definitions = definitions
        )
      }
    }
  }
} 

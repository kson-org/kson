package org.kson.schema

import org.kson.parser.*
import org.kson.parser.messages.MessageType.*
import org.kson.schema.validators.*
import org.kson.schema.SchemaIdLookup.Companion.resolveUri
import org.kson.value.*

/**
 * Parses a JSON Schema string into our JsonSchema model.
 */
object SchemaParser {
    /**
     * Parse [schemaRootValue] as a JSON Schema root, i.e. a complete JSON Schema, not a sub-schema.
     * See [parseSchemaElement] for sub-schema parsing.
     */
    fun parseSchemaRoot(schemaRootValue: KsonValue, messageSink: MessageSink): JsonSchema? {
        // Determine the initial base URI from the root schema's $id if present
        val initialBaseUri = if (schemaRootValue is KsonObject) {
            extractId(schemaRootValue, messageSink) ?: ""
        } else {
            ""
        }

        val idLookup = SchemaIdLookup(schemaRootValue)
        return parseSchemaElement(schemaRootValue, messageSink, initialBaseUri, idLookup)
    }

    /**
     *  Parse [schemaValue] as a JSON sub-schema. See [parseSchemaRoot] for full schema parsing.
     *
     *  @param schemaValue The schema value to parse
     *  @param messageSink The message sink for errors
     *  @param currentBaseUri The current base URI context for resolving references
     *  @param idLookup The lookup for resolving $id and $ref references
     */
    fun parseSchemaElement(
        schemaValue: KsonValue,
        messageSink: MessageSink,
        currentBaseUri: String,
        idLookup: SchemaIdLookup,
        propertyName: String? = null
    ): JsonSchema? {
        return when (schemaValue) {
            is KsonBoolean -> JsonBooleanSchema(schemaValue.value)
            is KsonObject -> parseObjectSchema(schemaValue, messageSink, currentBaseUri, idLookup, propertyName = propertyName)
            else -> {
                messageSink.error(schemaValue.location, SCHEMA_OBJECT_OR_BOOLEAN.create())
                null
            }
        }
    }

    private fun parseObjectSchema(
        schemaObject: KsonObject,
        messageSink: MessageSink,
        currentBaseUri: String,
        idLookup: SchemaIdLookup,
        propertyName: String? = null
    ): JsonSchema? {
        val schemaProperties = schemaObject.propertyLookup

        when (val refOutcome = tryResolveRef(schemaProperties, currentBaseUri, idLookup, messageSink)) {
            is RefOutcome.Resolved -> return refOutcome.schema
            is RefOutcome.Failed -> return null
            is RefOutcome.NotPresent -> Unit // fall through to parse the remaining keywords
        }

        // Check if this schema defines a new $id and update base URI accordingly
        val idString = extractId(schemaObject, messageSink)
        val updatedBaseUri = if (idString != null) {
            resolveUri(idString, currentBaseUri).toString()
        } else {
            currentBaseUri
        }

        val metadata = parseMetadata(schemaProperties, updatedBaseUri, idLookup, messageSink)
        val typeValidator = parseTypeValidator(schemaProperties, propertyName, messageSink)

        // Order here drives the order errors land in messageSink; preserve it.
        val validators = buildList {
            addIfNotNull(parseNumericValidator(schemaProperties, "minimum", messageSink, ::MinimumValidator))
            addIfNotNull(parseNumericValidator(schemaProperties, "maximum", messageSink, ::MaximumValidator))
            addIfNotNull(parseNumericValidator(schemaProperties, "multipleOf", messageSink, ::MultipleOfValidator))
            addIfNotNull(parseNumericValidator(schemaProperties, "exclusiveMinimum", messageSink, ::ExclusiveMinimumValidator))
            addIfNotNull(parseNumericValidator(schemaProperties, "exclusiveMaximum", messageSink, ::ExclusiveMaximumValidator))

            addIfNotNull(parseIntegerValidator(schemaProperties, "minLength", messageSink, ::MinLengthValidator))
            addIfNotNull(parseIntegerValidator(schemaProperties, "maxLength", messageSink, ::MaxLengthValidator))

            addIfNotNull(parsePatternValidator(schemaProperties, messageSink))

            addIfNotNull(parseEnumValidator(schemaProperties, messageSink))

            addIfNotNull(parseItemsValidator(schemaProperties, updatedBaseUri, idLookup, messageSink))

            addIfNotNull(parseSubSchemaValidator(schemaProperties, "contains", updatedBaseUri, idLookup, messageSink, ::ContainsValidator))

            addIfNotNull(parseIntegerValidator(schemaProperties, "minItems", messageSink, ::MinItemsValidator))
            addIfNotNull(parseIntegerValidator(schemaProperties, "maxItems", messageSink, ::MaxItemsValidator))

            addIfNotNull(parseUniqueItemsValidator(schemaProperties, messageSink))

            addIfNotNull(parsePropertyValidators(schemaProperties, metadata.title, updatedBaseUri, idLookup, messageSink))

            addIfNotNull(parseRequiredValidator(schemaProperties, messageSink))

            addIfNotNull(parseIntegerValidator(schemaProperties, "minProperties", messageSink, ::MinPropertiesValidator))
            addIfNotNull(parseIntegerValidator(schemaProperties, "maxProperties", messageSink, ::MaxPropertiesValidator))

            addIfNotNull(schemaProperties["const"]?.let(::ConstValidator))

            addIfNotNull(parseSchemaListValidator(schemaProperties, "allOf", updatedBaseUri, idLookup, messageSink, ::AllOfValidator))
            addIfNotNull(parseSchemaListValidator(schemaProperties, "anyOf", updatedBaseUri, idLookup, messageSink, ::AnyOfValidator))
            addIfNotNull(parseSchemaListValidator(schemaProperties, "oneOf", updatedBaseUri, idLookup, messageSink, ::OneOfValidator))

            addIfNotNull(parseSubSchemaValidator(schemaProperties, "not", updatedBaseUri, idLookup, messageSink, ::NotValidator))

            addIfNotNull(parseIfThenElseValidator(schemaProperties, updatedBaseUri, idLookup, messageSink))

            addIfNotNull(parseDependenciesValidator(schemaProperties, updatedBaseUri, idLookup, messageSink))

            addIfNotNull(parseSubSchemaValidator(schemaProperties, "propertyNames", updatedBaseUri, idLookup, messageSink, ::PropertyNamesValidator))
        }

        return JsonObjectSchema(
            metadata.title, metadata.description, metadata.comment,
            metadata.default, metadata.definitions,
            typeValidator, validators
        )
    }

    /**
     * Parses a keyword whose value must be a JSON number into a validator built by [factory].
     * Reports [SCHEMA_NUMBER_REQUIRED] when the keyword is present but not a [KsonNumber].
     */
    private inline fun parseNumericValidator(
        schemaProperties: Map<String, KsonValue>,
        key: String,
        messageSink: MessageSink,
        factory: (Double) -> JsonSchemaValidator,
    ): JsonSchemaValidator? {
        val value = schemaProperties[key] ?: return null
        if (value !is KsonNumber) {
            messageSink.error(value.location, SCHEMA_NUMBER_REQUIRED.create(key))
            return null
        }
        return factory(value.value.asDouble)
    }

    /**
     * Parses a keyword whose value must be a schema integer (see [asSchemaInteger]) into a validator
     * built by [factory]. Reports [SCHEMA_INTEGER_REQUIRED] when the keyword is present but not a
     * [KsonNumber]; silently skips when the number exists but is not representable as an integer
     * (matching the original inline behavior).
     */
    private inline fun parseIntegerValidator(
        schemaProperties: Map<String, KsonValue>,
        key: String,
        messageSink: MessageSink,
        factory: (Long) -> JsonSchemaValidator,
    ): JsonSchemaValidator? {
        val value = schemaProperties[key] ?: return null
        if (value !is KsonNumber) {
            messageSink.error(value.location, SCHEMA_INTEGER_REQUIRED.create(key))
            return null
        }
        return asSchemaInteger(value)?.let(factory)
    }

    /**
     * Parses a keyword whose value is a single sub-schema (e.g. `contains`, `not`,
     * `propertyNames`) into a validator built by [factory]. No validator is added when the
     * sub-schema fails to parse (the underlying parse reports the error).
     */
    private inline fun parseSubSchemaValidator(
        schemaProperties: Map<String, KsonValue>,
        key: String,
        updatedBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
        factory: (JsonSchema) -> JsonSchemaValidator,
    ): JsonSchemaValidator? {
        val value = schemaProperties[key] ?: return null
        val schema = parseSchemaElement(value, messageSink, updatedBaseUri, idLookup) ?: return null
        return factory(schema)
    }

    /**
     * Parses a keyword whose value must be a list of sub-schemas (e.g. `allOf`, `anyOf`, `oneOf`)
     * into a validator built by [factory]. Elements that fail to parse are skipped; the resulting
     * list contains only the successfully parsed schemas. Reports [SCHEMA_ARRAY_REQUIRED] when the
     * keyword is present but not a [KsonList].
     */
    private fun parseSchemaListValidator(
        schemaProperties: Map<String, KsonValue>,
        key: String,
        updatedBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
        factory: (List<JsonSchema>) -> JsonSchemaValidator,
    ): JsonSchemaValidator? {
        val value = schemaProperties[key] ?: return null
        if (value !is KsonList) {
            messageSink.error(value.location, SCHEMA_ARRAY_REQUIRED.create(key))
            return null
        }
        val entries = ArrayList<JsonSchema>()
        for (element in value.elements) {
            entries.add(parseSchemaElement(element, messageSink, updatedBaseUri, idLookup) ?: continue)
        }
        return factory(entries)
    }

    /**
     * Outcome of the `$ref` short-circuit at the top of [parseObjectSchema].
     *
     * - [NotPresent]: no `$ref` keyword (or `$ref` was present but non-string, which was already
     *   reported). The caller continues parsing the remaining keywords.
     * - [Resolved]: `$ref` was a valid string that resolved; the caller returns [schema] directly
     *   without evaluating any other keyword.
     * - [Failed]: `$ref` was a string that failed to resolve; the error was reported and the
     *   caller returns `null`.
     */
    private sealed class RefOutcome {
        object NotPresent : RefOutcome()
        data class Resolved(val schema: JsonSchema) : RefOutcome()
        object Failed : RefOutcome()
    }

    /**
     * Handles the `$ref` short-circuit branch at the top of [parseObjectSchema]. See [RefOutcome]
     * for the three-way outcome.
     */
    private fun tryResolveRef(
        schemaProperties: Map<String, KsonValue>,
        currentBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
    ): RefOutcome {
        val refString = schemaProperties["\$ref"] ?: return RefOutcome.NotPresent
        if (refString !is KsonString) {
            messageSink.error(refString.location, SCHEMA_STRING_REQUIRED.create("\$ref"))
            return RefOutcome.NotPresent
        }
        val resolvedValue = idLookup.resolveRef(refString.value, currentBaseUri)
        if (resolvedValue == null) {
            messageSink.error(refString.location, SCHEMA_REF_RESOLUTION_FAILED.create(refString.value))
            return RefOutcome.Failed
        }
        val refValidator = RefValidator(resolvedValue, idLookup)
        return RefOutcome.Resolved(
            JsonObjectSchema(null, refString.value, null, null, null, null, listOf(refValidator))
        )
    }

    /**
     * The user-facing and nav metadata keywords parsed once at the top of a schema object.
     * Grouped to keep [parseObjectSchema] focused on validator assembly.
     */
    private data class SchemaMetadata(
        val title: String?,
        val description: String?,
        val comment: String?,
        val default: KsonValue?,
        val definitions: Map<KsonString, JsonSchema?>?,
    )

    /**
     * Parses `title`, `description`, `$comment`, `default`, and `definitions`. `title` accepts
     * only [KsonString]; `description` and `$comment` additionally accept [EmbedBlock].
     * `definitions` recursively parses each sub-schema.
     */
    private fun parseMetadata(
        schemaProperties: Map<String, KsonValue>,
        updatedBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
    ): SchemaMetadata {
        val title = schemaProperties["title"]?.let { titleValue ->
            if (titleValue is KsonString) {
                titleValue.value
            } else {
                messageSink.error(titleValue.location, SCHEMA_STRING_REQUIRED.create("title"))
                null
            }
        }
        val description = parseStringOrEmbedBlock(schemaProperties, "description", messageSink)
        val comment = parseStringOrEmbedBlock(schemaProperties, "\$comment", messageSink)
        val default = schemaProperties["default"]
        val definitions = schemaProperties["definitions"]?.let { definitionsValue ->
            if (definitionsValue is KsonObject) {
                definitionsValue.propertyMap.entries.associate { (_, value) ->
                    value.propName to parseSchemaElement(value.propValue, messageSink, updatedBaseUri, idLookup)
                }
            } else {
                messageSink.error(definitionsValue.location, SCHEMA_OBJECT_REQUIRED.create("definitions"))
                null
            }
        }
        return SchemaMetadata(title, description, comment, default, definitions)
    }

    /**
     * Parses a keyword whose value may be either a [KsonString] or an [EmbedBlock], returning the
     * raw string content. Reports [SCHEMA_STRING_OR_EMBED_BLOCK_REQUIRED] for any other type.
     */
    private fun parseStringOrEmbedBlock(
        schemaProperties: Map<String, KsonValue>,
        key: String,
        messageSink: MessageSink,
    ): String? {
        val value = schemaProperties[key] ?: return null
        return when (value) {
            is KsonString -> value.value
            is EmbedBlock -> value.embedContent.value
            else -> {
                messageSink.error(value.location, SCHEMA_STRING_OR_EMBED_BLOCK_REQUIRED.create(key))
                null
            }
        }
    }

    /**
     * Parses the `type` keyword. Supports a single type string or an array of type strings; any
     * non-string array entry is reported as [SCHEMA_TYPE_ARRAY_ENTRY_ERROR], any non-string/array
     * value as [SCHEMA_TYPE_TYPE_ERROR].
     */
    private fun parseTypeValidator(
        schemaProperties: Map<String, KsonValue>,
        propertyName: String?,
        messageSink: MessageSink,
    ): TypeValidator? {
        val typeValue = schemaProperties["type"] ?: return null
        return when (typeValue) {
            is KsonString -> TypeValidator(typeValue.value, propertyName)
            is KsonList -> {
                val typeArrayEntries = ArrayList<String>()
                for (element in typeValue.elements) {
                    if (element is KsonString) {
                        typeArrayEntries.add(element.value)
                    } else {
                        messageSink.error(element.location, SCHEMA_TYPE_ARRAY_ENTRY_ERROR.create())
                    }
                }
                TypeValidator(typeArrayEntries, propertyName)
            }
            else -> {
                messageSink.error(typeValue.location, SCHEMA_TYPE_TYPE_ERROR.create())
                null
            }
        }
    }

    /**
     * Parses the `properties` / `patternProperties` / `additionalProperties` cluster. These three
     * keywords coordinate to produce at most one [PropertiesValidator]. Returns `null` when none of
     * the three keywords are present. [title] is the schema's `title`, forwarded to
     * [AdditionalPropertiesBooleanValidator] when `additionalProperties` is a boolean.
     */
    private fun parsePropertyValidators(
        schemaProperties: Map<String, KsonValue>,
        title: String?,
        updatedBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
    ): JsonSchemaValidator? {
        val propertySchemas = schemaProperties["properties"]?.let { properties ->
            if (properties is KsonObject) {
                properties.propertyMap.entries.associate { (_, value) ->
                    value.propName to parseSchemaElement(value.propValue, messageSink, updatedBaseUri, idLookup, value.propName.value)
                }
            } else {
                messageSink.error(properties.location, SCHEMA_OBJECT_REQUIRED.create("properties"))
                null
            }
        }

        val compiledPatterns = schemaProperties["patternProperties"]?.let { patternProperties ->
            if (patternProperties is KsonObject) {
                val validEntries = mutableListOf<CompiledPatternSchema>()
                for ((_, prop) in patternProperties.propertyMap.entries) {
                    runCatching { Regex(prop.propName.value) }
                        .onSuccess { regex ->
                            val schema = parseSchemaElement(prop.propValue, messageSink, updatedBaseUri, idLookup)
                            validEntries.add(CompiledPatternSchema(regex, schema))
                        }
                        .onFailure { messageSink.error(prop.propName.location, SCHEMA_INVALID_REGEX.create("patternProperties", prop.propName.value)) }
                }
                validEntries
            } else {
                messageSink.error(patternProperties.location, SCHEMA_OBJECT_REQUIRED.create("patternProperties"))
                null
            }
        }

        val additionalPropertiesValidator = schemaProperties["additionalProperties"]?.let { additionalProperties ->
            when (additionalProperties) {
                is KsonBoolean -> AdditionalPropertiesBooleanValidator(additionalProperties.value, title)
                else -> parseSchemaElement(additionalProperties, messageSink, updatedBaseUri, idLookup)
                    ?.let { AdditionalPropertiesSchemaValidator(it) }
            }
        }

        return if (propertySchemas != null || compiledPatterns != null || additionalPropertiesValidator != null) {
            PropertiesValidator(propertySchemas, compiledPatterns, additionalPropertiesValidator)
        } else {
            null
        }
    }

    /**
     * Parses the `pattern` keyword. The value must be a [KsonString] containing a valid regex.
     * An invalid regex is reported as [SCHEMA_INVALID_REGEX]; a non-string value as
     * [SCHEMA_STRING_REQUIRED].
     */
    private fun parsePatternValidator(
        schemaProperties: Map<String, KsonValue>,
        messageSink: MessageSink,
    ): JsonSchemaValidator? {
        val pattern = schemaProperties["pattern"] ?: return null
        if (pattern !is KsonString) {
            messageSink.error(pattern.location, SCHEMA_STRING_REQUIRED.create("pattern"))
            return null
        }
        return runCatching { Regex(pattern.value) }
            .onFailure { messageSink.error(pattern.location, SCHEMA_INVALID_REGEX.create("pattern", pattern.value)) }
            .getOrNull()
            ?.let(::PatternValidator)
    }

    /**
     * Parses the `enum` keyword. The value must be a [KsonList]; non-list values are reported as
     * [SCHEMA_ARRAY_REQUIRED].
     */
    private fun parseEnumValidator(
        schemaProperties: Map<String, KsonValue>,
        messageSink: MessageSink,
    ): JsonSchemaValidator? {
        val enum = schemaProperties["enum"] ?: return null
        if (enum !is KsonList) {
            messageSink.error(enum.location, SCHEMA_ARRAY_REQUIRED.create("enum"))
            return null
        }
        return EnumValidator(enum)
    }

    /**
     * Parses the `uniqueItems` keyword. The value must be a [KsonBoolean]; non-boolean values are
     * reported as [SCHEMA_BOOLEAN_REQUIRED].
     */
    private fun parseUniqueItemsValidator(
        schemaProperties: Map<String, KsonValue>,
        messageSink: MessageSink,
    ): JsonSchemaValidator? {
        val uniqueItems = schemaProperties["uniqueItems"] ?: return null
        if (uniqueItems !is KsonBoolean) {
            messageSink.error(uniqueItems.location, SCHEMA_BOOLEAN_REQUIRED.create("uniqueItems"))
            return null
        }
        return UniqueItemsValidator(uniqueItems.value)
    }

    /**
     * Parses the `items` keyword. A [KsonList] value defines a tuple schema; any other value is
     * treated as a single schema applied to every item. Both paths pair with `additionalItems`
     * via [parseAdditionalItemsValidator] to build one [ItemsValidator].
     */
    private fun parseItemsValidator(
        schemaProperties: Map<String, KsonValue>,
        updatedBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
    ): JsonSchemaValidator? {
        val itemsValue = schemaProperties["items"] ?: return null
        if (itemsValue is KsonList) {
            val tupleSchemas = itemsValue.elements.mapNotNull {
                parseSchemaElement(it, messageSink, updatedBaseUri, idLookup)
            }
            val additionalItems = parseAdditionalItemsValidator(
                schemaProperties, tupleSchemas.size, messageSink, updatedBaseUri, idLookup
            )
            return ItemsValidator(LeadingItemsTupleValidator(tupleSchemas), additionalItems)
        }
        val itemsSchema = parseSchemaElement(itemsValue, messageSink, updatedBaseUri, idLookup) ?: return null
        val additionalItems = parseAdditionalItemsValidator(
            schemaProperties, 0, messageSink, updatedBaseUri, idLookup
        )
        return ItemsValidator(LeadingItemsSchemaValidator(itemsSchema), additionalItems)
    }

    /**
     * Parses the `required` keyword. The value must be a [KsonList] of [KsonString] property
     * names; non-string entries are reported as [SCHEMA_STRING_ARRAY_ENTRY_ERROR], a non-list
     * value as [SCHEMA_ARRAY_REQUIRED].
     */
    private fun parseRequiredValidator(
        schemaProperties: Map<String, KsonValue>,
        messageSink: MessageSink,
    ): JsonSchemaValidator? {
        val required = schemaProperties["required"] ?: return null
        if (required !is KsonList) {
            messageSink.error(required.location, SCHEMA_ARRAY_REQUIRED.create("required"))
            return null
        }
        val requiredArrayEntries = ArrayList<KsonString>()
        for (element in required.elements) {
            if (element is KsonString) {
                requiredArrayEntries.add(element)
            } else {
                messageSink.error(element.location, SCHEMA_STRING_ARRAY_ENTRY_ERROR.create("required"))
            }
        }
        return RequiredValidator(requiredArrayEntries)
    }

    /**
     * Parses the coordinated `if` / `then` / `else` trio. A missing or unparseable `if` schema
     * short-circuits (no validator is added); `then` and `else` are optional and may be absent.
     */
    private fun parseIfThenElseValidator(
        schemaProperties: Map<String, KsonValue>,
        updatedBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
    ): JsonSchemaValidator? {
        val ifElement = schemaProperties["if"] ?: return null
        val ifSchema = parseSchemaElement(ifElement, messageSink, updatedBaseUri, idLookup) ?: return null
        val thenSchema = schemaProperties["then"]?.let {
            parseSchemaElement(it, messageSink, updatedBaseUri, idLookup)
        }
        val elseSchema = schemaProperties["else"]?.let {
            parseSchemaElement(it, messageSink, updatedBaseUri, idLookup)
        }
        return IfValidator(ifSchema, thenSchema, elseSchema)
    }

    /**
     * Parses the `dependencies` keyword. Each entry in the dependencies object is either a list of
     * property names (array form) or a sub-schema; non-string entries in the array form are
     * reported as [SCHEMA_DEPENDENCIES_ARRAY_STRING_REQUIRED]. A non-object `dependencies` value
     * is reported as [SCHEMA_OBJECT_REQUIRED].
     */
    private fun parseDependenciesValidator(
        schemaProperties: Map<String, KsonValue>,
        updatedBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
    ): JsonSchemaValidator? {
        val dependencies = schemaProperties["dependencies"] ?: return null
        if (dependencies !is KsonObject) {
            messageSink.error(dependencies.location, SCHEMA_OBJECT_REQUIRED.create("dependencies"))
            return null
        }
        val dependencyMap = buildMap<String, DependencyValidator> {
            dependencies.propertyLookup.forEach { (key, value) ->
                parseSingleDependency(value, updatedBaseUri, idLookup, messageSink)?.let { put(key, it) }
            }
        }
        return DependenciesValidator(dependencyMap)
    }

    /**
     * Parses one entry of the `dependencies` map: a [KsonList] of property names produces a
     * [DependencyValidatorArray]; any other value is parsed as a sub-schema and wrapped in a
     * [DependencyValidatorSchema]. Returns `null` when the sub-schema fails to parse so the key
     * is simply skipped.
     */
    private fun parseSingleDependency(
        value: KsonValue,
        updatedBaseUri: String,
        idLookup: SchemaIdLookup,
        messageSink: MessageSink,
    ): DependencyValidator? {
        if (value is KsonList) {
            val dependencyArrayEntries = mutableSetOf<KsonString>()
            for (element in value.elements) {
                if (element is KsonString) {
                    dependencyArrayEntries.add(element)
                } else {
                    messageSink.error(element.location, SCHEMA_DEPENDENCIES_ARRAY_STRING_REQUIRED.create())
                }
            }
            return DependencyValidatorArray(dependencyArrayEntries)
        }
        val depSchema = parseSchemaElement(value, messageSink, updatedBaseUri, idLookup) ?: return null
        return DependencyValidatorSchema(depSchema)
    }

    private fun parseAdditionalItemsValidator(
        schemaProperties: Map<String, KsonValue>,
        tupleLength: Int,
        messageSink: MessageSink,
        currentBaseUri: String,
        idLookup: SchemaIdLookup
    ): AdditionalItemsValidator? {
        return schemaProperties["additionalItems"]?.let { additionalItems ->
            when (additionalItems) {
                is KsonBoolean -> AdditionalItemsBooleanValidator(additionalItems.value, tupleLength)
                else -> parseSchemaElement(
                    additionalItems,
                    messageSink,
                    currentBaseUri,
                    idLookup
                )?.let { AdditionalItemsSchemaValidator(it) }
            }
        }
    }
}

/** Adds [element] to the receiver only when it is non-null. */
private fun <T : Any> MutableCollection<T>.addIfNotNull(element: T?) {
    if (element != null) add(element)
}

/**
 * Extract the `$id` property from the given [KsonObject] if it has one, ensuring it is of type string
 */
private fun extractId(schemaObject: KsonObject, messageSink: MessageSink): String? {
    return schemaObject.propertyLookup["\$id"]?.let { idValue ->
        if (idValue is KsonString) {
            idValue.value
        } else {
            messageSink.error(idValue.location, SCHEMA_STRING_REQUIRED.create("\$id"))
            null
        }
    }
}

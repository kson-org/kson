package org.kson.schema

import org.kson.parser.*
import org.kson.parser.messages.MessageType.*
import org.kson.schema.validators.*
import org.kson.uri.UriReference
import org.kson.value.*

/**
 * Parses a JSON Schema string into our JsonSchema model.
 */
internal object SchemaParser {
    /**
     * Parse the root of [indexedDocuments] as a complete JSON Schema, not a sub-schema.
     * See [parseSchemaElement] for sub-schema parsing.
     */
    fun parseSchemaRoot(indexedDocuments: IndexedSchemaDocuments, messageSink: MessageSink): JsonSchema? =
        parseSchemaElement(indexedDocuments.root.value, messageSink, indexedDocuments.rootBaseUri, indexedDocuments)

    /**
     *  Parse [schemaValue] as a JSON sub-schema. See [parseSchemaRoot] for full schema parsing.
     *
     *  @param schemaValue The schema value to parse
     *  @param messageSink The message sink for errors
     *  @param currentBaseUri The current base URI context for resolving references
     *  @param indexedDocuments The indexed documents for resolving $id and $ref references
     */
    fun parseSchemaElement(
        schemaValue: KsonValue,
        messageSink: MessageSink,
        currentBaseUri: UriReference,
        indexedDocuments: IndexedSchemaDocuments,
        propertyName: String? = null
    ): JsonSchema? {
        return when (schemaValue) {
            is KsonBoolean -> JsonBooleanSchema(schemaValue.value)
            is KsonObject -> parseObjectSchema(schemaValue, messageSink, currentBaseUri, indexedDocuments, propertyName = propertyName)
            else -> {
                messageSink.error(schemaValue.location, SCHEMA_OBJECT_OR_BOOLEAN.create())
                null
            }
        }
    }

    private fun parseObjectSchema(
        schemaObject: KsonObject,
        messageSink: MessageSink,
        currentBaseUri: UriReference,
        indexedDocuments: IndexedSchemaDocuments,
        propertyName: String? = null
    ): JsonSchema? {
        val schemaProperties = schemaObject.propertyLookup
        val updatedBaseUri = currentBaseUri.withIdOf(schemaObject)
        val context = KeywordContext(schemaObject, messageSink, updatedBaseUri, indexedDocuments)

        // `$ref` comes first, as Draft 7 ignores every keyword beside a string `$ref`
        val refValidator = compileKeyword(RefKeyword, context, updatedBaseUri, indexedDocuments)
        if (context.isNoSchema) return null
        if (schemaProperties[$$"$ref"] is KsonString) {
            return JsonObjectSchema(null, null, null, null, null, listOfNotNull(refValidator))
        }

        reportNonStringId(schemaObject, messageSink)
        val metadata = parseMetadata(schemaProperties, messageSink)
        val typeValidator = parseTypeValidator(schemaProperties, propertyName, messageSink)
        val validators = listOfNotNull(refValidator) + draft7Keywords.filter { it !== RefKeyword }.mapNotNull {
            compileKeyword(it, context, updatedBaseUri, indexedDocuments)
        }

        return JsonObjectSchema(
            metadata.title, metadata.description, metadata.comment, metadata.default,
            typeValidator, validators
        )
    }

    /**
     * Compiles [keyword]'s validator for the schema in [context], or returns null when that schema has none of the
     * keyword's properties or the keyword adds no validator.  [baseUri] is the base URI inside that schema, which its
     * subschemas inherit.
     */
    private fun compileKeyword(
        keyword: SchemaKeyword,
        context: KeywordContext,
        baseUri: UriReference,
        indexedDocuments: IndexedSchemaDocuments
    ): JsonSchemaValidator? {
        if (keyword.names.none { it in context.properties }) return null
        val compilableSubschemas = keyword.declaredSubschemas(context.schema).map { declared ->
            val propertyName = (declared.applies as? Applies.Property)?.name
            CompilableSubschema(declared) {
                parseSchemaElement(declared.value, context.messageSink, baseUri, indexedDocuments, propertyName)
            }
        }
        return keyword.compile(context, compilableSubschemas)
    }

    /** Reports why [unresolvedRef] couldn't be followed */
    internal fun reportUnresolvedRef(unresolvedRef: UnresolvedRef, messageSink: MessageSink) {
        val ref = unresolvedRef.ref
        val message = when (unresolvedRef) {
            is UnresolvedRef.MissingTarget -> when (val failure = unresolvedRef.failure) {
                is RefResolution.NotFound -> SCHEMA_REF_RESOLUTION_FAILED.create(ref.value)
                is RefResolution.MalformedPointer -> failure.message
            }
            is UnresolvedRef.Cycle -> SCHEMA_REF_CYCLE.create(ref.value)
        }
        messageSink.error(ref.location, message)
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
    )

    /**
     * Parses `title`, `description`, `$comment` and `default`. `title` accepts only [KsonString];
     * `description` and `$comment` additionally accept [EmbedBlock].
     */
    private fun parseMetadata(
        schemaProperties: Map<String, KsonValue>,
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
        val comment = parseStringOrEmbedBlock(schemaProperties, $$"$comment", messageSink)
        val default = schemaProperties["default"]
        return SchemaMetadata(title, description, comment, default)
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
}

/** Reports an `$id` that isn't a string, as JSON Schema requires it to be one */
private fun reportNonStringId(schemaObject: KsonObject, messageSink: MessageSink) {
    val idValue = schemaObject.propertyLookup[$$"$id"] ?: return
    if (idValue !is KsonString) {
        messageSink.error(idValue.location, SCHEMA_STRING_REQUIRED.create($$"$id"))
    }
}

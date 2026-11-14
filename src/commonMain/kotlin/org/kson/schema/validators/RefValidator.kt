package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.schema.*
import org.kson.schema.SchemaIdLookup.Companion.parseUri
import org.kson.validation.SourceContext
import org.kson.value.navigation.json_pointer.JsonPointer

/**
 * Validator for JSON Schema `$ref` references
 *
 * @param [resolvedRef] the [ResolvedRef] object for this $ref
 * @param [idLookup] the IdSchemaLookup for resolving nested $ref references within the referenced schema
 * @param [refString] the original `$ref` string (e.g. `"#/$defs/TaskModel"`), kept to name the target
 *   when it declares no `title`
 */
class RefValidator(
    private val resolvedRef: ResolvedRef,
    private val idLookup: SchemaIdLookup,
    private val refString: String
) : JsonSchemaValidator {
    private val refParseResult: Pair<JsonSchema?, List<LoggedMessage>> by lazy {
        val parseMessageSink = MessageSink()
        // TODO these parsed $ref schemas should be cached for efficiency
        val schema = SchemaParser.parseSchemaElement(
            resolvedRef.resolvedValue,
            parseMessageSink,
            resolvedRef.resolvedValueBaseUri,
            idLookup)
        schema to parseMessageSink.loggedMessages()
    }

    /**
     * A short name for the referenced schema: its `title`, else the last JSON Pointer token of
     * [refString] (`#/$defs/TaskModel` -> `TaskModel`), else `null`.
     */
    fun refShortName(): String? = targetTitle() ?: pointerTail(refString)

    /** The schema this `$ref` resolves to, or `null` if the target failed to parse. */
    internal fun resolvedSchema(): JsonSchema? = refParseResult.first

    private fun targetTitle(): String? = (resolvedSchema() as? JsonObjectSchema)?.title

    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        val (schema, parseErrors) = refParseResult

        if (schema == null) {
            parseErrors.forEach { messageSink.error(it.location, it.message) }
            return
        }

        schema.validate(ksonValue, messageSink, sourceContext)
    }
}

/** The last token of a `$ref` fragment that is a rooted JSON Pointer, else `null`. */
private fun pointerTail(refString: String): String? {
    val fragment = parseUri(refString).fragment
    if (!fragment.startsWith("#/")) return null
    val tokens = runCatching { JsonPointer(fragment.removePrefix("#")).tokens }.getOrNull() ?: return null
    return tokens.lastOrNull()?.ifBlank { null }
}

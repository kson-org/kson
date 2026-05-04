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
 * @param [refString] the original `$ref` string (e.g. `"#/$defs/TaskModel"`), retained so this validator
 *   can produce a short human-friendly name for the target even when the target declares no `title`.
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
     * A short, human-recognizable name for the referenced schema, or `null` if none is available.
     *
     * Preference order:
     *   1. the `title` declared on the target schema (via [targetTitle])
     *   2. the last JSON Pointer token of [refString]'s fragment — e.g. `#/$defs/TaskModel` -> `TaskModel`
     *
     * Used by [JsonObjectSchema.descriptionWithDefault] to name schemas whose only validator is a `$ref`
     * (including each branch of a `oneOf`/`anyOf`/`allOf` combinator).
     */
    fun refShortName(): String? = targetTitle() ?: pointerTail(refString)

    /**
     * The `title` declared on the ref target schema, or `null` if the target is not a titled object schema.
     * Reads through the parsed target schema so we share [SchemaParser]'s interpretation rather than
     * re-navigating the raw [KsonValue].
     */
    private fun targetTitle(): String? {
        val (schema, _) = refParseResult
        return (schema as? JsonObjectSchema)?.title
    }

    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        val (schema, parseErrors) = refParseResult

        if (schema == null) {
            // Schema parsing failed — forward the parse errors so callers know the $ref target is broken
            parseErrors.forEach { messageSink.error(it.location, it.message) }
            return
        }

        // Validate the value against our referenced schema
        schema.validate(ksonValue, messageSink)
    }
}

/**
 * Extract the last JSON Pointer token from a `$ref` string's fragment.  Returns `null` when the
 * fragment is empty, blank, not a rooted JSON Pointer (i.e. doesn't start with `#/`), or fails to
 * parse — those shapes have no meaningful tail token to use as a name.
 */
private fun pointerTail(refString: String): String? {
    val fragment = parseUri(refString).fragment
    if (!fragment.startsWith("#/")) return null
    val tokens = runCatching { JsonPointer(fragment.removePrefix("#")).tokens }.getOrNull() ?: return null
    return tokens.lastOrNull()?.ifBlank { null }
}

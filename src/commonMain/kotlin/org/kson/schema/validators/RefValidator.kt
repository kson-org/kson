package org.kson.schema.validators

import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.schema.*
import org.kson.uri.UriReference
import org.kson.validation.SourceContext
import org.kson.value.navigation.json_pointer.FragmentPointer
import org.kson.value.navigation.json_pointer.fragmentPointer

/**
 * Validator for JSON Schema `$ref` references
 *
 * @param [target] the schema this `$ref` resolves to
 * @param [ref] the `$ref` itself, whose string (e.g. `"#/$defs/TaskModel"`) names the target when the target
 *   declares no `title`, and names this `$ref` in cycle messages
 * @param [sameInstanceSchemas] the schemas on the way to this `$ref` that validate the same instance, including the
 *   schema that contains this `$ref`
 */
internal class RefValidator(
    private val target: SchemaNode,
    private val ref: KsonString,
    private val sameInstanceSchemas: SameInstanceSchemas
) : JsonSchemaValidator {
    private val chainEnd: SchemaNode by lazy { target.indexedDocuments.resolveRefIfPresent(target) }

    private val refParseResult: Pair<JsonSchema?, List<LoggedMessage>> by lazy {
        val parseMessageSink = MessageSink()
        // TODO these parsed $ref schemas should be cached for efficiency
        val unresolvedRef = unresolvedRef()
        val schema = if (unresolvedRef != null) {
            SchemaParser.reportUnresolvedRef(unresolvedRef, parseMessageSink)
            null
        } else {
            SchemaParser.parseSchemaElement(chainEnd, parseMessageSink, sameInstanceSchemas = sameInstanceSchemas)
        }
        schema to parseMessageSink.loggedMessages()
    }

    private val targetPins: Set<KsonValue>? by lazy { (resolvedSchema() as? JsonObjectSchema)?.pinnedValues() }

    /** Why this `$ref` leads to no schema to apply, if it doesn't */
    private fun unresolvedRef(): UnresolvedRef? {
        val leadsBack = chainEnd.value in sameInstanceSchemas
        return chainEnd.unresolvedRef ?: UnresolvedRef.Cycle(ref).takeIf { leadsBack }
    }

    override fun pinnedValues(): Set<KsonValue>? = targetPins

    /**
     * A short name for the referenced schema: its `title`, else the last JSON Pointer token of
     * [ref] (`#/$defs/TaskModel` -> `TaskModel`), else `null`.
     */
    fun refShortName(): String? = targetTitle() ?: pointerTail(ref.value)

    /** The schema this `$ref` applies, or `null` if it can't be followed to one or that schema fails to parse. */
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
private fun pointerTail(ref: String): String? {
    val pointer = (UriReference.parse(ref).fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return null
    return pointer.tokens.lastOrNull()?.ifBlank { null }
}

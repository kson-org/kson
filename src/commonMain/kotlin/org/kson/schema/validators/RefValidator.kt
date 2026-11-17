package org.kson.schema.validators

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
 * @param [refString] the original `$ref` string (e.g. `"#/$defs/TaskModel"`), kept to name the target
 *   when it declares no `title`
 */
internal class RefValidator(
    private val target: SchemaNode,
    private val refString: String
) : JsonSchemaValidator {
    private val refParseResult: Pair<JsonSchema?, List<LoggedMessage>> by lazy {
        val parseMessageSink = MessageSink()
        // TODO these parsed $ref schemas should be cached for efficiency
        val chainEnd = target.indexedDocuments.resolveRefIfPresent(target)
        val unresolvedRef = chainEnd.unresolvedRef
        val schema = if (unresolvedRef != null) {
            SchemaParser.reportUnresolvedRef(unresolvedRef, parseMessageSink)
            null
        } else {
            SchemaParser.parseSchemaElement(chainEnd, parseMessageSink)
        }
        schema to parseMessageSink.loggedMessages()
    }

    // the whole chain is resolved at once: parsing it hop by hop would never notice a cycle
    private val targetPins: Set<KsonValue>? by lazy {
        val chainEnd = target.indexedDocuments.resolveRefIfPresent(target)
        if (chainEnd.endsAtUnresolvedRef) return@lazy null
        (SchemaParser.parseSchemaElement(chainEnd, MessageSink()) as? JsonObjectSchema)?.pinnedValues()
    }

    override fun pinnedValues(): Set<KsonValue>? = targetPins

    /**
     * A short name for the referenced schema: its `title`, else the last JSON Pointer token of
     * [refString] (`#/$defs/TaskModel` -> `TaskModel`), else `null`.
     */
    fun refShortName(): String? = targetTitle() ?: pointerTail(refString)

    /** The schema this `$ref` resolves to, or `null` if the target failed to parse. */
    internal fun resolvedSchema(): JsonSchema? = refParseResult.first

    /**
     * Each `$ref` parses its own copy of its target, so only this can tell that two lead to the same schema.
     * The target's value alone identifies that schema (see [SchemaNode.baseUri]).
     */
    internal fun hasSameTargetAs(other: RefValidator): Boolean =
        target.value === other.target.value

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
    val pointer = (UriReference.parse(refString).fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return null
    return pointer.tokens.lastOrNull()?.ifBlank { null }
}

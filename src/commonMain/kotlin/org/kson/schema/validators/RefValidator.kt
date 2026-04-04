package org.kson.schema.validators

import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.schema.*
import org.kson.validation.SourceContext
import org.kson.value.KsonValue

/**
 * Validator for JSON Schema `$ref` references
 *
 * @param [resolvedRef] the [ResolvedRef] object for this $ref
 * @param [idLookup] the IdSchemaLookup for resolving nested $ref references within the referenced schema
 */
class RefValidator(
    private val resolvedRef: ResolvedRef,
    private val idLookup: SchemaIdLookup,
) : JsonSchemaValidator {
    private val refParseResult: Pair<JsonSchema?, List<LoggedMessage>> by lazy {
        val parseMessageSink = MessageSink()
        // TODO these parsed $ref schemas should be cached for efficiency
        val schema =
            SchemaParser.parseSchemaElement(
                resolvedRef.resolvedValue,
                parseMessageSink,
                resolvedRef.resolvedValueBaseUri,
                idLookup,
            )
        schema to parseMessageSink.loggedMessages()
    }

    override fun validate(
        ksonValue: KsonValue,
        messageSink: MessageSink,
        sourceContext: SourceContext,
    ) {
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

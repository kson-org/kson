package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.schema.*

/**
 * Validator for JSON Schema `$ref` references
 *
 * @param [resolvedRef] the [ResolvedRef] object for this $ref
 * @param [idLookup] the IdSchemaLookup for resolving nested $ref references within the referenced schema
 */
class RefValidator(
    private val resolvedRef: ResolvedRef,
    private val idLookup: SchemaIdLookup
) : JsonSchemaValidator {
    private val refSchema: JsonSchema? by lazy {
        // Parse the resolved value as a schema with the appropriate base URI context
        val messageSink = MessageSink()
        // TODO these parsed $ref schemas should be cached for efficiency
        SchemaParser.parseSchemaElement(
            resolvedRef.resolvedValue,
            messageSink,
            resolvedRef.resolvedValueBaseUri,
            idLookup)
    }

    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val schema = refSchema
            ?: // Schema parsing failed, so can't perform validation against it
            return

        // Validate the value against our referenced schema
        schema.validate(ksonValue, messageSink)
    }
}

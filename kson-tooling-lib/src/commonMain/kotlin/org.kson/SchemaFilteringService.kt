@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.value.navigation.jsonPointer.JsonPointer
import org.kson.schema.ResolvedRef
import org.kson.schema.SchemaIdLookup
import org.kson.schema.SchemaParser
import org.kson.schema.SchemaResolutionType
import org.kson.value.navigation.KsonValueNavigation
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Service for filtering schemas based on validation against document content.
 *
 * This service handles the validation-based filtering logic for JSON Schema combinators
 * (oneOf/anyOf/allOf), ensuring that only compatible schemas are used for IDE features
 * like completions, hover info, and jump-to-definition.
 *
 * The filtering uses a "soft" validation approach: a schema is included if the existing
 * properties don't contradict it, even if required properties are missing.
 */
class SchemaFilteringService(private val schemaIdLookup: SchemaIdLookup) {

    /**
     * Get valid schemas for a document path, applying combinator expansion and filtering.
     *
     * This function:
     * 1. Expands combinator schemas (oneOf/anyOf/allOf) into individual branches
     * 2. Filters branches based on validation against the current document (for oneOf/anyOf)
     * 3. Returns all branches for allOf and direct properties (no filtering needed)
     *
     * @param candidateSchemas The schemas found at the document path
     * @param documentRoot The document being edited (KSON string)
     * @param documentPointer The [JsonPointer] to the location in the document
     * @return List of valid schemas after expansion and filtering
     */
    fun getValidSchemas(
        candidateSchemas: List<ResolvedRef>,
        documentRoot: String,
        documentPointer: JsonPointer
    ): List<ResolvedRef> {
        // Check if we need to filter based on combinators
        // This includes both schemas directly tagged as combinators AND schemas that contain combinator properties
        // Note: Only oneOf/anyOf require validation-based filtering.
        // allOf always includes all branches (no filtering needed).
        val hasCombinatorsThatRequireValidation = candidateSchemas.any { ref ->
            requiresValidationFiltering(ref)
        }

        // Always expand combinators to get individual branches
        val expandedSchemas = schemaIdLookup.expandCombinators(candidateSchemas)

        // Filter if needed (for oneOf/anyOf that require validation)
        return if (hasCombinatorsThatRequireValidation) {
            // Parse the document for validation
            val documentValue = KsonCore.parseToAst(documentRoot).ksonValue
            if (documentValue != null) {
                filterByValidation(expandedSchemas, documentValue, documentPointer)
            } else {
                // If document doesn't parse, fall back to unfiltered schemas
                expandedSchemas
            }
        } else {
            // No filtering needed, use all expanded schemas
            expandedSchemas
        }
    }

    /**
     * Checks if a schema reference requires validation-based filtering.
     *
     * Only oneOf/anyOf combinators require validation filtering. allOf combinators
     * always include all branches.
     *
     * @param ref The schema reference to check
     * @return true if the schema requires validation filtering
     */
    private fun requiresValidationFiltering(ref: ResolvedRef): Boolean {
        return ref.resolutionType == SchemaResolutionType.ANY_OF ||
            ref.resolutionType == SchemaResolutionType.ONE_OF ||
            (ref.resolvedValue as? org.kson.value.KsonObject)?.let { obj ->
                obj.propertyLookup.containsKey("oneOf") || obj.propertyLookup.containsKey("anyOf")
            } ?: false
    }

    /**
     * Filters schemas based on validation against the current document.
     *
     * For schemas resolved via combinators (anyOf/oneOf), this validates them against
     * the parent object to ensure only compatible schemas contribute completions.
     *
     * This uses a "soft" validation approach: a schema is included if the existing
     * properties don't contradict it, even if required properties are missing.
     *
     * @param candidateSchemas All schemas found at the document path
     * @param documentValue The parsed document
     * @param documentPointer The [JsonPointer] to the completion location
     * @return Filtered list of compatible schemas
     */
    private fun filterByValidation(
        candidateSchemas: List<ResolvedRef>,
        documentValue: org.kson.value.KsonValue,
        documentPointer: JsonPointer
    ): List<ResolvedRef> {
        // Get the object to validate against
        // For completions, we validate the object where we're adding properties
        val targetValue = KsonValueNavigation.navigateWithJsonPointer(documentValue, documentPointer) ?: documentValue

        return candidateSchemas.filter { ref ->
            when (ref.resolutionType) {
                // For anyOf/oneOf, check if the current document state is compatible
                SchemaResolutionType.ANY_OF,
                SchemaResolutionType.ONE_OF -> isSchemaValidForDocument(ref, targetValue)
                // For all other types (direct property, allOf, etc.), include them
                else -> true
            }
        }
    }

    /**
     * Checks if a schema is valid for the given document value.
     *
     * This method validates the schema against the document using a "soft" validation approach:
     * a schema is considered valid if the existing properties don't contradict it, even if
     * required properties are missing (which is expected during completion).
     *
     * @param ref The schema reference to validate
     * @param targetValue The document value to validate against
     * @return true if the schema is compatible with the document, false otherwise
     */
    private fun isSchemaValidForDocument(
        ref: ResolvedRef,
        targetValue: org.kson.value.KsonValue
    ): Boolean {
        val messageSink = MessageSink()
        val schema = SchemaParser.parseSchemaElement(
            ref.resolvedValue,
            messageSink,
            ref.resolvedValueBaseUri,
            schemaIdLookup
        )

        if (schema == null) {
            // If we can't parse the schema, include it (fail open)
            return true
        }

        // Check for validation errors
        // Important: we want to see if existing properties are COMPATIBLE,
        // not if the object is complete. So we check if validation produces errors
        // about existing properties (type mismatches, const violations, etc.)
        // vs. missing required properties.
        val errorCountBefore = messageSink.loggedMessages().size
        schema.validate(targetValue, messageSink)
        val errors = messageSink.loggedMessages().drop(errorCountBefore)

        // Filter out "required" errors - those are expected during completion
        val significantErrors = filterInsignificantErrors(errors)

        // Include this schema if there are no significant validation errors
        return significantErrors.isEmpty()
    }

    /**
     * Filters out insignificant validation errors that don't indicate incompatibility.
     *
     * During completion, missing required properties are expected and should not disqualify
     * a schema. We only care about errors that indicate incompatibility with existing properties.
     *
     * @param errors All validation errors
     * @return Only the errors that indicate real incompatibility
     */
    private fun filterInsignificantErrors(errors: List<org.kson.parser.LoggedMessage>): List<org.kson.parser.LoggedMessage> {
        return errors.filter { loggedMessage ->
            loggedMessage.message.type !in IGNORABLE_ERROR_TYPES
        }
    }

    companion object {
        /**
         * Error types that should be ignored during validation-based filtering.
         * These errors don't indicate incompatibility with existing properties.
         */
        private val IGNORABLE_ERROR_TYPES = setOf(
            MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING,
            MessageType.SCHEMA_MISSING_REQUIRED_DEPENDENCIES
        )
    }
}

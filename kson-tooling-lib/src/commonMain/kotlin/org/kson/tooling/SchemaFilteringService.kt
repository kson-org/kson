@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson.tooling

import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.schema.ResolvedRef
import org.kson.schema.SchemaIdLookup
import org.kson.schema.SchemaParser
import org.kson.schema.SchemaResolutionType
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.walker.KsonValueWalker
import org.kson.walker.navigateWithJsonPointer
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Service for filtering schemas based on validation against document content.
 *
 * This service handles the validation-based filtering logic for JSON Schema combinators
 * (oneOf/anyOf/allOf) and conditionals (if/then/else), ensuring that only compatible
 * schemas are used for IDE features like completions, hover info, and jump-to-definition.
 *
 * The filtering uses a "soft" validation approach: a schema is included if the existing
 * properties don't contradict it, even if required properties are missing.
 */
class SchemaFilteringService(private val schemaIdLookup: SchemaIdLookup) {

    /**
     * Get valid schemas for a document path, applying expansion and filtering.
     *
     * This function:
     * 1. Expands combinator schemas (oneOf/anyOf/allOf) and conditionals (if/then/else) into individual branches
     * 2. Filters branches based on validation against the current document (for oneOf/anyOf/if-then-else)
     * 3. Returns all branches for allOf and direct properties (no filtering needed)
     *
     * @param candidateSchemas The schemas found at the document path
     * @param documentValue The pre-parsed document value, or null if the document
     *   couldn't be parsed. When null, filtering is skipped and all
     *   expanded schemas are returned.
     * @param documentPointer The [JsonPointer] to the location in the document
     * @return List of valid schemas after expansion and filtering
     */
    fun getValidSchemas(
        candidateSchemas: List<ResolvedRef>,
        documentValue: KsonValue?,
        documentPointer: JsonPointer
    ): List<ResolvedRef> {
        val hasBranchesThatRequireValidation = candidateSchemas.any { ref ->
            requiresValidationFiltering(ref)
        }

        // Always expand to get individual branches
        val expandedSchemas = schemaIdLookup.expandCombinators(candidateSchemas)

        return if (hasBranchesThatRequireValidation && documentValue != null) {
            filterByValidation(expandedSchemas, documentValue, documentPointer)
        } else {
            expandedSchemas
        }
    }

    /**
     * Checks if a schema reference requires validation-based filtering.
     *
     * oneOf/anyOf combinators and if/then/else conditionals require validation filtering.
     * allOf combinators always include all branches (no filtering needed).
     *
     * @param ref The schema reference to check
     * @return true if the schema requires validation filtering
     */
    private fun requiresValidationFiltering(ref: ResolvedRef): Boolean {
        return ref.resolutionType in FILTERABLE_RESOLUTION_TYPES ||
            (ref.resolvedValue as? org.kson.value.KsonObject)?.let { obj ->
                obj.propertyLookup.containsKey("oneOf") ||
                obj.propertyLookup.containsKey("anyOf") ||
                obj.propertyLookup.containsKey("if")
            } ?: false
    }

    /**
     * Filters schemas based on validation against the current document.
     *
     * For schemas resolved via combinators (anyOf/oneOf) or conditionals (if/then/else),
     * this validates them against the document value to ensure only compatible schemas
     * contribute completions.
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
        documentValue: KsonValue,
        documentPointer: JsonPointer
    ): List<ResolvedRef> {
        // Get the value at the pointer location to validate against.
        // If navigation returns null, the value doesn't exist yet (e.g. the user is about
        // to type at an empty position). In that case there's nothing to filter against,
        // so return all expanded schemas.
        val targetValue = KsonValueWalker.navigateWithJsonPointer(documentValue, documentPointer)
            ?: return candidateSchemas

        val filtered = candidateSchemas.filter { ref ->
            when (ref.resolutionType) {
                // For anyOf/oneOf and if/then/else, check if the current document state is compatible
                SchemaResolutionType.ANY_OF,
                SchemaResolutionType.ONE_OF,
                SchemaResolutionType.IF_THEN,
                SchemaResolutionType.IF_ELSE -> isSchemaValidForDocument(ref, targetValue)
                // For all other types (direct property, allOf, etc.), include them
                else -> true
            }
        }

        // If filtering eliminated every filterable branch, the document value likely
        // doesn't match the expected shape yet (e.g. a list where objects are expected).
        // Fall back to unfiltered schemas so completions remain available.
        val filterableBranchesBefore = candidateSchemas.count { isFilterableBranch(it) }
        val filterableBranchesAfter = filtered.count { isFilterableBranch(it) }
        return if (filterableBranchesBefore > 0 && filterableBranchesAfter == 0) candidateSchemas else filtered
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
        targetValue: KsonValue
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
    private fun filterInsignificantErrors(errors: List<LoggedMessage>): List<LoggedMessage> {
        return errors.filter { loggedMessage ->
            loggedMessage.message.type !in IGNORABLE_ERROR_TYPES
        }
    }

    private fun isFilterableBranch(ref: ResolvedRef): Boolean =
        ref.resolutionType in FILTERABLE_RESOLUTION_TYPES

    companion object {
        /**
         * Error types that should be ignored during validation-based filtering.
         * These errors don't indicate incompatibility with existing properties.
         */
        private val IGNORABLE_ERROR_TYPES = setOf(
            MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING,
            MessageType.SCHEMA_MISSING_REQUIRED_DEPENDENCIES
        )

        /**
         * Resolution types that require validation-based filtering.
         * These are schema branches where multiple alternatives exist and
         * only compatible ones should be shown.
         */
        private val FILTERABLE_RESOLUTION_TYPES = setOf(
            SchemaResolutionType.ANY_OF,
            SchemaResolutionType.ONE_OF,
            SchemaResolutionType.IF_THEN,
            SchemaResolutionType.IF_ELSE
        )
    }
}

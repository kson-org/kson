@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson.tooling

import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.ResolvedRef
import org.kson.schema.SchemaIdLookup
import org.kson.schema.SchemaParser
import org.kson.schema.SchemaResolutionType
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.JsonPointer
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
     * Get valid schemas for a document path, applying expansion and two-pass filtering.
     *
     * This function:
     * 1. Expands combinator schemas (oneOf/anyOf/allOf) and conditionals (if/then/else) into individual branches
     * 2. Filters oneOf/anyOf branches by sibling property constraints (using [ToolingDocument.partialKsonValue])
     * 3. Filters remaining branches by leaf-level validation (using [ToolingDocument.ksonValue])
     *
     * The two passes use different document values because sibling filtering must work
     * even when the document has parse errors at the cursor position (where only the
     * partial value is available), while leaf validation needs the fully parsed tree.
     *
     * @param candidateSchemas The schemas found at the document path
     * @param document The parsed document providing both full and partial value trees
     * @param documentPointer The [JsonPointer] to the location in the document
     * @return List of valid schemas after expansion and filtering
     */
    fun getValidSchemas(
        candidateSchemas: List<ResolvedRef>,
        document: ToolingDocument,
        documentPointer: JsonPointer
    ): List<ResolvedRef> {
        val expandedSchemas = schemaIdLookup.expandCombinators(candidateSchemas)

        val partialDocumentValue = document.partialKsonValue
        val afterSiblingFilter = if (partialDocumentValue != null) {
            filterBySiblingCompatibility(expandedSchemas, partialDocumentValue, documentPointer)
        } else {
            expandedSchemas
        }

        val hasBranchesThatRequireValidation = afterSiblingFilter.any { ref ->
            requiresValidationFiltering(ref)
        }
        val documentValue = document.ksonValue
        return if (hasBranchesThatRequireValidation && documentValue != null) {
            filterByValidation(afterSiblingFilter, documentValue, documentPointer)
        } else {
            afterSiblingFilter
        }
    }

    /**
     * Filters oneOf/anyOf branches by sibling property compatibility.
     *
     * For schemas with [ResolvedRef.parentBranch] set, checks whether the parent
     * branch's property constraints (const/enum) are compatible with the document's
     * sibling values. Falls back to unfiltered schemas if all branches are eliminated.
     */
    private fun filterBySiblingCompatibility(
        schemas: List<ResolvedRef>,
        documentValue: KsonValue,
        documentPointer: JsonPointer
    ): List<ResolvedRef> {
        val hasParentBranches = schemas.any { it.parentBranch != null }
        if (!hasParentBranches) return schemas

        val filtered = schemas.filter { ref ->
            val parentBranch = ref.parentBranch
            parentBranch == null || isBranchCompatibleWithSiblings(parentBranch, documentValue, documentPointer)
        }

        val branchesBefore = schemas.count { it.parentBranch != null }
        val branchesAfter = filtered.count { it.parentBranch != null }
        return if (branchesBefore > 0 && branchesAfter == 0) schemas else filtered
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
            (ref.resolvedValue as? KsonObject)?.let { obj ->
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

        // Scalar targets are often an empty/placeholder value being typed into during
        // completion. Validating against a placeholder can spuriously eliminate every
        // enum/const branch, destroying the narrowing from sibling-aware navigation.
        // When that happens — all filterable branches eliminated for a scalar target —
        // fall back to the unfiltered set so value completions remain available.
        // Structural targets (objects, lists) reflect committed user intent; if no
        // branch matches, offering completions from incompatible shapes would mislead.
        val isScalarTarget = targetValue !is KsonObject && targetValue !is KsonList
        if (isScalarTarget) {
            val filterableBefore = candidateSchemas.count { it.resolutionType in FILTERABLE_RESOLUTION_TYPES }
            val filterableAfter = filtered.count { it.resolutionType in FILTERABLE_RESOLUTION_TYPES }
            if (filterableBefore > 0 && filterableAfter == 0) return candidateSchemas
        }
        return filtered
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

    /**
     * Checks if a oneOf/anyOf branch is compatible with the document's sibling properties.
     *
     * Uses the [ResolvedRef.parentBranch] to access the full branch schema, then checks
     * its property constraints (const/enum) against the document's existing values.
     * The property being completed (last token of [documentPointer]) is excluded since
     * its value is incomplete during completion.
     *
     * @param parentBranch The oneOf/anyOf branch that contained this result
     * @param documentValue The root document value
     * @param documentPointer Path to the property being completed
     */
    private fun isBranchCompatibleWithSiblings(
        parentBranch: ResolvedRef,
        documentValue: KsonValue,
        documentPointer: JsonPointer
    ): Boolean {
        val propertyBeingCompleted = documentPointer.tokens.lastOrNull() ?: return true
        val parentPointer = JsonPointer.fromTokens(documentPointer.tokens.dropLast(1))
        val parentDocValue = KsonValueWalker.navigateWithJsonPointer(documentValue, parentPointer)
            as? KsonObject ?: return true

        val branchSchema = parentBranch.resolvedValue as? KsonObject ?: return true
        val branchProperties = (branchSchema.propertyLookup["properties"] as? KsonObject)
            ?: return true

        for ((propName, propSchemaValue) in branchProperties.propertyLookup) {
            if (propName == propertyBeingCompleted) continue

            val docValue = parentDocValue.propertyLookup[propName] ?: continue
            val propSchema = schemaIdLookup.resolveRefIfPresent(propSchemaValue, parentBranch.resolvedValueBaseUri)
                .resolvedValue as? KsonObject ?: continue

            val constValue = propSchema.propertyLookup["const"]
            if (constValue != null && constValue != docValue) return false

            val enumList = propSchema.propertyLookup["enum"] as? KsonList
            if (enumList != null && docValue !in enumList.elements) return false
        }

        return true
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

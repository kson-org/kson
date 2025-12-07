@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.navigation.KsonValuePathBuilder
import org.kson.navigation.SchemaInformation
import org.kson.parser.Coordinates
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.ResolvedRef
import org.kson.schema.SchemaIdLookup
import org.kson.schema.SchemaParser
import org.kson.schema.SchemaResolutionType
import org.kson.value.KsonValueNavigation
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Tooling utilities for IDE features like hover information and completions.
 *
 * This module provides schema-aware navigation and introspection capabilities
 * for building IDE integrations.
 */
object KsonTooling {

    /**
     * Get schema information for a position in a document.
     *
     * This is a convenience method that finds the KsonValue at the given position
     * and then retrieves schema information for it.
     *
     * @param documentRoot The root of the document being edited (internal KsonValue)
     * @param schemaValue The schema for the document (internal KsonValue)
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return Formatted text, or null if no schema info available
     */
    fun getSchemaInfoAtLocation(
        documentRoot: String,
        schemaValue: String,
        line: Int,
        column: Int
    ): String? {
        val buildPath = KsonValuePathBuilder( documentRoot, Coordinates(line, column)).buildPathToPosition() ?: return null
        val schemaInfo = KsonCore.parseToAst(schemaValue).ksonValue.let{
            it ?: return null
            SchemaInformation.getSchemaInfo(it, buildPath)
        }

        return schemaInfo
    }

    /**
     * Get schema location for a position in a document.
     *
     * This is a convenience method that finds the KsonValue at the given position
     * and then returns its location in the schema document.
     *
     * @param documentRoot The root of the document being edited (KSON string)
     * @param schemaValue The schema for the document (KSON string)
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return DefinitionLocationResult with zero-based coordinates, or null if no schema info available
     */
    fun getSchemaLocationAtLocation(
        documentRoot: String,
        schemaValue: String,
        line: Int,
        column: Int
    ): List<Range>? {
        val buildPath = KsonValuePathBuilder( documentRoot, Coordinates(line, column)).buildPathToPosition(forDefinition = true) ?: return null
        val locations = KsonCore.parseToAst(schemaValue).ksonValue.let{
            it ?: return null
            SchemaInformation.getSchemaLocations(it, buildPath)
        }

        return locations.map {
            Range(
                it.start.line,
                it.start.column,
                it.end.line,
                it.end.column
            )
        }
    }

    /**
     * Get completion suggestions for a position in a document.
     *
     * This is a convenience method that finds the KsonValue at the given position
     * and then retrieves completion suggestions based on the schema.
     *
     * @param documentRoot The root of the document being edited (KSON string)
     * @param schemaValue The schema for the document (KSON string)
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return List of completion items, or null if no completions available
     */
    fun getCompletionsAtLocation(
        documentRoot: String,
        schemaValue: String,
        line: Int,
        column: Int
    ): List<CompletionItem>? {
        // Create a location from the position
        val position = Coordinates(line, column)

        val buildPath = KsonValuePathBuilder(documentRoot, position).buildPathToPosition() ?: return null

        // Parse the schema
        val parsedSchema = KsonCore.parseToAst(schemaValue).ksonValue ?: return null

        // Parse the document
        val parsedDocument = KsonCore.parseToAst(documentRoot).ksonValue

        // Get all candidate schemas at this path
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val candidateSchemas = schemaIdLookup.navigateByDocumentPath(buildPath)

        // Apply filtering to get only valid schemas
        val validSchemas = getValidSchemas(candidateSchemas, documentRoot, buildPath, schemaIdLookup)

        // Get completions from valid schemas, passing the document value to filter out already-filled properties
        val completions = SchemaInformation.getCompletions(parsedSchema, buildPath, validSchemas, parsedDocument)

        return completions.takeIf { it.isNotEmpty() }
    }

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
     * @param documentPath The path to the location in the document
     * @param schemaIdLookup The schema lookup for parsing schemas
     * @return List of valid schemas after expansion and filtering
     */
    private fun getValidSchemas(
        candidateSchemas: List<ResolvedRef>,
        documentRoot: String,
        documentPath: List<String>,
        schemaIdLookup: SchemaIdLookup
    ): List<ResolvedRef> {
        // Check if we need to filter based on combinators
        // This includes both schemas directly tagged as combinators AND schemas that contain combinator properties
        val needsFiltering = candidateSchemas.any { ref ->
            ref.resolutionType == SchemaResolutionType.ANY_OF ||
            ref.resolutionType == SchemaResolutionType.ONE_OF ||
            (ref.resolvedValue as? org.kson.value.KsonObject)?.let { obj ->
                obj.propertyLookup.containsKey("oneOf") || obj.propertyLookup.containsKey("anyOf")
            } ?: false
        }

        // Always expand combinators to get individual branches
        val expandedSchemas = expandCombinatorSchemas(candidateSchemas)

        // Filter if needed (for oneOf/anyOf that require validation)
        val validSchemas = if (needsFiltering) {
            // Parse the document for validation
            val documentValue = KsonCore.parseToAst(documentRoot).ksonValue
            if (documentValue != null) {
                filterValidSchemas(expandedSchemas, documentValue, buildPath, schemaIdLookup)
            } else {
                // If document doesn't parse, fall back to unfiltered schemas
                expandedSchemas
            }
        } else {
            // No filtering needed, use all expanded schemas
            expandedSchemas
        }

        // Get completions from valid schemas
        val completions = SchemaInformation.getCompletions(parsedSchema, buildPath, validSchemas)

        return completions.takeIf { it.isNotEmpty() }
    }

    /**
     * Expands combinator schemas (oneOf/anyOf/allOf) into individual branches.
     *
     * If a schema contains oneOf/anyOf/allOf, this replaces it with multiple ResolvedRef objects,
     * one for each branch, tagged with the appropriate resolution type.
     *
     * @param schemas The list of schemas to expand
     * @return Expanded list with combinator branches as separate items
     */
    private fun expandCombinatorSchemas(schemas: List<ResolvedRef>): List<ResolvedRef> {
        val expanded = mutableListOf<ResolvedRef>()

        for (ref in schemas) {
            val schemaObj = ref.resolvedValue as? org.kson.value.KsonObject

            if (schemaObj != null) {
                var addedBranches = false

                // Check for oneOf
                (schemaObj.propertyLookup["oneOf"] as? org.kson.value.KsonList)?.elements?.forEach { branch ->
                    expanded.add(ResolvedRef(branch, ref.resolvedValueBaseUri, SchemaResolutionType.ONE_OF))
                    addedBranches = true
                }

                // Check for anyOf
                (schemaObj.propertyLookup["anyOf"] as? org.kson.value.KsonList)?.elements?.forEach { branch ->
                    expanded.add(ResolvedRef(branch, ref.resolvedValueBaseUri, SchemaResolutionType.ANY_OF))
                    addedBranches = true
                }

                // Check for allOf - these don't need filtering, but we expand them for consistency
                (schemaObj.propertyLookup["allOf"] as? org.kson.value.KsonList)?.elements?.forEach { branch ->
                    expanded.add(ResolvedRef(branch, ref.resolvedValueBaseUri, SchemaResolutionType.ALL_OF))
                    addedBranches = true
                }

                // If we didn't add any branches, keep the original schema
                if (!addedBranches) {
                    expanded.add(ref)
                }
            } else {
                // Not an object, keep as-is
                expanded.add(ref)
            }
        }

        return expanded
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
     * @param documentPath The path to the completion location
     * @param schemaIdLookup The schema lookup for parsing schemas
     * @return Filtered list of compatible schemas
     */
    private fun filterValidSchemas(
        candidateSchemas: List<ResolvedRef>,
        documentValue: org.kson.value.KsonValue,
        documentPath: List<String>,
        schemaIdLookup: SchemaIdLookup
    ): List<ResolvedRef> {
        // Get the object to validate against
        // For completions, we validate the object where we're adding properties
        val targetValue = KsonValueNavigation.navigateByTokens(documentValue, documentPath) ?: documentValue

        return candidateSchemas.filter { ref ->
            when (ref.resolutionType) {
                // For anyOf/oneOf, check if the current document state is compatible
                SchemaResolutionType.ANY_OF,
                SchemaResolutionType.ONE_OF -> {
                    val messageSink = MessageSink()
                    val schema = SchemaParser.parseSchemaElement(
                        ref.resolvedValue,
                        messageSink,
                        ref.resolvedValueBaseUri,
                        schemaIdLookup
                    )

                    if (schema == null) {
                        // If we can't parse the schema, include it (fail open)
                        return@filter true
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
                    // We only care about errors that indicate incompatibility with existing properties,
                    // not missing properties (which we're about to add via completions)
                    val significantErrors = errors.filter { loggedMessage ->
                        loggedMessage.message.type != MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING &&
                        loggedMessage.message.type != MessageType.SCHEMA_MISSING_REQUIRED_DEPENDENCIES
                    }

                    // Include this schema if there are no significant validation errors
                    significantErrors.isEmpty()
                }
                // For all other types (direct property, allOf, etc.), include them
                else -> true
            }
        }
    }
}

/**
 * Represents a completion item to be shown in the IDE.
 */
class CompletionItem(
    val label: String,              // The text to insert
    val detail: String?,            // Short description (e.g., "string")
    val documentation: String?,     // Full markdown documentation
    val kind: CompletionKind        // Type of completion
)

/**
 * The type of completion item.
 */
enum class CompletionKind {
    PROPERTY,    // Object property name
    VALUE        // Enum value or suggested value
}

/**
 * Ranges are used to describe the start and end Coordinates inside a document
 *
 * @param startLine line where range starts
 * @param startColumn column where range starts
 * @param endLine line where range ends
 * @param endColumn column where range ends
 */
class Range(val startLine: Int, val startColumn: Int, val  endLine: Int, val endColumn: Int)
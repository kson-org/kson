@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.navigation.KsonValuePathBuilder
import org.kson.navigation.SchemaInformation
import org.kson.navigation.extractSchemaInfo
import org.kson.parser.Coordinates
import org.kson.schema.SchemaIdLookup
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
     * Filters schemas based on validation - only returns info from schemas that
     * are compatible with the existing document properties (for oneOf/anyOf combinators).
     * When multiple valid schemas exist, their information is combined with separators.
     *
     * @param documentRoot The root of the document being edited (KSON string)
     * @param schemaValue The schema for the document (KSON string)
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
        val parsedSchema = KsonCore.parseToAst(schemaValue).ksonValue ?: return null

        // Get all candidate schemas at this path
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val candidateSchemas = schemaIdLookup.navigateByDocumentPath(buildPath)

        // Apply the same filtering logic as completions and jump-to-definition
        val filteringService = SchemaFilteringService(schemaIdLookup)
        val validSchemas = filteringService.getValidSchemas(candidateSchemas, documentRoot, buildPath)

        // Extract schema info from each valid schema
        val schemaInfos = validSchemas.mapNotNull { ref ->
            ref.resolvedValue.extractSchemaInfo()
        }

        // Combine multiple schema infos with separator
        return when {
            schemaInfos.isEmpty() -> null
            schemaInfos.size == 1 -> schemaInfos.first()
            else -> schemaInfos.joinToString("\n\n---\n\n")
        }
    }

    /**
     * Get schema location for a position in a document.
     *
     * This is a convenience method that finds the KsonValue at the given position
     * and then returns its location in the schema document.
     *
     * Filters schemas based on validation - only returns locations for schemas that
     * are compatible with the existing document properties (for oneOf/anyOf combinators).
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
        val buildPath = KsonValuePathBuilder( documentRoot, Coordinates(line, column)).buildPathToPosition(includePropertyKeys = true) ?: return null
        val parsedSchema = KsonCore.parseToAst(schemaValue).ksonValue ?: return null

        // Get all candidate schemas at this path
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val candidateSchemas = schemaIdLookup.navigateByDocumentPath(buildPath)

        // Apply the same filtering logic as completions to only show valid schemas
        val filteringService = SchemaFilteringService(schemaIdLookup)
        val validSchemas = filteringService.getValidSchemas(candidateSchemas, documentRoot, buildPath)

        return validSchemas.map {
            Range(
                it.resolvedValue.location.start.line,
                it.resolvedValue.location.start.column,
                it.resolvedValue.location.end.line,
                it.resolvedValue.location.end.column
            )
        }
    }

    /**
     * Resolve a $ref reference within a schema document at the given position.
     *
     * This function checks if the cursor is positioned on a $ref string value,
     * and if so, resolves it to the target location within the same schema document.
     * Only internal references (starting with #) are supported.
     *
     * @param schemaValue The schema document (KSON string)
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return List of Range objects pointing to the referenced schema location(s), or null if not a ref or not found
     */
    fun resolveRefAtLocation(
        schemaValue: String,
        line: Int,
        column: Int
    ): List<Range>? {
        val parsedSchema = KsonCore.parseToAst(schemaValue).ksonValue ?: return null
        val buildPath = KsonValuePathBuilder(schemaValue, Coordinates(line, column)).buildPathToPosition() ?: return null

        // Return early if we are not in a $ref string
        if( buildPath.lastOrNull() != $$"$ref") { return emptyList() }

        // Navigate to the value at the cursor position
        val valueAtPosition = KsonValueNavigation.navigateByTokens(parsedSchema, buildPath) ?: return null
        // TODO - Currently we lookup the whole ref string. With sublocations we might be able to find the 'sublocation' to look up.
        val refString = (valueAtPosition as? org.kson.value.KsonString)?.value ?: return null

        // Determine the base URI for the schema root
        val baseUri = (parsedSchema as? org.kson.value.KsonObject)
            ?.propertyLookup["\$id"]
            ?.let { it as? org.kson.value.KsonString }
            ?.value ?: ""

        // Resolve the reference and return its location
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val resolvedRef = schemaIdLookup.resolveRef(refString, baseUri) ?: return null

        return listOf(
            Range(
                resolvedRef.resolvedValue.location.start.line,
                resolvedRef.resolvedValue.location.start.column,
                resolvedRef.resolvedValue.location.end.line,
                resolvedRef.resolvedValue.location.end.column
            )
        )
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
        val filteringService = SchemaFilteringService(schemaIdLookup)
        val validSchemas = filteringService.getValidSchemas(candidateSchemas, documentRoot, buildPath)

        // Get completions from valid schemas, passing the document value to filter out already-filled properties
        val completions = SchemaInformation.getCompletions(parsedSchema, buildPath, validSchemas, parsedDocument)

        return completions.takeIf { it.isNotEmpty() }
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
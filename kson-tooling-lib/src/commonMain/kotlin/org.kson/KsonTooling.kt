@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.navigation.KsonValuePathBuilder
import org.kson.navigation.SchemaInformation
import org.kson.navigation.extractSchemaInfo
import org.kson.parser.Coordinates
import org.kson.value.navigation.jsonPointer.JsonPointer
import org.kson.schema.SchemaIdLookup
import org.kson.value.navigation.KsonValueNavigation
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
        val documentPointer = KsonValuePathBuilder( documentRoot, Coordinates(line, column)).buildJsonPointerToPosition() ?: return null
        val context = ResolvedSchemaContext.resolveAndFilterSchemas(schemaValue, documentRoot, documentPointer) ?: return null

        // Extract schema info from each valid schema
        val schemaInfos = context.validSchemas.mapNotNull { ref ->
            ref.resolvedValue.extractSchemaInfo()
        }

        return schemaInfos.distinct().joinToString("\n\n---\n\n")
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
     * @return List of Range objects with zero-based coordinates, or empty list if no schema info available
     */
    fun getSchemaLocationAtLocation(
        documentRoot: String,
        schemaValue: String,
        line: Int,
        column: Int
    ): List<Range> {
        val documentPointer = KsonValuePathBuilder( documentRoot, Coordinates(line, column)).buildJsonPointerToPosition() ?: return emptyList()
        val context = ResolvedSchemaContext.resolveAndFilterSchemas(schemaValue, documentRoot, documentPointer) ?: return emptyList()

        return context.validSchemas.map {
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
     * @return List of Range objects pointing to the referenced schema location(s), or empty list if not a ref or not found
     */
    fun resolveRefAtLocation(
        schemaValue: String,
        line: Int,
        column: Int
    ): List<Range> {
        val parsedSchema = KsonCore.parseToAst(schemaValue).ksonValue ?: return emptyList()
        val documentPointer = KsonValuePathBuilder(schemaValue, Coordinates(line, column)).buildJsonPointerToPosition() ?: return emptyList()

        // Return early if we are not in a $ref string
        if( documentPointer.tokens.lastOrNull() != $$"$ref") { return emptyList() }

        // Navigate to the value at the cursor position
        val valueAtPosition = KsonValueNavigation.navigateWithJsonPointer(parsedSchema, documentPointer) ?: return emptyList()
        // TODO - Currently we lookup the whole ref string. With sublocations we might be able to find the 'sublocation' to look up.
        val refString = (valueAtPosition as? org.kson.value.KsonString)?.value ?: return emptyList()

        // Determine the base URI for the schema root
        val baseUri = (parsedSchema as? org.kson.value.KsonObject)
            ?.propertyLookup[$$"$id"]
            ?.let { it as? org.kson.value.KsonString }
            ?.value ?: ""

        // Resolve the reference and return its location
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val resolvedRef = schemaIdLookup.resolveRef(refString, baseUri) ?: return emptyList()

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
    ): List<CompletionItem> {
        val documentPointer = KsonValuePathBuilder(documentRoot, Coordinates(line, column)).buildJsonPointerToPosition(includePropertyKeys = false) ?: return emptyList()
        val context = ResolvedSchemaContext.resolveAndFilterSchemas(schemaValue, documentRoot, documentPointer) ?: return emptyList()

        // Get completions from valid schemas, passing the document value to filter out already-filled properties
        return SchemaInformation.getCompletions(context.schemaIdLookup.schemaRootValue, documentPointer, context.validSchemas, context.parsedDocument)
    }

    /**
     * Internal helper data class to hold the result of schema resolution and filtering.
     */
    private data class ResolvedSchemaContext(
        val schemaIdLookup: SchemaIdLookup,
        val validSchemas: List<org.kson.schema.ResolvedRef>,
        val parsedDocument: org.kson.value.KsonValue?
    ){
        companion object {
            /**
             * Common helper to parse, navigate, and filter schemas based on a document path.
             *
             * This method encapsulates the repeated pattern of:
             * 1. Parsing the schema
             * 2. Creating a SchemaIdLookup
             * 3. Navigating to candidate schemas
             * 4. Filtering schemas based on validation
             *
             * @param schemaValue The schema document (KSON string)
             * @param documentRoot The document being edited (KSON string)
             * @param documentPointer The [JsonPointer] to navigate to in the schema
             * @return ResolvedSchemaContext containing the parsed schema, lookup, filtered schemas, and parsed document, or null if parsing fails
             */
            fun resolveAndFilterSchemas(
                schemaValue: String,
                documentRoot: String,
                documentPointer: JsonPointer
            ): ResolvedSchemaContext? {
                val parsedSchema = KsonCore.parseToAst(schemaValue).ksonValue ?: return null
                val schemaIdLookup = SchemaIdLookup(parsedSchema)
                val candidateSchemas = schemaIdLookup.navigateByDocumentPointer(documentPointer)

                val filteringService = SchemaFilteringService(schemaIdLookup)
                val validSchemas = filteringService.getValidSchemas(candidateSchemas, documentRoot, documentPointer)

                val parsedDocument = KsonCore.parseToAst(documentRoot).ksonValue

                return ResolvedSchemaContext( schemaIdLookup, validSchemas, parsedDocument)
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
@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.navigation.KsonValuePathBuilder
import org.kson.navigation.SchemaInformation
import org.kson.parser.Coordinates
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

        // Step 2: Get completions
        val completions = KsonCore.parseToAst(schemaValue).ksonValue?.let{
            SchemaInformation.getCompletions(it, buildPath)
        } ?: return null

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
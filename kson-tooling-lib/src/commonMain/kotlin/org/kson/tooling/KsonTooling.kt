@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson.tooling

import org.kson.tooling.navigation.KsonValuePathBuilder
import org.kson.tooling.navigation.SchemaInformation
import org.kson.tooling.navigation.extractSchemaInfo
import org.kson.parser.Coordinates
import org.kson.schema.ResolvedRef
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.schema.SchemaIdLookup
import org.kson.validation.SourceContext
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.walker.KsonValueWalker
import org.kson.walker.navigateWithJsonPointer
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
     * Finds the KsonValue at the given position and retrieves schema information
     * for it. Filters schemas based on validation — only returns info from schemas
     * compatible with the existing document properties (for oneOf/anyOf combinators).
     * When multiple valid schemas exist, their information is combined with separators.
     *
     * @param document The pre-parsed document being edited
     * @param schema The pre-parsed schema for the document
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return Formatted text, or null if no schema info available
     */
    fun getSchemaInfoAtLocation(
        document: ToolingDocument,
        schema: ToolingDocument,
        line: Int,
        column: Int
    ): String? {
        val parsedSchema = schema.ksonValue ?: return null
        val documentPointer = KsonValuePathBuilder(document, Coordinates(line, column)).buildJsonPointerToPosition() ?: return null
        val validSchemas = resolveAndFilterSchemas(parsedSchema, document.ksonValue, documentPointer)

        val schemaInfos = validSchemas.mapNotNull { ref ->
            ref.resolvedValue.extractSchemaInfo()
        }

        return schemaInfos.distinct().joinToString("\n\n---\n\n")
    }

    /**
     * Get schema location for a position in a document.
     *
     * Finds the KsonValue at the given position and returns its location in the
     * schema document. Filters schemas based on validation — only returns locations
     * for schemas compatible with the existing document properties.
     *
     * @param document The pre-parsed document being edited
     * @param schema The pre-parsed schema for the document
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return List of Range objects with zero-based coordinates, or empty list if no schema info available
     */
    fun getSchemaLocationAtLocation(
        document: ToolingDocument,
        schema: ToolingDocument,
        line: Int,
        column: Int
    ): List<Range> {
        val parsedSchema = schema.ksonValue ?: return emptyList()
        val documentPointer = KsonValuePathBuilder(document, Coordinates(line, column)).buildJsonPointerToPosition() ?: return emptyList()
        val validSchemas = resolveAndFilterSchemas(parsedSchema, document.ksonValue, documentPointer)

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
     * Checks if the cursor is positioned on a $ref string value, and if so,
     * resolves it to the target location within the same schema document.
     * Only internal references (starting with #) are supported.
     *
     * @param schema The pre-parsed schema document
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return List of Range objects pointing to the referenced schema location(s), or empty list if not a ref or not found
     */
    fun resolveRefAtLocation(
        schema: ToolingDocument,
        line: Int,
        column: Int
    ): List<Range> {
        val parsedSchema = schema.ksonValue ?: return emptyList()
        val documentPointer = KsonValuePathBuilder(schema, Coordinates(line, column)).buildJsonPointerToPosition() ?: return emptyList()

        // Return early if we are not in a $ref string
        if( documentPointer.tokens.lastOrNull() != $$"$ref") { return emptyList() }

        // Navigate to the value at the cursor position
        val valueAtPosition = KsonValueWalker.navigateWithJsonPointer(parsedSchema, documentPointer) ?: return emptyList()
        // TODO - Currently we lookup the whole ref string. With sublocations we might be able to find the 'sublocation' to look up.
        val refString = (valueAtPosition as? KsonString)?.value ?: return emptyList()

        // Determine the base URI for the schema root
        val baseUri = (parsedSchema as? KsonObject)
            ?.propertyLookup[$$"$id"]
            ?.let { it as? KsonString }
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
     * Finds the KsonValue at the given position and retrieves completion
     * suggestions based on the schema.
     *
     * @param document The pre-parsed document being edited
     * @param schema The pre-parsed schema for the document
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return List of completion items, or empty list if no completions available
     */
    fun getCompletionsAtLocation(
        document: ToolingDocument,
        schema: ToolingDocument,
        line: Int,
        column: Int
    ): List<CompletionItem> {
        val parsedSchema = schema.ksonValue ?: return emptyList()
        val documentPointer = KsonValuePathBuilder(document, Coordinates(line, column)).buildJsonPointerToPosition(includePropertyKeys = false) ?: return emptyList()
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val candidateSchemas = schemaIdLookup.navigateByDocumentPointer(documentPointer, document.partialKsonValue)

        val filteringService = SchemaFilteringService(schemaIdLookup)
        val validSchemas = filteringService.getValidSchemas(candidateSchemas, document.ksonValue, documentPointer)

        return SchemaInformation.getCompletions(parsedSchema, documentPointer, validSchemas, document.ksonValue)
    }

    /**
     * Parse KSON source into a [ToolingDocument] that can be reused across
     * multiple tooling operations, avoiding redundant parsing.
     *
     * The document is parsed with error tolerance so that partial results
     * are available even for documents with syntax errors.
     *
     * @param content The KSON source text
     * @param filepath The filepath of the document, used to provide context to validators
     * @return A reusable [ToolingDocument]
     */
    fun parse(content: String, filepath: String? = null): ToolingDocument =
        ToolingDocument(content, SourceContext(filepath))

    /**
     * Get document symbols from a pre-parsed [ToolingDocument].
     *
     * @param document The pre-parsed KSON document
     * @return List of document symbols, or empty list if parsing failed
     */
    fun getDocumentSymbols(document: ToolingDocument): List<DocumentSymbol> {
        return document.documentSymbols
    }

    /**
     * Build semantic tokens from a pre-parsed [ToolingDocument].
     *
     * @param document The pre-parsed KSON document
     * @return List of semantic tokens with absolute positions
     */
    fun getSemanticTokens(document: ToolingDocument): List<SemanticToken> {
        return SemanticTokenBuilder.build(document.tokens, document.ast)
    }

    /**
     * Get structural ranges (foldable regions) from a pre-parsed [ToolingDocument].
     *
     * Identifies multi-line objects, arrays, and embed blocks that can
     * be collapsed in an editor. Single-line constructs are excluded.
     *
     * @param document The pre-parsed KSON document
     * @return List of structural ranges, each spanning at least two lines
     */
    fun getStructuralRanges(document: ToolingDocument): List<StructuralRange> {
        return FoldingRangeBuilder.build(document.tokens)
    }

    /**
     * Get enclosing ranges for a cursor position in a pre-parsed [ToolingDocument].
     *
     * Returns a list of ranges from innermost to outermost that contain
     * the cursor position. Used for smart expand/shrink selection.
     * Includes the full-document range as the outermost entry.
     *
     * @param document The pre-parsed KSON document
     * @param line Zero-based line number
     * @param column Zero-based column number
     * @return List of ranges from innermost to outermost, deduplicated,
     *         with the full-document range as the last element
     */
    fun getEnclosingRanges(document: ToolingDocument, line: Int, column: Int): List<Range> {
        val ksonValue = document.ksonValue
        val ancestors = if (ksonValue != null) {
            SelectionRangeBuilder.build(ksonValue, line, column).toMutableList()
        } else {
            mutableListOf()
        }
        // The lexer always produces at least an EOF token
        val eof = document.tokens.last()
        val documentRange = Range(0, 0, eof.lexeme.location.end.line, eof.lexeme.location.end.column)
        if (ancestors.lastOrNull() != documentRange) {
            ancestors.add(documentRange)
        }
        return ancestors
    }

    /**
     * Validate a KSON document and return diagnostic messages.
     *
     * Internally performs a strict re-parse (without error tolerance) to
     * produce accurate error messages. The [ToolingDocument]'s content and
     * [SourceContext][SourceContext] are used for the
     * strict parse, so validators receive the document's filepath.
     *
     * If a [schema] is provided, the document is validated against it.
     * Schema validation includes both parse errors and schema violations.
     * If no schema is provided (or it fails to parse), only parse errors are returned.
     *
     * @param document The pre-parsed document to validate
     * @param schema Optional pre-parsed schema document
     * @return List of diagnostic messages
     */
    fun validateDocument(document: ToolingDocument, schema: ToolingDocument? = null): List<DiagnosticMessage> {
        return DiagnosticBuilder.build(document.content, schema?.content, document.sourceContext)
    }

    /**
     * Get sibling key ranges for a cursor position in a pre-parsed [ToolingDocument].
     *
     * If the cursor is on a property key, returns the selection ranges of all
     * sibling keys within the same parent object. Returns an empty list if the
     * cursor is not on a key.
     *
     * @param document The pre-parsed KSON document
     * @param line Zero-based line number
     * @param column Zero-based column number
     * @return List of ranges for sibling key symbols
     */
    fun getSiblingKeys(document: ToolingDocument, line: Int, column: Int): List<Range> {
        val symbols = document.documentSymbols
        if (symbols.isEmpty()) return emptyList()
        return SiblingKeyBuilder.build(symbols, line, column)
    }

    /**
     * Navigate and filter schemas for a document path.
     *
     * Creates a [SchemaIdLookup], navigates to candidate schemas at the pointer,
     * then filters them based on validation against the document value.
     */
    private fun resolveAndFilterSchemas(
        parsedSchema: KsonValue,
        documentValue: KsonValue?,
        documentPointer: JsonPointer
    ): List<ResolvedRef> {
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val candidateSchemas = schemaIdLookup.navigateByDocumentPointer(documentPointer, documentValue)
        val filteringService = SchemaFilteringService(schemaIdLookup)
        return filteringService.getValidSchemas(candidateSchemas, documentValue, documentPointer)
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
data class Range(val startLine: Int, val startColumn: Int, val endLine: Int, val endColumn: Int)

internal fun KsonValue.toRange(): Range = Range(
    location.start.line, location.start.column,
    location.end.line, location.end.column
)

/**
 * Represents a document symbol for the IDE outline view.
 */
data class DocumentSymbol(
    val name: String,
    val kind: DocumentSymbolKind,
    val range: Range,
    val selectionRange: Range,
    val detail: String?,
    val children: List<DocumentSymbol>
)

/**
 * Kind of a document symbol. Domain-level classification of KSON values.
 */
enum class DocumentSymbolKind {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    KEY,
    EMBED
}

/**
 * Represents a semantic token with absolute position.
 */
data class SemanticToken(
    val line: Int,
    val column: Int,
    val length: Int,
    val tokenType: SemanticTokenKind
)

/**
 * Kind of a semantic token. Domain-level classification of KSON tokens.
 */
enum class SemanticTokenKind {
    STRING,
    KEY,
    NUMBER,
    KEYWORD,
    OPERATOR,
    COMMENT,
    EMBED_TAG,
    EMBED_CONTENT,
    EMBED_DELIM
}

/**
 * A structural range representing a foldable region in a KSON document.
 */
data class StructuralRange(val startLine: Int, val endLine: Int, val kind: StructuralRangeKind)

/**
 * Kind of structural range.
 */
enum class StructuralRangeKind {
    OBJECT,
    ARRAY,
    EMBED
}

/**
 * A diagnostic message from document validation.
 */
data class DiagnosticMessage(val message: String, val severity: DiagnosticSeverity, val range: Range)

/**
 * Severity of a diagnostic message.
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING
}
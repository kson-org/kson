package org.kson.tooling.navigation

import org.kson.ast.AstNode
import org.kson.tooling.CompletionItem
import org.kson.tooling.CompletionKind
import org.kson.value.toKsonValueOrNull
import org.kson.walker.AstNodeWalker
import org.kson.walker.TreePointer
import org.kson.walker.navigate
import org.kson.value.KsonValue as InternalKsonValue
import org.kson.value.KsonObject as InternalKsonObject
import org.kson.value.KsonList as InternalKsonList
import org.kson.value.KsonString as InternalKsonString
import org.kson.value.KsonNumber as InternalKsonNumber
import org.kson.value.EmbedBlock as InternalEmbedBlock
import org.kson.value.KsonBoolean as InternalKsonBoolean
import org.kson.value.KsonNull as InternalKsonNull

internal object SchemaInformation{
    /**
     * Get completion suggestions for the node in a schema, found by using the
     * [documentPointer] to navigate the schema
     *
     * When multiple schemas match (e.g., property defined in multiple combinator branches),
     * merges completions from all matching schemas.
     *
     * @param documentPointer The pointer through the document's AST to the value being completed
     * @param validSchemas Pre-filtered list of valid schemas at the path
     * @param documentAst Root of the document's AST, or null; when present, filters out already-filled properties
     * @return List of completion items
     */
    fun getCompletions(
        documentPointer: TreePointer<AstNode>,
        validSchemas: List<NavigatedSchema>,
        documentAst: AstNode?
    ): List<CompletionItem> {
        val allCompletions = extractCompletionsWithNarrowing(validSchemas)

        // Only filter if:
        // 1. Document AST is provided
        // 2. We have PROPERTY completions (not just VALUE completions)
        // 3. We can successfully navigate to an object at the document path
        if (documentAst == null) {
            return allCompletions
        }

        val hasPropertyCompletions = allCompletions.any { it.kind == CompletionKind.PROPERTY }
        if (!hasPropertyCompletions) {
            return allCompletions
        }

        // Get the current object at the completion location
        // If we can't find an object, it means the caret is before the object literal,
        // so we shouldn't filter (e.g., "user: <caret>{" - object exists but path doesn't reach it yet)
        val currentObject = AstNodeWalker.navigate(documentAst, documentPointer)?.toKsonValueOrNull() as? InternalKsonObject
            ?: return allCompletions

        // Get the set of already-filled property names
        val filledProperties = currentObject.propertyLookup.keys

        // Filter out completions for properties that are already filled
        return allCompletions.filter { completion ->
            // Only filter PROPERTY kind completions (not VALUE completions like enum values)
            if (completion.kind == CompletionKind.PROPERTY) {
                completion.label !in filledProperties
            } else {
                true // Keep all VALUE completions
            }
        }
    }
}

/**
 * Extract completions from resolved schemas, applying JSON Schema narrowing semantics.
 *
 * Branches reach here already narrowed against the document by navigation; their
 * branch trails say how their completions combine:
 *
 * Property-name completions are always **unioned** — allOf/oneOf/anyOf branches each
 * contribute keys, and the valid set is their union.
 *
 * Value completions offer the listed values the schemas allow together (see [allowedAlongTrails]):
 * a value must satisfy every schema that holds together, such as a property's enum and its allOf
 * members', and one open branch of each oneOf, anyOf or if.  When nothing restricts it, every
 * value a schema lists is offered.
 *
 * @param resolvedSchemas The schemas found at the document path, with their branch trails
 * @return Deduplicated list of completion items respecting narrowing semantics
 */
private fun extractCompletionsWithNarrowing(resolvedSchemas: List<NavigatedSchema>): List<CompletionItem> {
    val (propertyCompletions, valueCompletions) = resolvedSchemas
        .flatMap { it.resolvedValue.extractCompletions() }
        .partition { it.kind == CompletionKind.PROPERTY }
    val allowedValues = resolvedSchemas.allowedAlongTrails(::valueLabels)
    val offeredValues = valueCompletions.filter { allowedValues == null || it.label in allowedValues }
    return (propertyCompletions + offeredValues).distinctBy { it.label }
}

/** The labels of the values [schema] offers, or null when it offers none and so isn't taken to restrict the value. */
private fun valueLabels(schema: InternalKsonValue): Set<String>? =
    schema.extractCompletions().filter { it.kind == CompletionKind.VALUE }.map { it.label }.toSet().ifEmpty { null }

/**
 * Extract schema information from a schema node.
 *
 * Formats schema metadata into markdown suitable for IDE hover tooltips.
 * Includes: title, description, type, default value, constraints (enum, pattern, min/max).
 *
 * @return Formatted markdown string, or null if no hover info available
 */
fun InternalKsonValue.extractSchemaInfo(): String? {
    if (this !is InternalKsonObject) return null

    val props: Map<String, InternalKsonValue> = this.propertyLookup

    return buildString {
        // Title (bold header)
        (props["title"] as? InternalKsonString)?.value?.let {
            append("**$it**\n\n")
        }

        // Description (main documentation)
        when (val desc = props["description"]) {
            is InternalKsonString -> desc.value
            is InternalEmbedBlock -> desc.embedContent.value
            else -> null
        }?.let { append("$it\n\n") }

        // Type information
        when (val typeValue: InternalKsonValue? = props["type"]) {
            is InternalKsonString -> {
                append("*Type:* `${typeValue.value}`\n\n")
            }

            is InternalKsonList -> {
                // Union type: ["string", "number"]
                val types = typeValue.elements.mapNotNull { (it as? InternalKsonString)?.value }
                if (types.isNotEmpty()) {
                    append("*Type:* `${types.joinToString(" | ")}`\n\n")
                }
            }

            is InternalKsonBoolean, is InternalKsonNull, is InternalKsonNumber, is InternalKsonObject, is InternalEmbedBlock, null -> {
                // These types are not expected for the "type" property in a schema
                // We simply don't add type information in these cases
            }
        }

        // Default value
        props["default"]?.let {
            append("*Default:* `${it.formatValueForDisplay()}`\n\n")
        }

        // Enum values
        (props["enum"] as? InternalKsonList)?.let { enumList ->
            val values = enumList.elements.joinToString(", ") { "`${it.formatValueForDisplay()}`" }
            append("*Allowed values:* $values\n\n")
        }

        // Pattern constraint
        (props["pattern"] as? InternalKsonString)?.value?.let {
            append("*Pattern:* `$it`\n\n")
        }

        // Numeric constraints
        (props["minimum"] as? InternalKsonNumber)?.let {
            append("*Minimum:* ${it.value.asString}\n\n")
        }
        (props["maximum"] as? InternalKsonNumber)?.let {
            append("*Maximum:* ${it.value.asString}\n\n")
        }

        // String length constraints
        (props["minLength"] as? InternalKsonNumber)?.let {
            append("*Min length:* ${it.value.asString}\n\n")
        }
        (props["maxLength"] as? InternalKsonNumber)?.let {
            append("*Max length:* ${it.value.asString}\n\n")
        }

        // Array length constraints
        (props["minItems"] as? InternalKsonNumber)?.let {
            append("*Min items:* ${it.value.asString}\n\n")
        }
        (props["maxItems"] as? InternalKsonNumber)?.let {
            append("*Max items:* ${it.value.asString}\n\n")
        }
    }.takeIf { it.isNotEmpty() }
}


/**
 * Format a KsonValue for display in info.
 * Converts values to a readable string representation.
 */
fun InternalKsonValue.formatValueForDisplay(): String {
    return when (this) {
        is InternalKsonList -> "[${this.elements.joinToString(",") { it.formatValueForDisplay() }}]"
        is InternalKsonBoolean -> this.value.toString()
        is InternalEmbedBlock -> "<embed>"
        is InternalKsonNull -> "null"
        is InternalKsonNumber -> this.value.asString
        is InternalKsonObject -> "{...}"
        is InternalKsonString -> this.value
    }
}

/**
 * Extract completion items from a schema node based on the completion context.
 *
 * @param context The completion context (property name or value)
 * @return List of completion items
 */
internal fun InternalKsonValue.extractCompletions(
): List<CompletionItem> {
    if (this !is InternalKsonObject) return emptyList()
    return extractValueCompletions()
}


/** True when this schema describes an object type (via `type` or the presence of `properties`). */
private fun InternalKsonObject.isObjectSchema(): Boolean =
    when (val typeValue = propertyLookup["type"]) {
        is InternalKsonString -> typeValue.value == "object"
        is InternalKsonList -> typeValue.elements.any { (it as? InternalKsonString)?.value == "object" }
        else -> propertyLookup.containsKey("properties") // Has properties = likely object schema
    }

/**
 * Extract completions from a schema node.
 *
 * Provides completions for:
 * - Object properties (if type is object)
 * - Const value (if const is defined)
 * - Enum values (if enum is defined)
 * - Boolean values (if type is boolean)
 * - Null value (if type is null or includes null)
 */
private fun InternalKsonObject.extractValueCompletions(): List<CompletionItem> {
    // An object schema offers property completions rather than value completions.
    if (isObjectSchema()) {
        return extractPropertyCompletions()
    }

    val completions = mutableListOf<CompletionItem>()

    // If const exists, offer that single value
    propertyLookup["const"]?.let { constValue ->
        completions.add(
            CompletionItem(
                label = constValue.formatValueForDisplay(),
                detail = "const value",
                documentation = this.extractSchemaInfo(),
                kind = CompletionKind.VALUE
            )
        )
        return completions
    }

    // If enum exists, offer those values
    (propertyLookup["enum"] as? InternalKsonList)?.let { enumList ->
        enumList.elements.forEach { enumValue ->
            completions.add(
                CompletionItem(
                    label = enumValue.formatValueForDisplay(),
                    detail = "enum value",
                    documentation = this.extractSchemaInfo(),
                    kind = CompletionKind.VALUE
                )
            )
        }
        return completions
    }

    // If type is defined, offer type-specific completions
    when (val typeValue = propertyLookup["type"]) {
        is InternalKsonString -> {
            when (typeValue.value) {
                "boolean" -> {
                    completions.add(CompletionItem("true", "boolean", null, CompletionKind.VALUE))
                    completions.add(CompletionItem("false", "boolean", null, CompletionKind.VALUE))
                }
                "null" -> {
                    completions.add(CompletionItem("null", "null", null, CompletionKind.VALUE))
                }
            }
        }
        is InternalKsonList -> {
            // Union type - check if it includes boolean or null
            val types = typeValue.elements.mapNotNull { (it as? InternalKsonString)?.value }
            if ("boolean" in types) {
                completions.add(CompletionItem("true", "boolean", null, CompletionKind.VALUE))
                completions.add(CompletionItem("false", "boolean", null, CompletionKind.VALUE))
            }
            if ("null" in types) {
                completions.add(CompletionItem("null", "null", null, CompletionKind.VALUE))
            }
        }
        else -> {}
    }

    return completions
}


/**
 * Extract property name completions from an object schema.
 *
 * Looks at the "properties" field in the schema and creates completion items
 * for each available property.
 */
private fun InternalKsonObject.extractPropertyCompletions(): List<CompletionItem> {
    val properties = (propertyLookup["properties"] as? InternalKsonObject)
        ?: return emptyList()

    return properties.propertyLookup.map { (propName, propSchema) ->
        CompletionItem(
            label = propName,
            detail = extractTypeHint(propSchema),
            documentation = propSchema.extractSchemaInfo(),  // Reuse existing function!
            kind = CompletionKind.PROPERTY
        )
    }
}

/**
 * Extract a simple type hint string from a schema node.
 *
 * This is a simplified version of the type extraction in extractSchemaInfo,
 * used for the "detail" field in completion items.
 *
 * @return Type string (e.g., "string", "number | string"), or null if no type info
 */
private fun extractTypeHint(schemaNode: InternalKsonValue): String? {
    if (schemaNode !is InternalKsonObject) return null

    return when (val typeValue = schemaNode.propertyLookup["type"]) {
        is InternalKsonString -> typeValue.value
        is InternalKsonList -> {
            typeValue.elements
                .mapNotNull { (it as? InternalKsonString)?.value }
                .joinToString(" | ")
                .takeIf { it.isNotEmpty() }
        }
        else -> null
    }
}
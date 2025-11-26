package org.kson.navigation

import org.kson.CompletionItem
import org.kson.CompletionKind
import org.kson.schema.SchemaIdLookup
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
     * Get info for the node in a schema, found by using the
     * [documentPath] to navigate the schema
     *
     * @param schemaValue The schema for the document (as KsonValue)
     * @param documentPath The path to the [org.kson.value.KsonValue] in the document
     */
    fun getSchemaInfo(
        schemaValue: InternalKsonValue,
        documentPath: List<String>
    ): String? {
        val resolvedSchema = SchemaIdLookup(schemaValue).navigateByDocumentPath(documentPath)
        return resolvedSchema?.resolvedValue?.extractSchemaInfo()
    }

    /**
     * Get completion suggestions for the node in a schema, found by using the
     * [documentPath] to navigate the schema
     *
     * @param schemaValue The schema for the document (as KsonValue)
     * @param documentPath The path to the [org.kson.value.KsonValue] in the document
     * @return List of completion items
     */
    fun getCompletions(
        schemaValue: InternalKsonValue,
        documentPath: List<String>
    ): List<CompletionItem> {

        val resolvedSchema = SchemaIdLookup(schemaValue).navigateByDocumentPath(documentPath)
        return resolvedSchema?.resolvedValue?.extractCompletions()
            ?: emptyList()
    }
}

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
        (props["description"] as? InternalKsonString)?.value?.let {
            append("$it\n\n")
        }

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


/**
 * Extract completions from a schema node.
 *
 * Provides completions for:
 * - Object properties (if type is object)
 * - Enum values (if enum is defined)
 * - Boolean values (if type is boolean)
 * - Null value (if type is null or includes null)
 */
private fun InternalKsonObject.extractValueCompletions(): List<CompletionItem> {
    // Check if this schema represents an object type
    // If so, we should provide property completions instead of value completions
    val isObjectType = when (val typeValue = propertyLookup["type"]) {
        is InternalKsonString -> typeValue.value == "object"
        is InternalKsonList -> {
            typeValue.elements.any { (it as? InternalKsonString)?.value == "object" }
        }
        else -> propertyLookup.containsKey("properties") // Has properties = likely object schema
    }

    if (isObjectType) {
        return extractPropertyCompletions()
    }

    val completions = mutableListOf<CompletionItem>()

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
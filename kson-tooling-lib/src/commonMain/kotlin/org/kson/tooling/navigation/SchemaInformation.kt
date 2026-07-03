package org.kson.tooling.navigation

import org.kson.tooling.CompletionItem
import org.kson.tooling.CompletionKind
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.walker.KsonValueWalker
import org.kson.walker.navigateWithJsonPointer
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
     * @param documentPointer The [JsonPointer] to the [org.kson.value.KsonValue] in the document
     * @param validSchemas Pre-filtered list of valid schemas at the path
     * @param documentValue The current document value, or null; when present, filters out already-filled properties
     * @return List of completion items
     */
    fun getCompletions(
        documentPointer: JsonPointer,
        validSchemas: List<NavigatedSchema>,
        documentValue: InternalKsonValue?
    ): List<CompletionItem> {
        val allCompletions = extractCompletionsWithNarrowing(validSchemas)

        // Only filter if:
        // 1. Document value is provided
        // 2. We have PROPERTY completions (not just VALUE completions)
        // 3. We can successfully navigate to an object at the document path
        if (documentValue == null) {
            return allCompletions
        }

        val hasPropertyCompletions = allCompletions.any { it.kind == CompletionKind.PROPERTY }
        if (!hasPropertyCompletions) {
            return allCompletions
        }

        // Get the current object at the completion location
        // If we can't find an object, it means the caret is before the object literal,
        // so we shouldn't filter (e.g., "user: <caret>{" - object exists but path doesn't reach it yet)
        val currentObject = KsonValueWalker.navigateWithJsonPointer(documentValue, documentPointer) as? InternalKsonObject
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
 * resolution type says how their completions combine:
 *
 * Property-name completions are always **unioned** — allOf/oneOf/anyOf branches each
 * contribute keys, and the valid set is their union.
 *
 * Value completions combine by group, and a value must satisfy every group that is
 * present (the groups are intersected):
 * - **base** (direct property, allOf, array items, root): the value must satisfy every
 *   such schema simultaneously, so their value-enums are intersected.
 * - **if/then & if/else**: mutually-exclusive alternatives — their value-enums are
 *   **unioned**, not intersected, so a value valid in only one branch isn't dropped.
 * - **oneOf/anyOf**: alternatives whose value-enums are unioned, but still bounded by
 *   the base — the union is intersected against the base enum, so a value the base
 *   forbids is never offered.
 *
 * @param resolvedSchemas The schemas found at the document path, with resolution type metadata
 * @return Deduplicated list of completion items respecting narrowing semantics
 */
private fun extractCompletionsWithNarrowing(resolvedSchemas: List<NavigatedSchema>): List<CompletionItem> {
    val perSchema = resolvedSchemas.map { it.resolutionType to it.resolvedValue.extractCompletions() }

    val propertyCompletions = perSchema.flatMap { (_, completions) ->
        completions.filter { it.kind == CompletionKind.PROPERTY }
    }

    // The non-empty per-schema VALUE-completion lists for branches matching [predicate].
    fun valueSets(predicate: (SchemaResolutionType) -> Boolean): List<List<CompletionItem>> =
        perSchema.filter { (type, _) -> predicate(type) }
            .map { (_, completions) -> completions.filter { it.kind == CompletionKind.VALUE } }
            .filter { it.isNotEmpty() }

    val baseSets = valueSets { it.isReductive && it != SchemaResolutionType.IF_THEN && it != SchemaResolutionType.IF_ELSE }
    val conditionalSets = valueSets { it == SchemaResolutionType.IF_THEN || it == SchemaResolutionType.IF_ELSE }
    val additiveSets = valueSets { !it.isReductive }

    // base schemas are intersected with each other; conditional and additive branches
    // are each unioned within their group.  null means "this group imposes no value
    // constraint" (no schema in it offered value completions).
    val baseLabels = baseSets.takeIf { it.isNotEmpty() }
        ?.map { set -> set.map { it.label }.toSet() }
        ?.reduce { acc, set -> acc.intersect(set) }
    val conditionalLabels = conditionalSets.flatten().map { it.label }.toSet().takeIf { conditionalSets.isNotEmpty() }
    val additiveLabels = additiveSets.flatten().map { it.label }.toSet().takeIf { additiveSets.isNotEmpty() }

    val constraintSets = listOfNotNull(baseLabels, conditionalLabels, additiveLabels)
    val valueCompletions = if (constraintSets.isEmpty()) {
        emptyList()
    } else {
        val allowedLabels = constraintSets.reduce { acc, set -> acc.intersect(set) }
        (baseSets + conditionalSets + additiveSets).flatten().filter { it.label in allowedLabels }
    }

    return (propertyCompletions + valueCompletions).distinctBy { it.label }
}

/**
 * True if this branch contributes value completions that must be intersected with other
 * reductive branches — a value must satisfy all reductive schemas simultaneously (e.g., a
 * base property's enum intersected with an if/then's narrower enum).  Additive branches
 * (oneOf/anyOf) merge their completions as alternatives instead.
 *
 * Exhaustive by design: adding a new [SchemaResolutionType] forces a compile error here so
 * the reductive-vs-additive classification is an explicit decision, not a default.
 */
private val SchemaResolutionType.isReductive: Boolean
    get() = when (this) {
        SchemaResolutionType.DIRECT_PROPERTY, SchemaResolutionType.PATTERN_PROPERTY,
        SchemaResolutionType.ADDITIONAL_PROPERTY, SchemaResolutionType.ARRAY_ITEMS,
        SchemaResolutionType.ALL_OF, SchemaResolutionType.IF_THEN,
        SchemaResolutionType.IF_ELSE, SchemaResolutionType.ROOT -> true
        SchemaResolutionType.ANY_OF, SchemaResolutionType.ONE_OF -> false
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
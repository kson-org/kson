package org.kson.schema

import org.kson.*
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.value.KsonValueNavigation

/**
 * Manages the mapping of `$id` values to their corresponding schema nodes for `$ref` resolution.
 *
 * @param schemaRootValue the [KsonValue] root of the schema to build this [SchemaIdLookup] from
 */
class SchemaIdLookup(val schemaRootValue: KsonValue) {

    private val idMap: Map<String, KsonValue>

    init {
        /**
         * Collect all `$id` entries from the given schema tree.
         * This pre-processes the entire schema to build a map of fully-qualified IDs.
         */
        idMap = mutableMapOf()

        // preload know meta-schemas
        idMap[Draft7MetaSchema.ID] = Draft7MetaSchema.schemaValue

        if (schemaRootValue is KsonObject) {
            val rootBaseUri = schemaRootValue.propertyLookup["\$id"]?.let { idValue ->
                if (idValue is KsonString) {
                    idValue.value
                } else {
                    // this $id is completely invalid
                    null
                }
            } ?: ""

            // Store the root schema at is baseUri
            idMap[rootBaseUri] = schemaRootValue

            // Walk the schema tree to collect all IDs with fully-qualified URIs
            walkSchemaForIds(schemaRootValue, idMap, rootBaseUri)
        }
    }

    /**
     * Resolves a `$ref` reference string to the corresponding schema value.
     *
     * @param ref The reference string (e.g., "#foo", "#/definitions/address", "bar")
     * @param currentBaseUri The current base URI context for resolving relative references
     * @return The resolved [KsonValue] representing the referenced schema, or null if not found
     */
    fun resolveRef(ref: String, currentBaseUri: String): ResolvedRef? {
        val resolvedRefUri = resolveUri(ref, currentBaseUri)

        // try a direct lookup of our resolved ref URI
        idMap[resolvedRefUri.toString()]?.let {
            return ResolvedRef(it, currentBaseUri)
        }

        // otherwise, see if we can interpret the fragment
        return if (resolvedRefUri.fragment.startsWith("#/")) {
            val decodedPointer = decodeUriEncoding(resolvedRefUri.fragment.substring(1))
            if (resolvedRefUri.origin.isNotBlank()) {
                idMap[resolvedRefUri.toString().substringBefore("#")]?.let { resolveJsonPointer(decodedPointer, it, resolvedRefUri.toString()) }
            } else {
                idMap[currentBaseUri]?.let { resolveJsonPointer(decodedPointer, it, currentBaseUri) }
            }
        } else {
            idMap[resolvedRefUri.toString().substringBefore("#") + resolvedRefUri.fragment.removePrefix("#")]
                ?.let { ResolvedRef(it, currentBaseUri) }
        }
    }


    /**
     * Expands combinator schemas (oneOf/anyOf/allOf) in a list of resolved schemas.
     *
     * For each schema in the list:
     * - If it contains oneOf/anyOf/allOf, expands them into individual branches
     * - Resolves any $ref in the branches
     * - Otherwise keeps the schema as-is
     *
     * @param schemas The list of schemas to expand
     * @return Expanded list with combinator branches as separate items, with $ref resolved
     */
    fun expandCombinators(schemas: List<ResolvedRef>): List<ResolvedRef> {
        val expanded = mutableListOf<ResolvedRef>()

        for (ref in schemas) {
            val schemaObj = ref.resolvedValue as? KsonObject

            if (schemaObj != null) {
                var addedBranches = false

                // Check for oneOf
                (schemaObj.propertyLookup["oneOf"] as? KsonList)?.elements?.forEach { branch ->
                    val resolved = resolveRefIfPresent(branch, ref.resolvedValueBaseUri)
                    expanded.add(ResolvedRef(resolved.resolvedValue, resolved.resolvedValueBaseUri, SchemaResolutionType.ONE_OF))
                    addedBranches = true
                }

                // Check for anyOf
                (schemaObj.propertyLookup["anyOf"] as? KsonList)?.elements?.forEach { branch ->
                    val resolved = resolveRefIfPresent(branch, ref.resolvedValueBaseUri)
                    expanded.add(ResolvedRef(resolved.resolvedValue, resolved.resolvedValueBaseUri, SchemaResolutionType.ANY_OF))
                    addedBranches = true
                }

                // Check for allOf
                (schemaObj.propertyLookup["allOf"] as? KsonList)?.elements?.forEach { branch ->
                    val resolved = resolveRefIfPresent(branch, ref.resolvedValueBaseUri)
                    expanded.add(ResolvedRef(resolved.resolvedValue, resolved.resolvedValueBaseUri, SchemaResolutionType.ALL_OF))
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
     * Navigate schema by document path tokens.
     *
     * This function translates document paths to schema paths by inserting schema-specific wrappers:
     * - For object properties: navigates through "properties" wrapper
     * - For array indices: navigates to "items" schema (all array elements share the same schema)
     * - Falls back to "additionalProperties" or "patternProperties" when specific property not found
     * - Resolves `$ref` references to their target schemas
     * - Handles combinators (allOf, anyOf, oneOf) which can create multiple schema branches
     *
     * Base URI tracking is handled internally to ensure correct `$ref` resolution.
     *
     * Returns a list because a single document path can match multiple schema locations:
     * - Property defined in multiple combinator branches
     * - Multiple patternProperties matching
     *
     * Example:
     * ```kotlin
     * // Document path: ["users", "0", "name"]
     * // Schema navigation: properties/users → items → properties/name
     * val idLookup = SchemaIdLookup(schemaRoot)
     * val schemaRefs = idLookup.navigateByDocumentPath(listOf("users", "0", "name"))
     * ```
     *
     * @param documentPathTokens Path through the document (from [KsonValueNavigation.navigateByTokens])
     * @return List of [ResolvedRef] containing all sub-schemas at that location (empty if not found)
     */
    fun navigateByDocumentPath(
        documentPathTokens: List<String>,
    ): List<ResolvedRef> {
        val startingBaseUri = ""

        if (documentPathTokens.isEmpty()) {
            // Even at root, resolve $ref if present
            val resolved = resolveRefIfPresent(schemaRootValue, startingBaseUri)
            return listOf(resolved)
        }

        // Track all current schema nodes we're exploring (can branch out due to combinators)
        var currentNodes = listOf(ResolvedRef(schemaRootValue, startingBaseUri))

        for (token in documentPathTokens) {
            val nextNodes = mutableListOf<ResolvedRef>()

            for ((node, baseUri) in currentNodes) {
                if (node !is KsonObject) {
                    continue
                }

                // Track $id changes for proper URI resolution
                var updatedBaseUri = baseUri
                node.propertyLookup[$$"$id"]?.let { idValue ->
                    if (idValue is KsonString) {
                        val fullyQualifiedId = resolveUri(idValue.value, baseUri)
                        updatedBaseUri = fullyQualifiedId.toString()
                    }
                }

                // Determine if this is an array index or property name
                val isArrayIndex = token.toIntOrNull() != null

                val navigatedNodes = if (isArrayIndex) {
                    // Array navigation: go to "items" schema
                    // We ignore the actual index - all array elements use the same schema
                    navigateArrayItems(node, updatedBaseUri)?.let { listOf(it) } ?: emptyList()
                } else {
                    // Object navigation: go to "properties" wrapper, then the property
                    navigateObjectProperty(node, token, updatedBaseUri)
                }

                // Resolve $ref for each navigated node
                for (navNode in navigatedNodes) {
                    val resolved = resolveRefIfPresent(navNode.resolvedValue, navNode.resolvedValueBaseUri)
                    nextNodes.add(ResolvedRef(resolved.resolvedValue, resolved.resolvedValueBaseUri, navNode.resolutionType))
                }
            }

            currentNodes = nextNodes
            if (currentNodes.isEmpty()) {
                break
            }
        }

        return currentNodes
    }

    /**
     * Navigate to the schema for array items.
     *
     * Looks for "items" or "additionalItems" schema properties.
     */
    private fun navigateArrayItems(schemaNode: KsonObject, currentBaseUri: String): ResolvedRef? {
        // Try "items" first (most common case)
        schemaNode.propertyLookup["items"]?.let {
            return ResolvedRef(it, currentBaseUri, SchemaResolutionType.ARRAY_ITEMS)
        }

        // Fallback to "additionalItems"
        return schemaNode.propertyLookup["additionalItems"]?.let {
            ResolvedRef(it, currentBaseUri, SchemaResolutionType.ARRAY_ITEMS)
        }
    }

    /**
     * Resolves a $ref in a schema value if present.
     *
     * @param value The schema value that might contain a $ref
     * @param currentBaseUri The current base URI for resolving the reference
     * @return A ResolvedRef with the resolved value and base URI
     */
    private fun resolveRefIfPresent(value: KsonValue, currentBaseUri: String): ResolvedRef {
        if (value is KsonObject) {
            val refValue = value.propertyLookup["\$ref"] as? KsonString
            if (refValue != null) {
                return resolveRef(refValue.value, currentBaseUri) ?: ResolvedRef(value, currentBaseUri)
            }
        }

        return ResolvedRef(value, currentBaseUri)
    }

    /**
     * Navigate through combinator schemas (allOf, anyOf, oneOf) to find property definitions.
     *
     * @param schemaNode The schema node containing combinators
     * @param propertyName The property to search for
     * @param currentBaseUri The current base URI for $ref resolution
     * @return All matching property schemas found across all combinator branches
     */
    private fun navigateThroughCombinators(
        schemaNode: KsonObject,
        propertyName: String,
        currentBaseUri: String
    ): List<ResolvedRef> {
        val results = mutableListOf<ResolvedRef>()

        // Helper to process a combinator array
        fun processCombinator(combinator: KsonValue?, resolutionType: SchemaResolutionType) {
            val combinatorList = combinator as? KsonList ?: return

            for (element in combinatorList.elements) {
                // Resolve $ref if present
                val resolved = resolveRefIfPresent(element, currentBaseUri)

                // Recursively navigate through this schema
                if (resolved.resolvedValue is KsonObject) {
                    val schema = resolved.resolvedValue
                    val schemaBaseUri = resolved.resolvedValueBaseUri
                    val nestedResults = navigateObjectProperty(schema, propertyName, schemaBaseUri)
                    // Tag nested results with the combinator type if they don't already have a combinator type
                    results.addAll(nestedResults.map { ref ->
                        if (ref.resolutionType in listOf(
                                SchemaResolutionType.DIRECT_PROPERTY,
                                SchemaResolutionType.PATTERN_PROPERTY,
                                SchemaResolutionType.ADDITIONAL_PROPERTY
                            )) {
                            ref.copy(resolutionType = resolutionType)
                        } else {
                            ref
                        }
                    })
                }
            }
        }

        // Process each type of combinator
        processCombinator(schemaNode.propertyLookup["allOf"], SchemaResolutionType.ALL_OF)
        processCombinator(schemaNode.propertyLookup["anyOf"], SchemaResolutionType.ANY_OF)
        processCombinator(schemaNode.propertyLookup["oneOf"], SchemaResolutionType.ONE_OF)

        return results
    }

    /**
     * Navigate an object schema to find all sub-schemas for a property.
     *
     * Handles multiple JSON Schema patterns:
     * 1. Direct property lookup in "properties"
     * 2. Pattern matching via "patternProperties" (can match multiple patterns)
     * 3. Combinator schemas ("allOf", "anyOf", "oneOf")
     * 4. Fallback to "additionalProperties"
     *
     * Returns a list because a property can be defined in multiple places:
     * - Multiple patternProperties can match
     * - Property can exist in multiple combinator branches
     */
    private fun navigateObjectProperty(
        schemaNode: KsonObject,
        propertyName: String,
        currentBaseUri: String
    ): List<ResolvedRef> {
        val results = mutableListOf<ResolvedRef>()

        // Try direct property lookup in "properties"
        val properties = schemaNode.propertyLookup["properties"] as? KsonObject
        properties?.propertyLookup?.get(propertyName)?.let {
            results.add(ResolvedRef(it, currentBaseUri, SchemaResolutionType.DIRECT_PROPERTY))
        }

        // Try pattern properties - check all patterns for a match
        val patternProperties = schemaNode.propertyLookup["patternProperties"] as? KsonObject
        patternProperties?.propertyMap?.forEach { (pattern, property) ->
            try {
                if (Regex(pattern).containsMatchIn(propertyName)) {
                    results.add(ResolvedRef(property.propValue, currentBaseUri, SchemaResolutionType.PATTERN_PROPERTY))
                }
            } catch (_: Throwable) {
                // Invalid regex pattern, skip it
                // Use Throwable to catch JavaScript SyntaxError and other platform-specific errors
            }
        }

        // Try combinators if we haven't found anything yet or if they exist
        // (allOf should be merged with existing results, anyOf/oneOf provide alternatives)
        if (schemaNode.propertyLookup.containsKey("allOf") ||
            schemaNode.propertyLookup.containsKey("anyOf") ||
            schemaNode.propertyLookup.containsKey("oneOf")) {
            results.addAll(navigateThroughCombinators(schemaNode, propertyName, currentBaseUri))
        }

        // Fallback to additionalProperties if nothing found
        if (results.isEmpty()) {
            schemaNode.propertyLookup["additionalProperties"]?.let {
                results.add(ResolvedRef(it, currentBaseUri, SchemaResolutionType.ADDITIONAL_PROPERTY))
            }
        }

        return results
    }

    companion object {
        /**
         * Recursively walks a schema value to collect all `$id` entries with fully-qualified URIs.
         *
         * @param schemaValue The current schema node to examine
         * @param idMap The map to collect fully-qualified $id entries into
         * @param currentBaseUri The current base URI context for resolving relative URIs
         */
        private fun walkSchemaForIds(
            schemaValue: KsonValue,
            idMap: MutableMap<String, KsonValue>,
            currentBaseUri: String
        ) {
            when (schemaValue) {
                is KsonObject -> {
                    var contextBaseUri = currentBaseUri

                    // Check for $id in this object
                    schemaValue.propertyLookup["\$id"]?.let { idValue ->
                        if (idValue is KsonString) {
                            val idString = idValue.value

                            // Resolve the ID to its fully-qualified form
                            val fullyQualifiedId = resolveUri(idString, currentBaseUri)
                            contextBaseUri = fullyQualifiedId.toString()
                            idMap[contextBaseUri] = schemaValue
                        }
                    }

                    // Recursively walk all property values with the updated context
                    schemaValue.propertyMap.values.forEach { propertyValue ->
                        walkSchemaForIds(propertyValue.propValue, idMap, contextBaseUri)
                    }
                }

                is KsonList -> {
                    // Recursively walk all list elements
                    schemaValue.elements.forEach { element ->
                        walkSchemaForIds(element, idMap, currentBaseUri)
                    }
                }

                else -> {
                    /** no-op, only [KsonObject] and [KsonValue] have children */
                }
            }
        }

        data class RefUriParts (
            val origin: String,
            val path: String,
            val fragment: String) {
            override fun toString(): String {
                return "$origin$path$fragment"
            }
        }

        /**
         * Parse the given string into [RefUriParts].  This is a simplified version of that parsing specified in
         * [RFC 3986 Section 3](https://datatracker.ietf.org/doc/html/rfc3986#section-3) targeted towards our
         * $ref parsing use case.
         * TODO we likely want to consider implementing a more formal parser implementation based on that specification
         */
        private fun parseUri(uri: String): RefUriParts {
            val origin = if (uri.contains("://")) {
                val scheme = uri.substringBefore("://")
                val authority = uri.substringAfter("://")
                    .substringBefore('/')
                    .substringBefore('#')
                "$scheme://$authority"
            } else if (uri.substringBefore("/").contains(":")){
                uri.substringBefore("#")
            } else {
                ""
            }

            val afterOrigin = uri.substringAfter(origin)
            val path = if (origin.isBlank()) {
                afterOrigin.substringBeforeLast('#')
            } else if (afterOrigin.isNotBlank() && !afterOrigin.startsWith("#")) {
                "/" + afterOrigin.removePrefix("/").substringBefore('#')
            } else {
                ""
            }

            val fragment = if (uri.contains('#')) {
                "#" + uri.substringAfter('#')
            } else {
                ""
            }

            return RefUriParts(origin, path, fragment)
        }

        /**
         * Resolve [uri] in the context of [baseUri].
         *
         * This works analogously to how url updates in a web browsers: if you are "on" [baseUri] and "click" on
         *   and link with href="[uri]", you will be sent to the uri defined by the returned [RefUriParts]
         *
         * NOTE: this attempts to implement the rules specified in [RFC 3986](https://datatracker.ietf.org/doc/html/rfc3986#section-5)
         *   but is a little ad-hoc compared to what is there.  If/when bugs creep up with their root cause in this
         *   method, let's more carefully port the behavior specified there
         */
        fun resolveUri(uri: String, baseUri: String): RefUriParts {
            val uriParts = parseUri(uri)
            val baseUriParts = parseUri(baseUri)
            val origin = uriParts.origin.ifBlank { baseUriParts.origin }
            val path = if (uriParts.path.startsWith('/')) {
                uriParts.path
            } else if (uriParts.path.isNotBlank()) {
                baseUriParts.path.substringBeforeLast("/") + "/" + uriParts.path.removePrefix("/")
            }
            else {
                baseUriParts.path
            }
            return RefUriParts(origin, path, uriParts.fragment)
        }
    }
}

/**
 * Decodes percent-encoded characters in a URI. According to RFC 6901, percent-encoding must be decoded
 * before JSON Pointer processing.
 *
 * @param encoded The percent-encoded string
 * @return The decoded string
 */
private fun decodeUriEncoding(encoded: String): String {
    if (!encoded.contains('%')) {
        return encoded
    }

    val result = StringBuilder()
    var i = 0
    while (i < encoded.length) {
        val char = encoded[i]
        if (char == '%' && i + 2 < encoded.length) {
            // Try to decode the next two characters as hex
            val hex = encoded.substring(i + 1, i + 3)
            val decoded = try {
                hex.toInt(16).toChar()
            } catch (_: NumberFormatException) {
                // Invalid hex sequence, keep the % and continue
                result.append(char)
                i++
                continue
            }
            result.append(decoded)
            i += 3
        } else {
            result.append(char)
            i++
        }
    }
    return result.toString()
}

/**
 * Resolves a JSON Pointer path within a [KsonValue] structure.
 *
 * @param pointer The JSON Pointer string (e.g., "/definitions/address")
 * @param ksonValue The [KsonValue] to traverse
 * @return The [KsonValue] at the pointer location, or null if not found
 */
private fun resolveJsonPointer(pointer: String, ksonValue: KsonValue, currentBaseUri: String): ResolvedRef? {
    return when (val parseResult = JsonPointerParser(pointer).parse()) {
        is JsonPointerParser.ParseResult.Success -> {
            val resolvedValue = KsonValueNavigation.navigateByTokens(ksonValue, parseResult.tokens)
            val resolvedBaseUri = updateBaseUriAlongPath(ksonValue, parseResult.tokens, currentBaseUri)
            resolvedValue?.let { ResolvedRef(it, resolvedBaseUri) }
        }

        is JsonPointerParser.ParseResult.Error -> {
            // Invalid JSON Pointer
            null
        }
    }
}

/**
 * Describes how a schema was resolved during navigation.
 * This information is used by completion providers to determine which schemas need validation.
 */
enum class SchemaResolutionType {
    /** Schema found via direct property lookup in "properties" */
    DIRECT_PROPERTY,
    /** Schema found via pattern matching in "patternProperties" */
    PATTERN_PROPERTY,
    /** Schema from "additionalProperties" fallback */
    ADDITIONAL_PROPERTY,
    /** Schema from "items" or "additionalItems" for array elements */
    ARRAY_ITEMS,
    /** Schema from "allOf" combinator - all branches must be valid */
    ALL_OF,
    /** Schema from "anyOf" combinator - at least one branch must be valid */
    ANY_OF,
    /** Schema from "oneOf" combinator - exactly one branch must be valid */
    ONE_OF,
    /** Root schema or schema resolved via $ref */
    ROOT
}

data class ResolvedRef(
    val resolvedValue: KsonValue,
    val resolvedValueBaseUri: String,
    val resolutionType: SchemaResolutionType = SchemaResolutionType.ROOT
)

/**
 * Updates the base URI while following a path of JSON Pointer tokens.
 *
 * @param current The current [KsonValue] node to start from
 * @param tokens The list of reference tokens to follow
 * @param currentBaseUri The starting base URI
 * @return The updated base URI after following the token path
 */
private fun updateBaseUriAlongPath(current: KsonValue, tokens: List<String>, currentBaseUri: String): String {
    var node = current
    var updatedBaseUri = currentBaseUri

    for (token in tokens) {
        // Update base URI if current node has a $id property
        if (node is KsonObject) {
            node.propertyLookup["\$id"]?.let { idValue ->
                if (idValue is KsonString) {
                    updatedBaseUri = SchemaIdLookup.resolveUri(idValue.value, updatedBaseUri).toString()
                }
            }
        }

        // Navigate to next node
        node = KsonValueNavigation.navigateByTokens(node, listOf(token)) ?: break
    }

    return updatedBaseUri
}

/**
 * Built-in Draft-07 meta-schema
 */
data object Draft7MetaSchema {
    const val ID = "http://json-schema.org/draft-07/schema"
    val schemaValue = KsonCore.parseToAst("""
        '${'$'}schema': 'http://json-schema.org/draft-07/schema#'
        '${'$'}id': 'http://json-schema.org/draft-07/schema#'
        title: 'Core schema meta-schema'
        definitions:
          schemaArray:
            type: array
            minItems: 1
            items:
              '${'$'}ref': '#'
              .
            .
          nonNegativeInteger:
            type: integer
            minimum: 0
            .
          nonNegativeIntegerDefault0:
            allOf:
              - '${'$'}ref': '#/definitions/nonNegativeInteger'
              - default: 0
                .
            .
          simpleTypes:
            enum:
              - array
              - boolean
              - integer
              - 'null'
              - number
              - object
              - string
            .
          stringArray:
            type: array
            items:
              type: string
              .
            uniqueItems: true
            default:
              <>
            .
          .
        type:
          - object
          - boolean
        properties:
          '${'$'}id':
            type: string
            format: 'uri-reference'
            .
          '${'$'}schema':
            type: string
            format: uri
            .
          '${'$'}ref':
            type: string
            format: 'uri-reference'
            .
          '${'$'}comment':
            type: string
            .
          title:
            type: string
            .
          description:
            type: string
            .
          default: true
          readOnly:
            type: boolean
            default: false
            .
          writeOnly:
            type: boolean
            default: false
            .
          examples:
            type: array
            items: true
            .
          multipleOf:
            type: number
            exclusiveMinimum: 0
            .
          maximum:
            type: number
            .
          exclusiveMaximum:
            type: number
            .
          minimum:
            type: number
            .
          exclusiveMinimum:
            type: number
            .
          maxLength:
            '${'$'}ref': '#/definitions/nonNegativeInteger'
            .
          minLength:
            '${'$'}ref': '#/definitions/nonNegativeIntegerDefault0'
            .
          pattern:
            type: string
            format: regex
            .
          additionalItems:
            '${'$'}ref': '#'
            .
          items:
            anyOf:
              - '${'$'}ref': '#'
              - '${'$'}ref': '#/definitions/schemaArray'
                .
            default: true
            .
          maxItems:
            '${'$'}ref': '#/definitions/nonNegativeInteger'
            .
          minItems:
            '${'$'}ref': '#/definitions/nonNegativeIntegerDefault0'
            .
          uniqueItems:
            type: boolean
            default: false
            .
          contains:
            '${'$'}ref': '#'
            .
          maxProperties:
            '${'$'}ref': '#/definitions/nonNegativeInteger'
            .
          minProperties:
            '${'$'}ref': '#/definitions/nonNegativeIntegerDefault0'
            .
          required:
            '${'$'}ref': '#/definitions/stringArray'
            .
          additionalProperties:
            '${'$'}ref': '#'
            .
          definitions:
            type: object
            additionalProperties:
              '${'$'}ref': '#'
              .
            default:
              {}
            .
          properties:
            type: object
            additionalProperties:
              '${'$'}ref': '#'
              .
            default:
              {}
            .
          patternProperties:
            type: object
            additionalProperties:
              '${'$'}ref': '#'
              .
            propertyNames:
              format: regex
              .
            default:
              {}
            .
          dependencies:
            type: object
            additionalProperties:
              anyOf:
                - '${'$'}ref': '#'
                - '${'$'}ref': '#/definitions/stringArray'
                  .
              .
            .
          propertyNames:
            '${'$'}ref': '#'
            .
          const: true
          enum:
            type: array
            items: true
            minItems: 1
            uniqueItems: true
            .
          type:
            anyOf:
              - '${'$'}ref': '#/definitions/simpleTypes'
              - type: array
                items:
                  '${'$'}ref': '#/definitions/simpleTypes'
                  .
                minItems: 1
                uniqueItems: true
                .
            .
          format:
            type: string
            .
          contentMediaType:
            type: string
            .
          contentEncoding:
            type: string
            .
          if:
            '${'$'}ref': '#'
            .
          then:
            '${'$'}ref': '#'
            .
          else:
            '${'$'}ref': '#'
            .
          allOf:
            '${'$'}ref': '#/definitions/schemaArray'
            .
          anyOf:
            '${'$'}ref': '#/definitions/schemaArray'
            .
          oneOf:
            '${'$'}ref': '#/definitions/schemaArray'
            .
          not:
            '${'$'}ref': '#'
            .
          .
        default: true
    """).ksonValue!!
}

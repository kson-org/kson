package org.kson.schema

import org.kson.parser.MessageSink
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.walker.KsonValueWalker
import org.kson.walker.navigateWithJsonPointer

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
        idMap[KsonDraft7MetaSchema.ID] = KsonDraft7MetaSchema.schemaValue

        if (schemaRootValue is KsonObject) {
            val rootBaseUri = schemaRootValue.propertyLookup["\$id"]?.let { idValue ->
                if (idValue is KsonString) {
                    idValue.value
                } else {
                    // this $id is completely invalid
                    null
                }
            } ?: ""

            // Store the root schema at its baseUri
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
            val decodedPointer = JsonPointer(decodeUriEncoding(resolvedRefUri.fragment.substring(1)))
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
            expandSingleSchema(ref, expanded)
        }

        return expanded
    }

    /**
     * Recursively expand a single schema's combinators and conditionals.
     *
     * Expands oneOf/anyOf/allOf into individual branches and if/then/else into
     * conditional branches, resolving any $ref along the way.  Branches are
     * themselves expanded recursively so that nested structures (e.g. allOf
     * containing if/then) are fully flattened.
     *
     * The parent schema is preserved alongside its branches so that its own
     * properties (title, description, constraints) remain available.
     */
    private fun expandSingleSchema(ref: ResolvedRef, expanded: MutableList<ResolvedRef>) {
        val schemaObj = ref.resolvedValue as? KsonObject

        if (schemaObj == null) {
            expanded.add(ref)
            return
        }

        var addedBranches = false
        // Track where to insert the parent (to add it first)
        val branchesStartIndex = expanded.size

        fun addBranch(branch: KsonValue, baseUri: String, resolutionType: SchemaResolutionType) {
            val resolved = resolveRefIfPresent(branch, baseUri)
            // Preserve the parentBranch from navigation through expansion — the
            // branch context from the original oneOf/anyOf is still the right one
            // for sibling filtering regardless of how deeply the schema expands.
            val branchRef = ResolvedRef(resolved.resolvedValue, resolved.resolvedValueBaseUri, resolutionType, ref.parentBranch)
            expandSingleSchema(branchRef, expanded)
            addedBranches = true
        }

        // Check for oneOf
        (schemaObj.propertyLookup["oneOf"] as? KsonList)?.elements?.forEach { branch ->
            addBranch(branch, ref.resolvedValueBaseUri, SchemaResolutionType.ONE_OF)
        }

        // Check for anyOf
        (schemaObj.propertyLookup["anyOf"] as? KsonList)?.elements?.forEach { branch ->
            addBranch(branch, ref.resolvedValueBaseUri, SchemaResolutionType.ANY_OF)
        }

        // Check for allOf
        (schemaObj.propertyLookup["allOf"] as? KsonList)?.elements?.forEach { branch ->
            addBranch(branch, ref.resolvedValueBaseUri, SchemaResolutionType.ALL_OF)
        }

        // Check for if/then/else conditionals
        if (schemaObj.propertyLookup.containsKey("if")) {
            schemaObj.propertyLookup["then"]?.let { thenBranch ->
                addBranch(thenBranch, ref.resolvedValueBaseUri, SchemaResolutionType.IF_THEN)
            }
            schemaObj.propertyLookup["else"]?.let { elseBranch ->
                addBranch(elseBranch, ref.resolvedValueBaseUri, SchemaResolutionType.IF_ELSE)
            }
        }

        if (addedBranches) {
            // Include the parent schema to preserve its properties (e.g., description, title, constraints)
            // Insert at the start so it appears first in hover info
            expanded.add(branchesStartIndex, ref)
        } else {
            // If we didn't add any branches, keep the original schema
            expanded.add(ref)
        }
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
     * @param documentPointer Pointer through the document (from [org.kson.walker.navigateToLocationWithPointer])
     * @return List of [ResolvedRef] containing all sub-schemas at that location (empty if not found)
     */
    fun navigateByDocumentPointer(
        documentPointer: JsonPointer,
        documentValue: KsonValue? = null,
    ): List<ResolvedRef> {
        val startingBaseUri = ""
        val documentPathTokens = documentPointer.tokens
        if (documentPathTokens.isEmpty()) {
            // Even at root, resolve $ref if present
            val resolved = resolveRefIfPresent(schemaRootValue, startingBaseUri)
            return listOf(resolved)
        }

        // Track all current schema nodes we're exploring (can branch out due to combinators)
        var currentNodes = listOf(resolveRefIfPresent(schemaRootValue, startingBaseUri))
        // Track the document value at the current navigation level for if/then evaluation
        var currentDocumentValue = documentValue

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
                    navigateArrayItems(node, updatedBaseUri)
                } else {
                    // Object navigation: go to "properties" wrapper, then the property
                    navigateObjectProperty(node, token, updatedBaseUri, currentDocumentValue)
                }

                // Resolve $ref for each navigated node, preserving parentBranch context
                for (navNode in navigatedNodes) {
                    val resolved = resolveRefIfPresent(navNode.resolvedValue, navNode.resolvedValueBaseUri)
                    nextNodes.add(ResolvedRef(resolved.resolvedValue, resolved.resolvedValueBaseUri, navNode.resolutionType, navNode.parentBranch))
                }
            }

            currentNodes = nextNodes
            if (currentNodes.isEmpty()) {
                break
            }

            // Advance document value in parallel with schema navigation
            currentDocumentValue = currentDocumentValue?.let { docVal ->
                KsonValueWalker.navigateWithJsonPointer(docVal, JsonPointer.fromTokens(listOf(token)))
            }
        }

        return currentNodes
    }

    /**
     * Navigate to the schema for array items.
     *
     * Looks for "items" or "additionalItems" schema properties.
     * Also searches through combinators (anyOf/oneOf/allOf) to find items schemas.
     */
    private fun navigateArrayItems(schemaNode: KsonObject, currentBaseUri: String): List<ResolvedRef> {
        val results = mutableListOf<ResolvedRef>()

        // Try "items" first (most common case)
        schemaNode.propertyLookup["items"]?.let {
            results.add(ResolvedRef(it, currentBaseUri, SchemaResolutionType.ARRAY_ITEMS))
        }

        // Fallback to "additionalItems"
        schemaNode.propertyLookup["additionalItems"]?.let {
            results.add(ResolvedRef(it, currentBaseUri, SchemaResolutionType.ARRAY_ITEMS))
        }

        // If no items found directly, search through combinators
        if (results.isEmpty() &&
            (schemaNode.propertyLookup.containsKey("allOf") ||
             schemaNode.propertyLookup.containsKey("anyOf") ||
             schemaNode.propertyLookup.containsKey("oneOf"))) {
            results.addAll(navigateThroughCombinators(
                schemaNode = schemaNode,
                currentBaseUri = currentBaseUri,
                recursiveNavigate = { schema, baseUri -> navigateArrayItems(schema, baseUri) },
                shouldTagWithCombinator = { it == SchemaResolutionType.ARRAY_ITEMS }
            ))
        }

        // If no items found, search through if/then/else conditionals
        if (results.isEmpty() && schemaNode.propertyLookup.containsKey("if")) {
            results.addAll(navigateThroughConditionals(
                schemaNode = schemaNode,
                currentBaseUri = currentBaseUri,
                recursiveNavigate = { schema, baseUri -> navigateArrayItems(schema, baseUri) }
            ))
        }

        return results
    }

    /**
     * Resolves a `$ref` in a schema value if present.
     *
     * Public to support downstream `$ref` resolution within schema branches, e.g.,
     * when checking property constraints inside oneOf/anyOf branches.
     *
     * @param value The schema value that might contain a `$ref`
     * @param currentBaseUri The current base URI for resolving the reference
     * @return A [ResolvedRef] with the resolved value and base URI
     */
    fun resolveRefIfPresent(value: KsonValue, currentBaseUri: String): ResolvedRef {
        if (value is KsonObject) {
            val refValue = value.propertyLookup["\$ref"] as? KsonString
            if (refValue != null) {
                return resolveRef(refValue.value, currentBaseUri) ?: ResolvedRef(value, currentBaseUri)
            }
        }

        return ResolvedRef(value, currentBaseUri)
    }

    /**
     * Navigate through combinator schemas (allOf, anyOf, oneOf) to find matching sub-schemas.
     *
     * This is a shared implementation used by both array and object navigation.
     * For each combinator branch, it resolves any $ref, then delegates to the caller's
     * navigation function to continue traversal.
     *
     * For oneOf/anyOf results, the branch schema is attached as [ResolvedRef.parentBranch]
     * so that downstream filtering can validate sibling property constraints that aren't
     * visible from the leaf schema alone.
     *
     * @param schemaNode The schema node containing combinators
     * @param currentBaseUri The current base URI for $ref resolution
     * @param recursiveNavigate How to continue navigation (e.g., navigateArrayItems or navigateObjectProperty)
     * @param shouldTagWithCombinator Which resolution types to overwrite with combinator type (e.g., ARRAY_ITEMS -> ANY_OF)
     */
    private fun navigateThroughCombinators(
        schemaNode: KsonObject,
        currentBaseUri: String,
        recursiveNavigate: (schema: KsonObject, baseUri: String) -> List<ResolvedRef>,
        shouldTagWithCombinator: (resolutionType: SchemaResolutionType) -> Boolean
    ): List<ResolvedRef> {
        val results = mutableListOf<ResolvedRef>()

        fun processCombinator(combinator: KsonValue?, combinatorType: SchemaResolutionType) {
            val combinatorList = combinator as? KsonList ?: return
            val attachParentBranch = combinatorType in setOf(SchemaResolutionType.ONE_OF, SchemaResolutionType.ANY_OF)

            for (element in combinatorList.elements) {
                val resolved = resolveRefIfPresent(element, currentBaseUri)

                if (resolved.resolvedValue is KsonObject) {
                    val nestedResults = recursiveNavigate(resolved.resolvedValue, resolved.resolvedValueBaseUri)

                    // Attach parentBranch for oneOf/anyOf so downstream filtering
                    // can check sibling constraints.
                    results.addAll(nestedResults.map { ref ->
                        if (shouldTagWithCombinator(ref.resolutionType)) {
                            ref.copy(
                                resolutionType = combinatorType,
                                parentBranch = if (attachParentBranch) resolved else null
                            )
                        } else {
                            ref
                        }
                    })
                }
            }
        }

        processCombinator(schemaNode.propertyLookup["allOf"], SchemaResolutionType.ALL_OF)
        processCombinator(schemaNode.propertyLookup["anyOf"], SchemaResolutionType.ANY_OF)
        processCombinator(schemaNode.propertyLookup["oneOf"], SchemaResolutionType.ONE_OF)

        return results
    }

    /**
     * Navigate through if/then/else conditional schemas.
     *
     * When [documentValue] is available, evaluates the "if" condition against it
     * and only includes the matching branch (then or else).  When unavailable,
     * includes both branches so that all possible schemas are discoverable.
     *
     * @param schemaNode The schema node containing if/then/else
     * @param currentBaseUri The current base URI for $ref resolution
     * @param documentValue The document value at this schema level, used to evaluate the "if" condition
     * @param recursiveNavigate How to continue navigation within each branch
     */
    private fun navigateThroughConditionals(
        schemaNode: KsonObject,
        currentBaseUri: String,
        documentValue: KsonValue? = null,
        recursiveNavigate: (schema: KsonObject, baseUri: String) -> List<ResolvedRef>
    ): List<ResolvedRef> {
        val results = mutableListOf<ResolvedRef>()

        fun processConditionalBranch(branch: KsonValue?, resolutionType: SchemaResolutionType) {
            branch ?: return
            val resolved = resolveRefIfPresent(branch, currentBaseUri)
            if (resolved.resolvedValue is KsonObject) {
                val nestedResults = recursiveNavigate(resolved.resolvedValue, resolved.resolvedValueBaseUri)
                // Unlike navigateThroughCombinators, we always tag results with the
                // conditional type.  This tagging is what SchemaFilteringService uses
                // to identify branches that need validation-based filtering.
                results.addAll(nestedResults.map { ref ->
                    ref.copy(resolutionType = resolutionType)
                })
            }
        }

        // When we have a document value, evaluate the "if" condition to determine
        // which branch to include.  This is critical when the "if" condition
        // checks a sibling property that isn't visible from the target property.
        val ifCondition = schemaNode.propertyLookup["if"]
        if (documentValue != null && ifCondition != null) {
            val ifSchema = SchemaParser.parseSchemaElement(ifCondition, MessageSink(), currentBaseUri, this)
            if (ifSchema != null) {
                val conditionMatches = ifSchema.isValid(documentValue, MessageSink())
                if (conditionMatches) {
                    processConditionalBranch(schemaNode.propertyLookup["then"], SchemaResolutionType.IF_THEN)
                } else {
                    processConditionalBranch(schemaNode.propertyLookup["else"], SchemaResolutionType.IF_ELSE)
                }
                return results
            }
        }

        // No document value or couldn't parse if condition — include both branches
        processConditionalBranch(schemaNode.propertyLookup["then"], SchemaResolutionType.IF_THEN)
        processConditionalBranch(schemaNode.propertyLookup["else"], SchemaResolutionType.IF_ELSE)

        return results
    }

    /**
     * Navigate an object schema to find all sub-schemas for a property.
     *
     * Handles multiple JSON Schema patterns:
     * 1. Direct property lookup in "properties"
     * 2. Pattern matching via "patternProperties" (can match multiple patterns)
     * 3. Combinator schemas ("allOf", "anyOf", "oneOf")
     * 4. Conditional schemas ("if"/"then"/"else")
     * 5. Fallback to "additionalProperties"
     *
     * Returns a list because a property can be defined in multiple places:
     * - Multiple patternProperties can match
     * - Property can exist in multiple combinator branches
     * - Property can exist in both then and else conditional branches
     */
    private fun navigateObjectProperty(
        schemaNode: KsonObject,
        propertyName: String,
        currentBaseUri: String,
        documentValue: KsonValue? = null
    ): List<ResolvedRef> {
        val results = mutableListOf<ResolvedRef>()

        // Try direct property lookup in "properties"
        val properties = schemaNode.propertyLookup["properties"] as? KsonObject
        properties?.propertyMap?.get(propertyName)?.let {
            results.add(ResolvedRef(it.propValue, currentBaseUri, SchemaResolutionType.DIRECT_PROPERTY))
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
            results.addAll(navigateThroughCombinators(
                schemaNode = schemaNode,
                currentBaseUri = currentBaseUri,
                recursiveNavigate = { schema, baseUri -> navigateObjectProperty(schema, propertyName, baseUri, documentValue) },
                shouldTagWithCombinator = { it in listOf(
                    SchemaResolutionType.DIRECT_PROPERTY,
                    SchemaResolutionType.PATTERN_PROPERTY,
                    SchemaResolutionType.ADDITIONAL_PROPERTY
                )}
            ))
        }

        // Try if/then/else conditionals
        if (schemaNode.propertyLookup.containsKey("if")) {
            results.addAll(navigateThroughConditionals(
                schemaNode = schemaNode,
                currentBaseUri = currentBaseUri,
                documentValue = documentValue,
                recursiveNavigate = { schema, baseUri -> navigateObjectProperty(schema, propertyName, baseUri, documentValue) }
            ))
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
         * NOTE: this implements reference transformation from
         *   [RFC 3986 §5.2.2](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.2) — including its
         *   scheme/authority inheritance cases and the
         *   [§5.2.3](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.3) merge-paths sub-operation.
         *   It does not yet perform the
         *   [§5.2.4](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.4) remove-dot-segments sub-operation;
         *   add that when a case needs it.
         */
        fun resolveUri(uri: String, baseUri: String): RefUriParts {
            val uriParts = parseUri(uri)
            val baseUriParts = parseUri(baseUri)
            val origin = uriParts.origin.ifBlank { baseUriParts.origin }
            val path = when {
                // Absolute-path reference: use as-is.
                uriParts.path.startsWith('/') -> uriParts.path
                // Empty reference path: inherit the base path.
                uriParts.path.isEmpty() -> baseUriParts.path
                // Relative reference path: merge with base path per RFC 3986 §5.2.3.
                // If the base has an authority and an empty path, the merged path
                // starts with "/". Otherwise, replace the last segment of the base
                // path with the reference path; if the base path has no "/", the
                // entire base path is discarded.
                baseUriParts.origin.isNotEmpty() && baseUriParts.path.isEmpty() ->
                    "/" + uriParts.path
                else -> {
                    val lastSlash = baseUriParts.path.lastIndexOf('/')
                    if (lastSlash < 0) {
                        uriParts.path
                    } else {
                        baseUriParts.path.substring(0, lastSlash + 1) + uriParts.path
                    }
                }
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
private fun resolveJsonPointer(pointer: JsonPointer, ksonValue: KsonValue, currentBaseUri: String): ResolvedRef? {
    val resolvedValue = KsonValueWalker.navigateWithJsonPointer(ksonValue, pointer)
    val resolvedBaseUri = updateBaseUriAlongPath(ksonValue, pointer, currentBaseUri)
    return resolvedValue?.let { ResolvedRef(it, resolvedBaseUri) }
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
    /** Schema from "then" branch of an if/then conditional */
    IF_THEN,
    /** Schema from "else" branch of an if/then/else conditional */
    IF_ELSE,
    /** Root schema or schema resolved via $ref */
    ROOT
}

/**
 * A schema node resolved during navigation, carrying the context of how it was found.
 *
 * @param resolvedValue The schema value at this location
 * @param resolvedValueBaseUri The base URI for resolving `$ref` within this schema
 * @param resolutionType How this schema was reached (direct property, combinator branch, etc.)
 * @param parentBranch For schemas found inside a oneOf/anyOf branch, the branch schema that
 *   contained this result.  This allows downstream filtering (e.g., [SchemaFilteringService])
 *   to validate the branch against the parent document object — checking sibling property
 *   constraints that aren't visible from the leaf schema alone.
 */
data class ResolvedRef(
    val resolvedValue: KsonValue,
    val resolvedValueBaseUri: String,
    val resolutionType: SchemaResolutionType = SchemaResolutionType.ROOT,
    val parentBranch: ResolvedRef? = null
)

/**
 * Updates the base URI while following a path of JSON Pointer tokens.
 *
 * @param current The current [KsonValue] node to start from
 * @param pointer The [JsonPointer] to follow
 * @param currentBaseUri The starting base URI
 * @return The updated base URI after following the token path
 */
private fun updateBaseUriAlongPath(current: KsonValue, pointer: JsonPointer, currentBaseUri: String): String {
    var node = current
    var updatedBaseUri = currentBaseUri

    for (token in pointer.tokens) {
        // Update base URI if current node has a $id property
        if (node is KsonObject) {
            node.propertyLookup["\$id"]?.let { idValue ->
                if (idValue is KsonString) {
                    updatedBaseUri = SchemaIdLookup.resolveUri(idValue.value, updatedBaseUri).toString()
                }
            }
        }

        // Navigate to next node
        node = KsonValueWalker.navigateWithJsonPointer(node, JsonPointer.fromTokens(listOf(token))) ?: break
    }

    return updatedBaseUri
}

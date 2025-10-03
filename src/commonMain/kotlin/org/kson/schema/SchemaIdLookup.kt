package org.kson.schema

import org.kson.*
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/**
 * Manages the mapping of `$id` values to their corresponding schema nodes for `$ref` resolution.
 *
 * @param schemaRootValue the [KsonValue] root of the schema to build this [SchemaIdLookup] from
 */
class SchemaIdLookup(schemaRootValue: KsonValue) {

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
            } catch (e: NumberFormatException) {
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
            navigatePointer(ksonValue, parseResult.tokens, currentBaseUri)
        }

        is JsonPointerParser.ParseResult.Error -> {
            // Invalid JSON Pointer
            null
        }
    }
}

data class ResolvedRef(val resolvedValue: KsonValue, val resolvedValueBaseUri: String)

/**
 * Navigates through a [KsonValue] structure using JSON Pointer tokens.
 *
 * @param current The current [KsonValue] node
 * @param tokens The list of reference tokens to follow
 * @return The [KsonValue] at the final location, or null if path not found
 */
private fun navigatePointer(current: KsonValue, tokens: List<String>, currentBaseUri: String): ResolvedRef? {
    if (tokens.isEmpty()) {
        return ResolvedRef(current, currentBaseUri)
    }

    var node: KsonValue? = current
    var updatedBaseUri = currentBaseUri

    for (token in tokens) {
        node = when (node) {
            is KsonObject -> {
                node.propertyLookup["\$id"]?.let { idValue ->
                    if (idValue is KsonString) {
                        val idString = idValue.value
                        // Resolve the ID relative to the current base URI and update it
                        val fullyQualifiedId = SchemaIdLookup.resolveUri(idString, updatedBaseUri)
                        updatedBaseUri = fullyQualifiedId.toString()
                    }
                }

                // Navigate into object property
                node.propertyLookup[token]
            }

            is KsonList -> {
                // Navigate into array element
                val index = token.toIntOrNull()
                if (index != null && index >= 0 && index < node.elements.size) {
                    node.elements[index]
                } else {
                    null
                }
            }

            else -> {
                // Cannot navigate further
                null
            }
        }

        if (node == null) {
            break
        }
    }

    return node?.let { ResolvedRef(it, updatedBaseUri) }
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

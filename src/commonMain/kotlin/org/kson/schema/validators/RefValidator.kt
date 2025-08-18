package org.kson.schema.validators

import org.kson.KsonList
import org.kson.KsonObject
import org.kson.KsonValue
import org.kson.parser.MessageSink
import org.kson.schema.*

/**
 * Validator for JSON Schema `$ref` references
 *
 * @param [refSchemaValue] the JSON Schema for this $ref (likely obtained with [resolveRef])
 * @param [rootSchemaValue] the root JSON Schema which contains this ref (used for recursive $ref
 *  lookups that may be in [refSchemaValue]
 */
class RefValidator(
    private val refSchemaValue: KsonValue,
    private val rootSchemaValue: KsonValue
) : JsonSchemaValidator {
    private val refSchema: JsonSchema? by lazy {
        // Parse the resolved value as a schema, passing the root for nested refs
        val messageSink = MessageSink()
        // TODO these parsed $ref schemas should be cached for efficiency
        SchemaParser.parseSchemaElement(refSchemaValue, messageSink, rootSchemaValue)
    }

    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val schema = refSchema
            ?: // Schema parsing failed, so can't perform validation against it
            return

        // Validate the value against our referenced schema
        schema.validate(ksonValue, messageSink)
    }
}

/**
 * Resolves a $ref string to the referenced schema value.
 *
 * @param ref The reference string (e.g., "#/definitions/address")
 * @param rootSchemaValue The root schema as a [KsonValue]
 * @return The resolved [KsonValue] representing the referenced schema, or null if not found or circular
 */
fun resolveRef(ref: String, rootSchemaValue: KsonValue): KsonValue? {
    // Handle different reference formats
    return when {
        ref == "#" -> {
            // Reference to root schema
            rootSchemaValue
        }

        ref.startsWith("#/") -> {
            // JSON Pointer reference within document
            val decodedPointer = decodeUriEncoding(ref.substring(1))
            resolveJsonPointer(decodedPointer, rootSchemaValue)
        }

        ref.startsWith("#") && ref.length > 1 -> {
            // Anchor reference or other fragment identifier
            // For now, treat as JSON Pointer without the leading slash
            val decodedFragment = decodeUriEncoding(ref.substring(1))
            resolveJsonPointer("/$decodedFragment", rootSchemaValue)
        }

        !ref.contains("#") -> {
            // External reference without fragment - not yet supported
            null
        }

        else -> {
            // External reference with fragment - not yet supported
            null
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
 * Resolves a JSON Pointer path within a KsonValue structure.
 *
 * @param pointer The JSON Pointer string (e.g., "/definitions/address")
 * @param root The root KsonValue to traverse
 * @return The KsonValue at the pointer location, or null if not found
 */
private fun resolveJsonPointer(pointer: String, root: KsonValue): KsonValue? {
    return when (val parseResult = JsonPointerParser(pointer).parse()) {
        is JsonPointerParser.ParseResult.Success -> {
            navigatePointer(root, parseResult.tokens)
        }

        is JsonPointerParser.ParseResult.Error -> {
            // Invalid JSON Pointer
            null
        }
    }
}

/**
 * Navigates through a KsonValue structure using JSON Pointer tokens.
 *
 * @param current The current KsonValue node
 * @param tokens The list of reference tokens to follow
 * @return The KsonValue at the final location, or null if path not found
 */
private fun navigatePointer(current: KsonValue, tokens: List<String>): KsonValue? {
    if (tokens.isEmpty()) {
        return current
    }

    var node: KsonValue? = current

    for (token in tokens) {
        node = when (node) {
            is KsonObject -> {
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

    return node
}

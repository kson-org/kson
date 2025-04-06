package org.kson.testSupport

import kotlinx.serialization.json.Json

/**
 * Validate whether the given [jsonString] parses as legal JSON
 */
fun validateJson(jsonString: String) {
    try {
        Json.parseToJsonElement(jsonString)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
    }
}

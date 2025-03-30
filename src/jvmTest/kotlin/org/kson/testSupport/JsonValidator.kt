package org.kson.testSupport

import kotlinx.serialization.json.Json

actual fun validateJson(jsonString: String) {
    try {
        Json.parseToJsonElement(jsonString)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
    }
} 
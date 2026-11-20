package org.kson.value.navigation.json_pointer

/** Thrown for a string that is no valid [JsonPointer] or [JsonPointerGlob]. */
class InvalidJsonPointerException(message: String) : IllegalArgumentException(message)

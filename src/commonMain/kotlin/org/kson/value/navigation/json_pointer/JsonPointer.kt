package org.kson.value.navigation.json_pointer

/**
 * Represents a validated JSON Pointer according to RFC 6901.
 *
 * A JSON Pointer is a Unicode string containing zero or more reference tokens, each prefixed by '/'.
 * This class wraps a JSON Pointer string and ensures it's valid at construction time.
 *
 * Examples:
 * ```kotlin
 * val pointer1 = JsonPointer("")              // Root document
 * val pointer2 = JsonPointer("/foo")          // Property "foo"
 * val pointer3 = JsonPointer("/foo/0")        // First element of array at "foo"
 * val pointer4 = JsonPointer("/a~1b")         // Property "a/b" (escaped slash)
 * val pointer5 = JsonPointer("/m~0n")         // Property "m~n" (escaped tilde)
 * ```
 *
 * @property pointerString The JSON Pointer string (must be valid according to RFC 6901)
 * @throws IllegalArgumentException if the pointer string is invalid
 */
class JsonPointer(pointerString: String) : BaseJsonPointer(JsonPointerParser(pointerString)) {

    companion object {
        /**
         * The root pointer, representing the entire document.
         */
        val ROOT = JsonPointer("")

        /**
         * Creates a JsonPointer from a list of already-parsed string tokens.
         *
         * This is useful when you have tokens from navigation or other sources and need
         * to create a JsonPointer. The tokens will be properly escaped according to RFC 6901.
         *
         * @param tokens The reference tokens (as strings) to encode into a JSON Pointer
         * @return A JsonPointer representing the token path
         *
         * Example:
         * ```kotlin
         * val pointer = JsonPointer.fromTokens(listOf("users", "0", "name"))
         * // pointer.pointerString == "/users/0/name"
         *
         * val escapedPointer = JsonPointer.fromTokens(listOf("a/b", "m~n"))
         * // escapedPointer.pointerString == "/a~1b/m~0n"
         * ```
         */
        fun fromTokens(tokens: List<String>): JsonPointer {
            if (tokens.isEmpty()) {
                return ROOT
            }

            // Escape each token according to RFC 6901:
            // IMPORTANT: Must escape '~' first, then '/' (order matters!)
            // 1. Replace '~' with '~0'
            // 2. Replace '/' with '~1'
            val pointerString = tokens.joinToString(separator = "/", prefix = "/") { token ->
                token.replace("~", "~0").replace("/", "~1")
            }
            return JsonPointer(pointerString)
        }
    }
}

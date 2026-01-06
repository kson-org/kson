package org.kson.value.navigation.jsonPointer

/**
 * Represents a validated JsonPointerGlob - an extension of JSON Pointer with glob-style pattern matching.
 *
 * JsonPointerGlob extends RFC 6901 JSON Pointer with:
 * - Wildcard tokens: A token that is exactly `*` matches any single key or array index
 * - Glob patterns: A token containing `*` or `?` (but not only `*`) is treated as a pattern
 * - Backslash escaping: `\*`, `\?`, `\\` for literal characters in patterns
 * - RFC 6901 escaping: `~0` (tilde), `~1` (slash) still supported for compatibility
 *
 * Examples (STAR represents asterisk):
 * ```kotlin
 * val pointer1 = JsonPointerGlob("")                      // Root document
 * val pointer2 = JsonPointerGlob("/users/STAR/email")     // Email of any user (wildcard)
 * val pointer3 = JsonPointerGlob("/users/STARadminSTAR/role") // Role of users with "admin" in name (pattern)
 * val pointer4 = JsonPointerGlob("/config/\\STARvalue")   // Property "STARvalue" (escaped)
 * val pointer5 = JsonPointerGlob("/path/to\\\\from")      // Property "to\from" (escaped backslash)
 * ```
 *
 * @property pointerString The JsonPointerGlob string (must be valid)
 * @throws IllegalArgumentException if the pointer string is invalid
 */
class JsonPointerGlob(pointerString: String) : BaseJsonPointer(JsonPointerGlobParser(pointerString)) {

    companion object {
        val ROOT = JsonPointerGlob("")

        /**
         * Creates a JsonPointerGlob from a list of string tokens.
         *
         * String tokens are interpreted as follows:
         * - Exactly "*" becomes a Wildcard token
         * - Strings containing unescaped * or ? become GlobPattern tokens
         * - All other strings become Literal tokens
         *
         * Special characters will be properly escaped according to JsonPointerGlob rules.
         *
         * @param tokens The reference tokens (as strings) to encode into a JsonPointerGlob
         * @return A JsonPointerGlob representing the token path
         *
         * Example:
         * ```kotlin
         * val pointer = JsonPointerGlob.fromTokens(listOf("users", "*", "email"))
         * // Result: /users + / + * + / + email
         * ```
         */
        fun fromTokens(tokens: List<String>): JsonPointerGlob {
            if (tokens.isEmpty()) {
                return ROOT
            }

            // Build pointer string by encoding each token
            val pointerString = tokens.joinToString(separator = "/", prefix = "/") { token ->
                // For exact "*", no escaping needed - it will be interpreted as Wildcard
                if (token == "*") {
                    return@joinToString "*"
                }

                // For other strings, escape special characters
                // IMPORTANT: Must escape in the right order!
                // 1. Escape backslashes first: \ -> \\
                // 2. Escape wildcards: * -> \*, ? -> \?
                // 3. Escape RFC 6901: ~ -> ~0, / -> ~1
                token
                    .replace("\\", "\\\\")
                    .replace("*", "\\*")
                    .replace("?", "\\?")
                    .replace("~", "~0")
                    .replace("/", "~1")
            }
            return JsonPointerGlob(pointerString)
        }
    }
}


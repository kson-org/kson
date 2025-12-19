package org.kson.value.navigation.jsonPointer

/**
 * Represents a validated JsonPointerPlus - an extension of JSON Pointer with glob-style pattern matching.
 *
 * JsonPointerPlus extends RFC 6901 JSON Pointer with:
 * - Wildcard tokens: A token that is exactly `*` matches any single key or array index
 * - Glob patterns: A token containing `*` or `?` (but not only `*`) is treated as a pattern
 * - Backslash escaping: `\*`, `\?`, `\\` for literal characters in patterns
 * - RFC 6901 escaping: `~0` (tilde), `~1` (slash) still supported for compatibility
 *
 * Examples (STAR represents asterisk):
 * ```kotlin
 * val pointer1 = JsonPointerPlus("")                      // Root document
 * val pointer2 = JsonPointerPlus("/users/STAR/email")     // Email of any user (wildcard)
 * val pointer3 = JsonPointerPlus("/users/STARadminSTAR/role") // Role of users with "admin" in name (pattern)
 * val pointer4 = JsonPointerPlus("/config/\\STARvalue")   // Property "STARvalue" (escaped)
 * val pointer5 = JsonPointerPlus("/path/to\\\\from")      // Property "to\from" (escaped backslash)
 * ```
 *
 * @property pointerString The JsonPointerPlus string (must be valid)
 * @throws IllegalArgumentException if the pointer string is invalid
 */
class JsonPointerPlus(pointerString: String) : BaseJsonPointer(JsonPointerPlusParser(pointerString)) {

    companion object {
        val ROOT = JsonPointerPlus("")

        /**
         * Creates a JsonPointerPlus from a list of string tokens.
         *
         * String tokens are interpreted as follows:
         * - Exactly "*" becomes a Wildcard token
         * - Strings containing unescaped * or ? become GlobPattern tokens
         * - All other strings become Literal tokens
         *
         * Special characters will be properly escaped according to JsonPointerPlus rules.
         *
         * @param tokens The reference tokens (as strings) to encode into a JsonPointerPlus
         * @return A JsonPointerPlus representing the token path
         *
         * Example:
         * ```kotlin
         * val pointer = JsonPointerPlus.fromTokens(listOf("users", "*", "email"))
         * // Result: /users + / + * + / + email
         * ```
         */
        fun fromTokens(tokens: List<String>): JsonPointerPlus {
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
            return JsonPointerPlus(pointerString)
        }
    }
}


package org.kson.value.navigation.jsonPointer

/**
 * Represents a validated JsonPointerGlob - an extension of JSON Pointer with glob-style pattern matching.
 *
 * JsonPointerGlob extends RFC 6901 JSON Pointer with:
 * - Wildcard tokens: A token that is exactly * matches any single key or array index
 * - Recursive descent: A token that is exactly TWO asterisks matches zero or more levels
 * - Glob patterns: A token containing * or ? (but not only * or TWO asterisks) is treated as a pattern
 * - Backslash escaping: for literal characters in patterns
 * - RFC 6901 escaping: still supported for compatibility
 *
 * Examples:
 * - JsonPointerGlob("") for root document
 * - JsonPointerGlob("/users/asterisk/email") for email of any user (wildcard, use single asterisk)
 * - JsonPointerGlob("/users/doubleasterisk/email") for all emails at any depth under users (recursive, use two asterisks)
 * - JsonPointerGlob("/doubleasterisk/email") for all emails anywhere in document (recursive)
 * - JsonPointerGlob("/users/asteriskadminasterisk/role") for role of users with "admin" in name (pattern)
 *
 * Recursive descent behavior:
 * - Matches zero or more path segments (object keys or array indices)
 * - Performs depth-first traversal through the document tree
 * - Performance: O(n) where n is the size of the subtree being searched
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
         * - Exactly TWO asterisks becomes a RecursiveDescent token
         * - Exactly one asterisk becomes a Wildcard token
         * - Strings containing unescaped asterisks or question marks become GlobPattern tokens
         * - All other strings become Literal tokens
         *
         * Special characters will be properly escaped according to JsonPointerGlob rules.
         *
         * @param tokens The reference tokens (as strings) to encode into a JsonPointerGlob
         * @return A JsonPointerGlob representing the token path
         */
        fun fromTokens(tokens: List<String>): JsonPointerGlob {
            if (tokens.isEmpty()) {
                return ROOT
            }

            // Build pointer string by encoding each token
            val pointerString = tokens.joinToString(separator = "/", prefix = "/") { token ->
                // For exact TWO asterisks, no escaping needed - interpreted as RecursiveDescent
                if (token == "**") {
                    return@joinToString "**"
                }

                // For exact single asterisk, no escaping needed - interpreted as Wildcard
                if (token == "*") {
                    return@joinToString "*"
                }

                // For other strings, escape special characters
                // IMPORTANT: Must escape in the right order!
                // 1. Escape backslashes first
                // 2. Escape wildcards
                // 3. Escape RFC 6901
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


package org.kson.schema

/**
 * Utility for matching strings against glob-style patterns.
 *
 * Supports:
 * - `*` matches zero or more characters
 * - `?` matches exactly one character
 * - `\*` matches literal asterisk
 * - `\?` matches literal question mark
 * - `\\` matches literal backslash
 *
 * Examples:
 * ```kotlin
 * GlobMatcher.matches("user*", "user123")      // true
 * GlobMatcher.matches("*admin*", "superadmin") // true
 * GlobMatcher.matches("file?.txt", "file1.txt") // true
 * GlobMatcher.matches("a\\*b", "a*b")          // true (literal asterisk)
 * ```
 */
object GlobMatcher {
    /**
     * Check if a value matches a glob pattern.
     *
     * @param pattern The glob pattern (may contain *, ?, and escape sequences)
     * @param value The string value to match against
     * @return true if the value matches the pattern, false otherwise
     */
    fun matches(pattern: String, value: String): Boolean {
        val regex = globToRegex(pattern)
        return regex.matches(value)
    }

    /**
     * Convert a glob pattern to a regular expression.
     *
     * The conversion handles:
     * - Escaping regex metacharacters in literal parts
     * - Converting `*` to `.*` (zero or more of any character)
     * - Converting `?` to `.` (exactly one of any character)
     * - Processing backslash escape sequences (`\*`, `\?`, `\\`)
     *
     * @param pattern The glob pattern string
     * @return A Regex that matches the same strings as the glob pattern
     */
    private fun globToRegex(pattern: String): Regex {
        val regexPattern = buildString {
            append('^') // Match from start
            var i = 0
            while (i < pattern.length) {
                when (val char = pattern[i]) {
                    '\\' -> {
                        // Handle escape sequences
                        if (i + 1 < pattern.length) {
                            val nextChar = pattern[i + 1]
                            when (nextChar) {
                                '*', '?', '\\' -> {
                                    // Escaped special character - add as literal
                                    append(Regex.escape(nextChar.toString()))
                                    i += 2
                                }
                                else -> {
                                    // Invalid escape sequence - treat backslash as literal
                                    append(Regex.escape("\\"))
                                    i++
                                }
                            }
                        } else {
                            // Trailing backslash - treat as literal
                            append(Regex.escape("\\"))
                            i++
                        }
                    }
                    '*' -> {
                        // Wildcard: zero or more characters
                        append(".*")
                        i++
                    }
                    '?' -> {
                        // Wildcard: exactly one character
                        append(".")
                        i++
                    }
                    else -> {
                        // Literal character - escape regex metacharacters
                        append(Regex.escape(char.toString()))
                        i++
                    }
                }
            }
            append('$') // Match to end
        }
        return Regex(regexPattern)
    }
}

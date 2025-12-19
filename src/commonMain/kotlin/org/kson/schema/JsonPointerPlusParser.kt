package org.kson.schema

import org.kson.parser.messages.MessageType.*

/**
 * Parser for JsonPointerPlus - an extension of JSON Pointer with glob-style pattern matching.
 *
 * Extends RFC 6901 JSON Pointer with:
 * - Wildcard tokens: A token that is exactly `*` matches any single key or array index
 * - Glob patterns: A token containing `*` or `?` (but not only `*`) is treated as a pattern
 * - Backslash escaping: `\*`, `\?`, `\\` for literal characters in patterns
 * - RFC 6901 escaping: `~0` (tilde), `~1` (slash) still supported for compatibility
 *
 * Examples (where STAR represents the asterisk character):
 * ```
 * /users/STAR/email          - Wildcard: matches email of any user
 * /users/STARadminSTAR/role  - Pattern: matches users with "admin" in key name
 * /config/\STAR value        - Literal: matches key named "STARvalue"
 * /path/to\\from             - Literal: matches key named "to\from"
 * ```
 *
 * @param pointerString The JsonPointerPlus string to parse
 */
class JsonPointerPlusParser(pointerString: String) : PointerParser(pointerString) {

    companion object {
        // Character constants
        private const val WILDCARD_CHAR = '*'
        private const val SINGLE_CHAR_WILDCARD = '?'
    }

    /**
     * reference-token = *( unescaped / rfc-escaped / backslash-escaped )
     *
     * Collects characters for a single reference token, processes escape sequences,
     * and determines whether the token is a Literal, Wildcard, or GlobPattern.
     *
     * @return true if token was successfully parsed, false if an error occurred
     */
    override fun referenceToken(): Boolean {
        val rawTokenBuilder = StringBuilder() // Unescaped content
        var hasWildcard = false
        var hasSingleCharWildcard = false

        // Collect all characters until next '/' or EOF
        while (!scanner.eof() && scanner.peek() != PATH_SEPARATOR) {
            val char = scanner.peek()!!

            // Try RFC escape handling first
            when (val rfcResult = PointerEscapeHandler.handleRfcEscape(scanner)) {
                is PointerEscapeHandler.EscapeResult.Success -> {
                    rawTokenBuilder.append(rfcResult.char)
                    continue
                }
                is PointerEscapeHandler.EscapeResult.Failure -> {
                    if (rfcResult.error != null) {
                        error = rfcResult.error
                        return false
                    }
                    // Fall through to try backslash escape
                }
            }

            // Try backslash escape handling
            when (val backslashResult = PointerEscapeHandler.handleBackslashEscape(scanner)) {
                is PointerEscapeHandler.EscapeResult.Success -> {
                    rawTokenBuilder.append(backslashResult.char)
                    continue
                }
                is PointerEscapeHandler.EscapeResult.Failure -> {
                    if (backslashResult.error != null) {
                        error = backslashResult.error
                        return false
                    }
                    // Fall through to handle as regular character
                }
            }

            // Handle unescaped wildcards and regular characters
            when (char) {
                WILDCARD_CHAR -> {
                    hasWildcard = true
                    rawTokenBuilder.append(char)
                    scanner.advance()
                }
                SINGLE_CHAR_WILDCARD -> {
                    hasSingleCharWildcard = true
                    rawTokenBuilder.append(char)
                    scanner.advance()
                }
                else -> {
                    if (PointerEscapeHandler.isUnescaped(char)) {
                        rawTokenBuilder.append(char)
                        scanner.advance()
                    } else {
                        error = JSON_POINTER_INVALID_CHARACTER.create(char.toString())
                        return false
                    }
                }
            }
        }

        val rawToken = rawTokenBuilder.toString()

        // Determine token type:
        // 1. If token is exactly "*" -> Wildcard
        // 2. If token contains * or ? -> GlobPattern
        // 3. Otherwise -> Literal
        val parsedToken = when {
            rawToken == "*" -> Tokens.Wildcard
            hasWildcard || hasSingleCharWildcard -> Tokens.GlobPattern(rawToken)
            else -> Tokens.Literal(rawToken)
        }

        tokens.add(parsedToken)
        return true
    }

}

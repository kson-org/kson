package org.kson.schema

import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType.*
import org.kson.stdlibx.exceptions.ShouldNotHappenException

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
class JsonPointerPlusParser(private val pointerString: String) {
    private val scanner: Scanner = Scanner(pointerString)
    private var error: Message? = null
    private val tokens = mutableListOf<ParsedToken>()

    companion object {
        // Character constants
        private const val PATH_SEPARATOR = '/'
        private const val RFC_ESCAPE_CHAR = '~'  // RFC 6901 escape
        private const val BACKSLASH_ESCAPE = '\\' // New backslash escape
        private const val TILDE_ESCAPE = '0'  // ~0 represents '~'
        private const val SLASH_ESCAPE = '1'  // ~1 represents '/'
        private const val WILDCARD_CHAR = '*'
        private const val SINGLE_CHAR_WILDCARD = '?'
    }

    /**
     * Represents a parsed token with its type.
     */
    sealed class ParsedToken {
        /**
         * A literal token with exact value to match.
         * @property value The exact string value to match
         */
        data class Literal(val value: String) : ParsedToken()

        /**
         * A wildcard token that matches any single key or array index.
         * Represented by a token that is exactly "*".
         */
        data object Wildcard : ParsedToken()

        /**
         * A glob pattern token for matching keys.
         * @property pattern The glob pattern (may contain * and ?)
         */
        data class GlobPattern(val pattern: String) : ParsedToken()
    }

    /**
     * Result of parsing a JsonPointerPlus
     */
    sealed class ParseResult {
        /**
         * Successfully parsed JsonPointerPlus
         * @property tokens List of parsed tokens with their types
         */
        data class Success(val tokens: List<ParsedToken>) : ParseResult()

        /**
         * Failed to parse JsonPointerPlus
         * @property message Description of the parsing error
         */
        data class Error(val message: Message) : ParseResult()
    }

    /**
     * Parse the JsonPointerPlus string
     * @return ParseResult.Success with tokens if valid, ParseResult.Error with message if invalid
     */
    fun parse(): ParseResult {
        // Parse according to grammar: json-pointer-plus = *( "/" reference-token )
        if (!jsonPointerPlus()) {
            return ParseResult.Error(
                error
                    ?: throw RuntimeException("must always set `error` for a failed parse")
            )
        }

        // Check for unexpected trailing content
        if (!scanner.eof()) {
            val char = scanner.peek()
            // If we haven't consumed anything and found a non-slash character, it's an invalid start
            if (tokens.isEmpty() && char != PATH_SEPARATOR) {
                return ParseResult.Error(JSON_POINTER_BAD_START.create(char.toString()))
            }

            throw ShouldNotHappenException(
                "All unicode characters after the initial slash should be consumed by the parser"
            )
        }

        return ParseResult.Success(tokens.toList())
    }

    /**
     * json-pointer-plus = *( "/" reference-token )
     */
    private fun jsonPointerPlus(): Boolean {
        // Zero or more occurrences of "/" followed by reference-token
        while (scanner.peek() == PATH_SEPARATOR) {
            scanner.advance() // consume '/'

            if (!referenceToken()) {
                return false
            }
        }

        return true
    }

    /**
     * reference-token = *( unescaped / rfc-escaped / backslash-escaped )
     *
     * Collects characters for a single reference token, processes escape sequences,
     * and determines whether the token is a Literal, Wildcard, or GlobPattern.
     *
     * @return true if token was successfully parsed, false if an error occurred
     */
    private fun referenceToken(): Boolean {
        val rawTokenBuilder = StringBuilder() // Unescaped content
        var hasWildcard = false
        var hasSingleCharWildcard = false

        // Collect all characters until next '/' or EOF
        while (!scanner.eof() && scanner.peek() != PATH_SEPARATOR) {
            val char = scanner.peek()!!

            when (char) {
                RFC_ESCAPE_CHAR -> {
                    // Handle RFC 6901 escape sequence (~0, ~1)
                    val escapedChar = rfcEscaped() ?: return false
                    rawTokenBuilder.append(escapedChar)
                }
                BACKSLASH_ESCAPE -> {
                    // Handle backslash escape sequence (\*, \?, \\)
                    val escapedChar = backslashEscaped() ?: return false
                    rawTokenBuilder.append(escapedChar)
                }
                WILDCARD_CHAR -> {
                    // Unescaped wildcard - will determine type after collecting all chars
                    hasWildcard = true
                    rawTokenBuilder.append(char)
                    scanner.advance()
                }
                SINGLE_CHAR_WILDCARD -> {
                    // Unescaped single-char wildcard
                    hasSingleCharWildcard = true
                    rawTokenBuilder.append(char)
                    scanner.advance()
                }
                else -> {
                    if (isUnescaped(char)) {
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
            rawToken == "*" -> ParsedToken.Wildcard
            hasWildcard || hasSingleCharWildcard -> ParsedToken.GlobPattern(rawToken)
            else -> ParsedToken.Literal(rawToken)
        }

        tokens.add(parsedToken)
        return true
    }

    /**
     * Check if a character is allowed as an unescaped character in a reference token.
     * According to RFC 6901, all characters are valid except '/' and '~'.
     *
     * @param char The character to check
     * @return true if the character can appear unescaped, false otherwise
     */
    private fun isUnescaped(char: Char): Boolean {
        // All characters are valid except '/' (0x2F) and '~' (0x7E)
        val code = char.code
        return code != 0x2F && code != 0x7E
    }

    /**
     * RFC 6901 escaped sequence: "~" ( "0" / "1" )
     * - "~0" represents a literal '~'
     * - "~1" represents a literal '/'
     *
     * @return The unescaped character if valid, null if invalid escape sequence
     */
    private fun rfcEscaped(): Char? {
        if (scanner.peek() != RFC_ESCAPE_CHAR) {
            return null
        }

        // consume '~'
        scanner.advance()

        if (scanner.eof()) {
            error = JSON_POINTER_INCOMPLETE_ESCAPE.create()
            return null
        }

        return when (val nextChar = scanner.peek()) {
            TILDE_ESCAPE -> {
                scanner.advance()
                RFC_ESCAPE_CHAR  // ~0 represents '~'
            }
            SLASH_ESCAPE -> {
                scanner.advance()
                PATH_SEPARATOR  // ~1 represents '/'
            }
            else -> {
                error = JSON_POINTER_INVALID_ESCAPE.create(nextChar.toString())
                null
            }
        }
    }

    /**
     * Backslash escaped sequence: "\" ( "*" / "?" / "\" )
     * - "\*" represents a literal '*'
     * - "\?" represents a literal '?'
     * - "\\" represents a literal '\'
     *
     * @return The unescaped character if valid, null if invalid escape sequence
     */
    private fun backslashEscaped(): Char? {
        if (scanner.peek() != BACKSLASH_ESCAPE) {
            return null
        }

        // consume '\'
        scanner.advance()

        if (scanner.eof()) {
            error = JSON_POINTER_INCOMPLETE_ESCAPE.create()
            return null
        }

        return when (val nextChar = scanner.peek()) {
            WILDCARD_CHAR, SINGLE_CHAR_WILDCARD, BACKSLASH_ESCAPE -> {
                scanner.advance()
                nextChar  // \*, \?, \\ represent literal *, ?, \
            }
            else -> {
                error = JSON_POINTER_INVALID_ESCAPE.create(nextChar.toString())
                null
            }
        }
    }

    /**
     * Scanner for character-by-character processing of the pointer string
     */
    private class Scanner(private val source: String) {
        var currentIndex = 0
            private set

        /**
         * Get the current character without advancing
         * @return Current character or null if at end
         */
        fun peek(): Char? {
            return if (eof()) null else source[currentIndex]
        }

        /**
         * Advance to the next character
         */
        fun advance() {
            if (!eof()) {
                currentIndex++
            }
        }

        /**
         * Check if at end of string.
         *
         * @return true if no more characters to read, false otherwise
         */
        fun eof(): Boolean {
            return currentIndex >= source.length
        }
    }
}

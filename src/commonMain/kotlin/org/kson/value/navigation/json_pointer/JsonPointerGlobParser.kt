package org.kson.value.navigation.json_pointer

import org.kson.parser.messages.MessageType.*

/**
 * Parser for JsonPointerGlob - an extension of JSON Pointer with glob-style pattern matching.
 *
 * Extends RFC 6901 JSON Pointer with:
 * - Wildcard tokens: A token that is exactly `*` matches any single key or array index
 * - Recursive descent: A token that is exactly `**` matches zero or more levels
 * - Glob patterns: A token containing `*` or `?` (but not only `*` or `**`) is treated as a pattern
 * - Backslash escaping: `\*`, `\?`, `\\` for literal characters in patterns
 * - RFC 6901 escaping: `~0` (tilde), `~1` (slash) still supported for compatibility
 *
 * Examples (where * represents the asterisk character):
 * ```
 * /users/*/email              - Wildcard: matches email of any user
 * /users/\*\*/email          - Recursive descent: matches all emails at any depth under users
 * /**/email                - Recursive descent: matches all emails anywhere in document
 * /users/*admin*/role      - Pattern: matches users with "admin" in key name
 * /config/\* value            - Literal: matches key named "*value"
 * /path/to\\from                 - Literal: matches key named "to\from"
 * ```
 *
 * @param pointerString The JsonPointerGlob string to parse
 */
class JsonPointerGlobParser(pointerString: String) : PointerParser(pointerString) {

    /**
     * Represents a parsed character with its semantic meaning.
     * Separates parsing from classification by capturing whether each character
     * is a literal or a glob wildcard.
     */
    private sealed class ParsedChar {
        /** A literal character (either plain or from an escape sequence) */
        data class Literal(val char: Char, val escaped: Boolean = false) : ParsedChar()
        /** An unescaped `*` wildcard */
        data object Wildcard : ParsedChar() { const val CHAR = '*' }
        /** An unescaped `?` single-character wildcard */
        data object SingleCharWildcard : ParsedChar() { const val CHAR = '?' }
    }

    override fun referenceToken(): Boolean {
        val chars = parseChars() ?: return false
        tokens.add(classifyToken(chars))
        return true
    }

    /**
     * Phase 1: Parse all characters in the token, handling escape sequences.
     * @return list of parsed characters, or null if an error occurred
     */
    private fun parseChars(): List<ParsedChar>? {
        val chars = mutableListOf<ParsedChar>()

        while (!scanner.eof() && scanner.peek() != PATH_SEPARATOR) {
            val parsed = parseNextChar() ?: return null
            chars.add(parsed)
        }

        return chars
    }

    /**
     * Parse the next character from the scanner.
     * @return the parsed character, or null if an error occurred
     */
    private fun parseNextChar(): ParsedChar? {
        // Try RFC escape first (~0, ~1)
        when (val rfcResult = PointerEscapeHandler.handleRfcEscape(scanner)) {
            is PointerEscapeHandler.EscapeResult.Success ->
                return ParsedChar.Literal(rfcResult.char)
            is PointerEscapeHandler.EscapeResult.Failure -> {
                if (rfcResult.error != null) {
                    error = rfcResult.error
                    return null
                }
            }
        }

        // Try backslash escape (\*, \?, \\)
        when (val backslashResult = PointerEscapeHandler.handleBackslashEscape(scanner)) {
            is PointerEscapeHandler.EscapeResult.Success ->
                return ParsedChar.Literal(backslashResult.char, escaped = true)
            is PointerEscapeHandler.EscapeResult.Failure -> {
                if (backslashResult.error != null) {
                    error = backslashResult.error
                    return null
                }
            }
        }

        // Handle unescaped characters
        val char = scanner.peek()!!
        scanner.advance()

        return when (char) {
            ParsedChar.Wildcard.CHAR -> ParsedChar.Wildcard
            ParsedChar.SingleCharWildcard.CHAR -> ParsedChar.SingleCharWildcard
            else -> if (PointerEscapeHandler.isUnescaped(char)) {
                ParsedChar.Literal(char)
            } else {
                error = JSON_POINTER_INVALID_CHARACTER.create(char.toString())
                null
            }
        }
    }

    /**
     * Phase 2: Classify the token based on its parsed characters.
     */
    private fun classifyToken(chars: List<ParsedChar>): Tokens {
        val hasGlobChars = chars.any { it is ParsedChar.Wildcard || it is ParsedChar.SingleCharWildcard }

        return when {
            chars == listOf(ParsedChar.Wildcard, ParsedChar.Wildcard) -> Tokens.RecursiveDescent
            chars == listOf(ParsedChar.Wildcard) -> Tokens.Wildcard
            hasGlobChars -> Tokens.GlobPattern(buildPatternString(chars))
            else -> Tokens.Literal(buildLiteralString(chars))
        }
    }

    /**
     * Build the literal string value from parsed characters.
     */
    private fun buildLiteralString(chars: List<ParsedChar>): String =
        chars.joinToString("") { (it as ParsedChar.Literal).char.toString() }

    /**
     * Build the pattern string for GlobMatcher, preserving escape sequences
     * so the matcher can distinguish literal characters from wildcards.
     */
    private fun buildPatternString(chars: List<ParsedChar>): String = buildString {
        for (char in chars) {
            when (char) {
                is ParsedChar.Literal -> {
                    if (char.escaped) {
                        append('\\')
                    }
                    append(char.char)
                }
                is ParsedChar.Wildcard -> append(ParsedChar.Wildcard.CHAR)
                is ParsedChar.SingleCharWildcard -> append(ParsedChar.SingleCharWildcard.CHAR)
            }
        }
    }
}

package org.kson.value.navigation.json_pointer

import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType.*

/**
 * Handles escape sequence processing for JSON Pointer parsers.
 * Supports both RFC 6901 escape sequences and backslash escapes used in JsonPointerGlob.
 */
internal object PointerEscapeHandler {
    // Character constants
    private const val PATH_SEPARATOR = '/'
    private const val RFC_ESCAPE_CHAR = '~'
    private const val BACKSLASH_ESCAPE = '\\'
    private const val TILDE_ESCAPE = '0'  // ~0 represents '~'
    private const val SLASH_ESCAPE = '1'  // ~1 represents '/'
    private const val WILDCARD_CHAR = '*'
    private const val SINGLE_CHAR_WILDCARD = '?'

    /**
     * Check if a character is allowed as an unescaped character in a reference token.
     * According to RFC 6901, all characters are valid except '/' and '~'.
     *
     * @param char The character to check
     * @return true if the character can appear unescaped, false otherwise
     */
    fun isUnescaped(char: Char): Boolean {
        // All characters are valid except '/' (0x2F) and '~' (0x7E)
        val code = char.code
        return code != 0x2F && code != 0x7E
    }

    /**
     * RFC 6901 escaped sequence: "~" ( "0" / "1" )
     * - "~0" represents a literal '~'
     * - "~1" represents a literal '/'
     *
     * @param scanner The scanner positioned at the '~' character
     * @return EscapeResult with the unescaped character if valid, or error message if invalid
     */
    fun handleRfcEscape(scanner: PointerScanner): EscapeResult {
        if (scanner.peek() != RFC_ESCAPE_CHAR) {
            return EscapeResult.Failure(null) // Not an RFC escape
        }

        // consume '~'
        scanner.advance()

        if (scanner.eof()) {
            return EscapeResult.Failure(JSON_POINTER_INCOMPLETE_ESCAPE.create())
        }

        return when (val nextChar = scanner.peek()) {
            TILDE_ESCAPE -> {
                scanner.advance()
                EscapeResult.Success(RFC_ESCAPE_CHAR)  // ~0 represents '~'
            }
            SLASH_ESCAPE -> {
                scanner.advance()
                EscapeResult.Success(PATH_SEPARATOR)  // ~1 represents '/'
            }
            else -> {
                EscapeResult.Failure(JSON_POINTER_INVALID_ESCAPE.create(nextChar.toString()))
            }
        }
    }

    /**
     * Backslash escaped sequence: "\" ( "*" / "?" / "\" )
     * - "\*" represents a literal '*'
     * - "\?" represents a literal '?'
     * - "\\" represents a literal '\'
     *
     * @param scanner The scanner positioned at the '\' character
     * @return EscapeResult with the unescaped character if valid, or error message if invalid
     */
    fun handleBackslashEscape(scanner: PointerScanner): EscapeResult {
        if (scanner.peek() != BACKSLASH_ESCAPE) {
            return EscapeResult.Failure(null) // Not a backslash escape
        }

        // consume '\'
        scanner.advance()

        if (scanner.eof()) {
            return EscapeResult.Failure(JSON_POINTER_INCOMPLETE_ESCAPE.create())
        }

        return when (val nextChar = scanner.peek()) {
            WILDCARD_CHAR, SINGLE_CHAR_WILDCARD, BACKSLASH_ESCAPE -> {
                scanner.advance()
                EscapeResult.Success(nextChar)  // \*, \?, \\ represent literal *, ?, \
            }
            else -> {
                EscapeResult.Failure(JSON_POINTER_INVALID_ESCAPE.create(nextChar.toString()))
            }
        }
    }

    /**
     * Result of processing an escape sequence
     */
    sealed class EscapeResult {
        /**
         * Successfully processed escape sequence
         * @property char The unescaped character
         */
        data class Success(val char: Char) : EscapeResult()

        /**
         * Failed to process escape sequence
         * @property error The error message, or null if this wasn't an escape sequence
         */
        data class Failure(val error: Message?) : EscapeResult()
    }
}

package org.kson.schema

import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType.*
import org.kson.stdlibx.exceptions.ShouldNotHappenException

/**
 * Parser for JSON Pointers according to RFC 6901.
 *
 * JSON Pointers provide a string syntax for identifying specific values within a JSON document.
 * A JSON Pointer is a Unicode string containing zero or more reference tokens, each prefixed by '/'.
 *
 * The grammar from RFC 6901 Section 3:
 * ```
 * json-pointer    -> *( "/" reference-token )
 * reference-token -> *( unescaped | escaped )
 * unescaped       -> %x00-2E | %x30-7D | %x7F-10FFFF
 *                     ; %x2F ('/') and %x7E ('~') are excluded from 'unescaped'
 * escaped         -> "~" ( "0" | "1" )
 *                     ; representing '~' and '/', respectively
 * ```
 *
 * Examples:
 * - "" - references the entire document
 * - "/foo" - references the "foo" member of the root object
 * - "/foo/0" - references the first element of the array at "foo"
 * - "/a~1b" - references a member with name "a/b" (escaped slash)
 * - "/m~0n" - references a member with name "m~n" (escaped tilde)
 *
 * @param pointerString The JSON Pointer string to parse
 */
class JsonPointerParser(private val pointerString: String) {
    private val scanner: Scanner = Scanner(pointerString)
    private var error: Message? = null
    private val tokens = mutableListOf<String>()

    companion object {
        // Character constants from RFC 6901
        private const val PATH_SEPARATOR = '/'
        private const val ESCAPE_CHAR = '~'
        private const val TILDE_ESCAPE = '0'  // ~0 represents '~'
        private const val SLASH_ESCAPE = '1'  // ~1 represents '/'
    }

    /**
     * Result of parsing a JSON Pointer
     */
    sealed class ParseResult {
        /**
         * Successfully parsed JSON Pointer
         * @property tokens List of reference tokens after unescaping
         */
        data class Success(val tokens: List<String>) : ParseResult()

        /**
         * Failed to parse JSON Pointer
         * @property message Description of the parsing error
         */
        data class Error(val message: Message) : ParseResult()
    }

    /**
     * Parse the JSON Pointer string
     * @return ParseResult.Success with tokens if valid, ParseResult.Error with message if invalid
     */
    fun parse(): ParseResult {
        // Parse according to grammar: json-pointer = *( "/" reference-token )
        if (!jsonPointer()) {
            return ParseResult.Error(
                error
                    ?: throw RuntimeException("must always set `errorMessage` for a failed parse")
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
                "All unicode characters after the initial slash are allowed an consumed by the parser"
            )
        }

        return ParseResult.Success(tokens.toList())
    }

    /**
     * json-pointer = *( "/" reference-token )
     */
    private fun jsonPointer(): Boolean {
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
     * reference-token = *( unescaped / escaped )
     *
     * Collects characters for a single reference token and adds the unescaped version to tokens list.
     * A reference token consists of any combination of unescaped characters and escape sequences.
     * Empty tokens are valid (e.g., "//" contains two empty tokens).
     *
     * @return true if token was successfully parsed, false if an error occurred
     */
    private fun referenceToken(): Boolean {
        val tokenBuilder = StringBuilder()

        // Collect all characters until next '/' or EOF
        while (!scanner.eof() && scanner.peek() != PATH_SEPARATOR) {
            val char = scanner.peek()!!

            if (char == ESCAPE_CHAR) {
                // Handle escaped sequence
                val escapedChar = escaped() ?: return false
                tokenBuilder.append(escapedChar)
            } else if (isUnescaped(char)) {
                tokenBuilder.append(char)
                scanner.advance()
            } else {
                error = JSON_POINTER_INVALID_CHARACTER.create(char.toString())
                return false
            }
        }

        tokens.add(tokenBuilder.toString())
        return true
    }

    /**
     * unescaped = %x00-2E / %x30-7D / %x7F-10FFFF
     * ; %x2F ('/') and %x7E ('~') are excluded from 'unescaped'
     *
     * Checks if a character is allowed as an unescaped character in a reference token.
     * The two special characters that must be escaped are:
     * - '/' (PATH_SEPARATOR) because it delimits tokens
     * - '~' (ESCAPE_CHAR) because it starts escape sequences
     * See [escaped]
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
     * escaped = "~" ( "0" / "1" )
     * ; representing '~' and '/', respectively
     *
     * Processes an escape sequence starting with '~'.
     * Valid escape sequences are:
     * - "~0" which represents a literal '~'
     * - "~1" which represents a literal '/'
     *
     * Any other character following '~' is an error according to RFC 6901.
     *
     * @return The unescaped character if valid, null if invalid escape sequence
     */
    private fun escaped(): Char? {
        if (scanner.peek() != ESCAPE_CHAR) {
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
                ESCAPE_CHAR  // ~0 represents '~'
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

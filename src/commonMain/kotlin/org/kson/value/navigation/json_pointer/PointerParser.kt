package org.kson.value.navigation.json_pointer

import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType
import org.kson.stdlibx.exceptions.ShouldNotHappenException

/**
 * Abstract base class for JSON Pointer parsers.
 *
 * Provides common parsing infrastructure for JSON Pointer variants, including:
 * - Scanner management
 * - Main parsing loop structure
 * - Error handling
 * - Path separator handling
 *
 * Subclasses need only implement the token parsing logic via [referenceToken].
 *
 * @param pointerString The pointer string to parse
 */
abstract class PointerParser(internal val pointerString: String) {
    internal val scanner = PointerScanner(pointerString)
    internal var error: Message? = null
    internal val tokens = mutableListOf<Tokens>()

    companion object {
        protected const val PATH_SEPARATOR = '/'
    }

    /**
     * Represents a parsed token with its type.
     * Shared by both JsonPointer (only Literal) and JsonPointerGlob (all types).
     */
    sealed class Tokens {
        /**
         * A literal token with exact value to match.
         * @property value The exact string value to match
         */
        data class Literal(val value: String) : Tokens()

        /**
         * A wildcard token that matches any single key or array index.
         * Represented by a token that is exactly "*".
         */
        data object Wildcard : Tokens()

        /**
         * A recursive descent token that matches zero or more levels.
         * Represented by a token that is exactly "**".
         * Performs depth-first traversal to find matches at any depth.
         */
        data object RecursiveDescent : Tokens()

        /**
         * A glob pattern token for matching keys.
         * @property pattern The glob pattern (may contain * and ?)
         */
        data class GlobPattern(val pattern: String) : Tokens()
    }

    /**
     * Result of parsing a pointer string
     */
    sealed class ParseResult {
        /**
         * Successfully parsed pointer
         * @property tokens List of parsed tokens
         */
        data class Success(val tokens: List<Tokens>) : ParseResult()

        /**
         * Failed to parse pointer
         * @property message Description of the parsing error
         */
        data class Error(val message: Message) : ParseResult()
    }

    /**
     * Parse the pointer string
     * @return ParseResult.Success with tokens if valid, ParseResult.Error with message if invalid
     */
    fun parse(): ParseResult {
        // Parse according to grammar: json-pointer = *( "/" reference-token )
        if (!jsonPointer()) {
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
                return ParseResult.Error(MessageType.JSON_POINTER_BAD_START.create(char.toString()))
            }

            throw ShouldNotHappenException(
                "All unicode characters after the initial slash should be consumed by the parser"
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
     * Parse a single reference token.
     *
     * Implementations should:
     * - Collect characters until the next '/' or EOF
     * - Handle escape sequences appropriately
     * - Add the parsed token to the [tokens] list
     * - Set [error] and return false on parsing failure
     *
     * @return true if token was successfully parsed, false if an error occurred
     */
    protected abstract fun referenceToken(): Boolean
}
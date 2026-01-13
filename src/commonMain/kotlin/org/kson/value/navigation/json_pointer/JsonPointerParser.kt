package org.kson.value.navigation.json_pointer

import org.kson.parser.messages.MessageType.*

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
class JsonPointerParser(pointerString: String) : PointerParser(pointerString) {

    /**
     * reference-token = *( unescaped / escaped )
     *
     * Collects characters for a single reference token and adds the unescaped version to tokens list.
     * A reference token consists of any combination of unescaped characters and escape sequences.
     * Empty tokens are valid (e.g., "//" contains two empty tokens).
     *
     * @return true if token was successfully parsed, false if an error occurred
     */
    override fun referenceToken(): Boolean {
        val tokenBuilder = StringBuilder()

        // Collect all characters until next '/' or EOF
        while (!scanner.eof() && scanner.peek() != PATH_SEPARATOR) {
            val char = scanner.peek()!!

            // Try RFC escape handling
            when (val escapeResult = PointerEscapeHandler.handleRfcEscape(scanner)) {
                is PointerEscapeHandler.EscapeResult.Success -> {
                    tokenBuilder.append(escapeResult.char)
                }
                is PointerEscapeHandler.EscapeResult.Failure -> {
                    if (escapeResult.error != null) {
                        error = escapeResult.error
                        return false
                    }
                    // Not an escape sequence, treat as regular character
                    if (PointerEscapeHandler.isUnescaped(char)) {
                        tokenBuilder.append(char)
                        scanner.advance()
                    } else {
                        error = JSON_POINTER_INVALID_CHARACTER.create(char.toString())
                        return false
                    }
                }
            }
        }

        tokens.add(Tokens.Literal(tokenBuilder.toString()))
        return true
    }
}

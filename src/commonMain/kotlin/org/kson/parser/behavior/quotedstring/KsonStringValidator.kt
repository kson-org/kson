package org.kson.parser.behavior.quotedstring

import org.kson.ast.EmbedBlockNode
import org.kson.ast.FalseNode
import org.kson.ast.KsonRoot
import org.kson.ast.KsonRootImpl
import org.kson.ast.KsonValueNode
import org.kson.ast.KsonValueNodeError
import org.kson.ast.ListElementNodeImpl
import org.kson.ast.ListNode
import org.kson.ast.NullNode
import org.kson.ast.NumberNode
import org.kson.ast.ObjectKeyNodeImpl
import org.kson.ast.ObjectNode
import org.kson.ast.ObjectPropertyNodeImpl
import org.kson.ast.QuotedStringNode
import org.kson.ast.TrueNode
import org.kson.ast.UnquotedStringNode
import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.stdlibx.exceptions.FatalParseException

/**
 * Validates that all strings for a given [KsonRoot] contain only valid KSON strings, i.e. strings which exactly follow
 * the JSON String rules specified in [Section 7. Strings of RFC8259](https://datatracker.ietf.org/doc/html/rfc8259#section-7),
 * with a KSON-specific allowance for raw whitespace characters
 *
 * [KsonStringValidator] walks the AST rooted to find [org.kson.ast.QuotedStringNode] instances and validates their
 * raw string content for:
 * - Invalid escape sequences (`\x` where x is not a valid escape character)
 * - Invalid unicode escapes (`\uXXXX` where XXXX is not 4 valid hex digits)
 * - Illegal control characters (0x00-0x1F except whitespace: `\n`, `\r`, `\t`)
 */
class KsonStringValidator {
    fun validate(ast: KsonRoot, messageSink: MessageSink) {
        if (ast is KsonRootImpl) {
            validateNode(ast.rootNode, messageSink)
        }
    }

    private fun validateNode(node: KsonValueNode, messageSink: MessageSink) {
        when (node) {
            is ObjectNode -> {
                node.properties.forEach { property ->
                    if (property is ObjectPropertyNodeImpl) {
                        // Validate the key if it's a quoted string
                        if (property.key is ObjectKeyNodeImpl) {
                            val keyNode = property.key.key
                            if (keyNode is QuotedStringNode) {
                                validateQuotedString(keyNode, messageSink)
                            }
                        }
                        // Recursively validate the value
                        validateNode(property.value, messageSink)
                    }
                }
            }
            is ListNode -> {
                node.elements.forEach { element ->
                    if (element is ListElementNodeImpl) {
                        validateNode(element.value, messageSink)
                    }
                }
            }
            is QuotedStringNode -> {
                validateQuotedString(node, messageSink)
            }
            // No validation needed for other node types
            is EmbedBlockNode, is UnquotedStringNode,
            is NumberNode, is TrueNode, is FalseNode, is NullNode,
            is KsonValueNodeError -> {
                // No string validation needed
            }
        }
    }

    private fun validateQuotedString(node: QuotedStringNode, messageSink: MessageSink) {
        val rawContent = node.rawStringContent
        validateRawStringContent(rawContent, node.location, messageSink)
    }

    /**
     * Validates the raw string content for invalid escapes, invalid unicode escapes, and illegal control characters.
     *
     * @param rawContent The raw string content (excluding quotes)
     * @param baseLocation The location of the STRING_CONTENT token in the source
     * @param messageSink The sink for reporting validation errors
     */
    private fun validateRawStringContent(rawContent: String, baseLocation: Location, messageSink: MessageSink) {
        val scanner = StringContentScanner(rawContent, baseLocation)

        while (!scanner.eof()) {
            val char = scanner.peek()

            // Check for illegal control characters (0x00-0x1F except whitespace)
            if (char.code in 0x00..0x1F && !isWhitespace(char)) {
                messageSink.error(scanner.currentLocation(), MessageType.STRING_CONTROL_CHARACTER.create(char.toString()))
                scanner.advance()
                continue
            }

            // Check for escape sequences
            if (char == '\\') {
                val escapeStartLocation = scanner.currentLocation()
                scanner.advance() // consume backslash

                if (scanner.eof()) {
                    // Incomplete escape at end of string
                    messageSink.error(escapeStartLocation, MessageType.STRING_BAD_ESCAPE.create("\\"))
                    break
                }

                val nextChar = scanner.peek()

                if (nextChar == 'u') {
                    // Unicode escape - must be \uXXXX where X is hex digit
                    // Collect up to 5 more chars (\u + 4 hex digits)
                    val escapeBuilder = StringBuilder("\\")
                    var charsConsumed = 0
                    while (!scanner.eof() && charsConsumed < 5 && !isWhitespace(scanner.peek())) {
                        escapeBuilder.append(scanner.peek())
                        scanner.advance()
                        charsConsumed++
                    }

                    val escapeText = escapeBuilder.toString()
                    if (!isValidUnicodeEscape(escapeText)) {
                        val errorLocation = Location.create(
                            escapeStartLocation.start.line, escapeStartLocation.start.column,
                            escapeStartLocation.start.line, escapeStartLocation.start.column + escapeText.length,
                            escapeStartLocation.startOffset, escapeStartLocation.startOffset + escapeText.length
                        )
                        messageSink.error(errorLocation, MessageType.STRING_BAD_UNICODE_ESCAPE.create(escapeText))
                    }
                } else {
                    // Regular escape - check if it's valid
                    if (!isValidStringEscape(nextChar)) {
                        val escapeText = "\\$nextChar"
                        val errorLocation = Location.create(
                            escapeStartLocation.start.line, escapeStartLocation.start.column,
                            escapeStartLocation.start.line, escapeStartLocation.start.column + 2,
                            escapeStartLocation.startOffset, escapeStartLocation.startOffset + 2
                        )
                        messageSink.error(errorLocation, MessageType.STRING_BAD_ESCAPE.create(escapeText))
                    }
                    scanner.advance() // consume the escaped character
                }
            } else {
                // Regular character - just advance
                scanner.advance()
            }
        }
    }

    private fun isWhitespace(char: Char?): Boolean {
        return char == ' ' || char == '\n' || char == '\r' || char == '\t'
    }

    private fun isValidStringEscape(escapedChar: Char): Boolean {
        return escapedChar in validStringEscapes
    }

    private fun isValidUnicodeEscape(unicodeEscapeText: String): Boolean {
        if (!unicodeEscapeText.startsWith("\\u")) {
            throw FatalParseException("Should only be asked to validate unicode escapes")
        }

        // clip off the `\u` to make this code point easier to inspect
        val unicodeCodePoint = unicodeEscapeText.replaceFirst("\\u", "")

        if (unicodeCodePoint.length != 4) {
            // must have four chars
            return false
        }

        for (codePointChar in unicodeCodePoint) {
            if (!validHexChars.contains(codePointChar)) {
                return false
            }
        }

        return true
    }

    /**
     * Scanner for character-by-character processing with position tracking.
     * Models after NumberParser.Scanner and JsonPointerParser.Scanner, but adds
     * line/column/offset tracking for error location reporting.
     */
    private class StringContentScanner(
        private val source: String,
        baseLocation: Location
    ) {
        private var currentIndex = 0
        private var currentLine = baseLocation.start.line
        private var currentColumn = baseLocation.start.column
        private var currentOffset = baseLocation.startOffset

        fun peek(): Char = if (!eof()) {
            source[currentIndex]
        } else {
            throw RuntimeException("`eof()` should be checked before calling this")
        }

        fun advance() {
            if (!eof()) {
                val char = source[currentIndex++]
                if (char == '\n') {
                    currentLine++
                    currentColumn = 0
                } else {
                    currentColumn++
                }
                currentOffset++
            }
        }

        fun eof(): Boolean = currentIndex >= source.length

        fun currentLocation(length: Int = 1): Location = Location.create(
            currentLine, currentColumn,
            currentLine, currentColumn + length,
            currentOffset, currentOffset + length
        )
    }
}

/**
 * Enumerate the set of valid Kson string escapes for easy validation `\u` is also supported,
 * but is validated separately against [validHexChars]
 */
private val validStringEscapes = setOf('\'', '"', '\\', '/', 'b', 'f', 'n', 'r', 't')
private val validHexChars = setOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F'
)

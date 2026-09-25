package org.kson.parser

import org.kson.ast.renderForJsonString
import org.kson.ast.unescapeForwardSlashes
import org.kson.parser.behavior.StringQuote.DoubleQuote
import org.kson.parser.behavior.StringQuote.SingleQuote
import org.kson.parser.behavior.quotedstring.unescapeStringContent
import org.kson.testSupport.validateJson
import kotlin.test.Test
import kotlin.test.assertEquals

class EscapingTest {

    /**
     * Assert the given [jsonStringContent] is escaped to [expectedEscapedString] by [renderForJsonString]
     */
    private fun assertJsonStringEscaping(jsonStringContent: String, expectedEscapedString: String) {
        try {
            validateJson("\"$expectedEscapedString\"")
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "ERROR: The expected JSON in this test is invalid. Please fix the test's expectations.\n" +
                        "JSON parsing error:\n${e.message}", e
            )
        }

        assertEquals(expectedEscapedString, renderForJsonString(jsonStringContent))
    }

    @Test
    fun testJsonRequiredEscapes() {
        // Quotation mark and reverse solidus - must be escaped
        assertJsonStringEscaping("\"", "\\\"")
        assertJsonStringEscaping("\\", "\\\\")

        // Control characters (testing boundaries and samples)
        assertJsonStringEscaping("\u0000", "\\u0000")
        assertJsonStringEscaping("\u0001", "\\u0001")
        assertJsonStringEscaping("\u001F", "\\u001F")
        
        // Special escape sequences for common control characters
        assertJsonStringEscaping("\b", "\\b")     // U+0008
        assertJsonStringEscaping("\u000C", "\\f") // U+000C
        assertJsonStringEscaping("\n", "\\n")     // U+000A
        assertJsonStringEscaping("\r", "\\r")     // U+000D
        assertJsonStringEscaping("\t", "\\t")     // U+0009
    }

    @Test
    fun testJsonOptionalEscapes() {
        // Forward slash can be escaped in JSON but isn't required to be, we prefer the cleaner output
        assertJsonStringEscaping("/", "/")
    }

    @Test
    fun testJsonNoEscapingNeeded() {
        // Regular ASCII characters
        assertJsonStringEscaping("Hello World", "Hello World")
        assertJsonStringEscaping("123", "123")
        
        // Empty string
        assertJsonStringEscaping("", "")
        
        // Unicode characters from Basic Multilingual Plane (BMP)
        // that aren't control characters should not be escaped
        assertJsonStringEscaping("Hello λ World", "Hello λ World")
        assertJsonStringEscaping("こんにちは", "こんにちは")
    }

    @Test
    fun testJsonUnicodeEscaping() {
        // Control characters must be escaped with \u00XX
        assertJsonStringEscaping("\u0001", "\\u0001")
        assertJsonStringEscaping("\u001F", "\\u001F")
        
        // Special whitespace that must be escaped
        assertJsonStringEscaping("\u2028", "\\u2028") // Line separator
        assertJsonStringEscaping("\u2029", "\\u2029") // Paragraph separator
        
        // Characters outside BMP must be escaped as surrogate pairs
        assertJsonStringEscaping("𝄞", "\\uD834\\uDD1E") // Musical G-clef (U+1D11E)
    }

    @Test
    fun testJsonCombinedEscapes() {
        // Mix of required escapes
        assertJsonStringEscaping("Hello\nWorld\t!", "Hello\\nWorld\\t!")
        assertJsonStringEscaping("\"Quote\" and \\Backslash\\", "\\\"Quote\\\" and \\\\Backslash\\\\")
        
        // Mix of required escapes with Unicode
        assertJsonStringEscaping("\"Hello\n世界\t!", "\\\"Hello\\n世界\\t!")
    }

    @Test
    fun testJsonSequentialEscapes() {
        // Multiple sequential special characters
        assertJsonStringEscaping("\n\r\t", "\\n\\r\\t")
        assertJsonStringEscaping("\"\"", "\\\"\\\"")
        
        // Multiple backslashes (each backslash must escape the next one if present)
        assertJsonStringEscaping("\\\"", "\\\\\\\"") // Input: \", Output: \"
        assertJsonStringEscaping("\\\\", "\\\\\\\\") // Input: \\, Output: \\
        assertJsonStringEscaping("\\\\\"", "\\\\\\\\\\\"") // Input: \\", Output: \\"
    }

    @Test
    fun testJsonLongStringWithEscapes() {
        // Test with a variety of characters that need different handling
        val input = "Hello\\\"🌍\\n\\t" // Contains literal backslash, quote, emoji, and control chars
        val expected = "Hello\\\\\\\"\\uD83C\\uDF0D\\\\n\\\\t"
        val longString = input.repeat(1000)
        val longExpected = expected.repeat(1000)
        assertJsonStringEscaping(longString, longExpected)
    }

    // Tests for unescapeJsonString

    @Test
    fun testUnescapeBasicEscapes() {
        assertEquals("\"", unescapeStringContent("\\\"", DoubleQuote))
        assertEquals("\\", unescapeStringContent("\\\\", DoubleQuote))
        assertEquals("/", unescapeStringContent("\\/", DoubleQuote))
        assertEquals("\b", unescapeStringContent("\\b", DoubleQuote))
        assertEquals("\u000C", unescapeStringContent("\\f", DoubleQuote))
        assertEquals("\n", unescapeStringContent("\\n", DoubleQuote))
        assertEquals("\r", unescapeStringContent("\\r", DoubleQuote))
        assertEquals("\t", unescapeStringContent("\\t", DoubleQuote))
    }

    @Test
    fun testUnescapeUnicodeEscapes() {
        assertEquals("A", unescapeStringContent("\\u0041", DoubleQuote))
        assertEquals("€", unescapeStringContent("\\u20AC", DoubleQuote))
        assertEquals("\u2028", unescapeStringContent("\\u2028", DoubleQuote))
        assertEquals("\u2029", unescapeStringContent("\\u2029", DoubleQuote))
        assertEquals("\u0000", unescapeStringContent("\\u0000", DoubleQuote))
        assertEquals("\u001F", unescapeStringContent("\\u001F", DoubleQuote))
    }

    @Test
    fun testUnescapeSurrogatePairs() {
        // Musical G-clef (U+1D11E)
        assertEquals("𝄞", unescapeStringContent("\\uD834\\uDD1E", DoubleQuote))
        // Emoji: 🌍 (U+1F30D)
        assertEquals("🌍", unescapeStringContent("\\uD83C\\uDF0D", DoubleQuote))
        // Mathematical bold capital A (U+1D400)
        assertEquals("𝐀", unescapeStringContent("\\uD835\\uDC00", DoubleQuote))
    }

    @Test
    fun testUnescapeMixedContent() {
        assertEquals("Hello \"World\"!", unescapeStringContent("Hello \\\"World\\\"!", DoubleQuote))
        assertEquals("Line 1\nLine 2\tTabbed", unescapeStringContent("Line 1\\nLine 2\\tTabbed", DoubleQuote))
        assertEquals("Path: C:\\Users\\John", unescapeStringContent("Path: C:\\\\Users\\\\John", DoubleQuote))
        assertEquals("\"Hello\n世界\t!", unescapeStringContent("\\\"Hello\\n世界\\t!", DoubleQuote))
    }

    @Test
    fun testUnescapeNoEscapes() {
        assertEquals("Hello World", unescapeStringContent("Hello World", DoubleQuote))
        assertEquals("123.456", unescapeStringContent("123.456", DoubleQuote))
        assertEquals("こんにちは", unescapeStringContent("こんにちは", DoubleQuote))
    }

    @Test
    fun testUnescapeInvalidEscapes() {
        // Invalid escape sequences should be preserved
        assertEquals("\\x", unescapeStringContent("\\x", DoubleQuote))
        assertEquals("\\", unescapeStringContent("\\", DoubleQuote))

        // Invalid unicode (not enough digits)
        assertEquals("\\u12", unescapeStringContent("\\u12", DoubleQuote))
        assertEquals("\\u", unescapeStringContent("\\u", DoubleQuote))
        assertEquals("\\uXYZ", unescapeStringContent("\\uXYZ", DoubleQuote))
    }

    @Test
    fun testUnescapeQuoteEscapes() {
        // Only an escape of the delimiting quote is processed; an escape of the other quote is invalid
        // and left untouched
        assertEquals("'\\\"", unescapeStringContent("\\'\\\"", SingleQuote))
        assertEquals("\\'\"", unescapeStringContent("\\'\\\"", DoubleQuote))

        // Content not delimited by quotes may not escape either quote
        assertEquals("\\'\\\"", unescapeStringContent("\\'\\\"", null))
    }

    @Test
    fun testEscapeQuotes() {
        assertEquals("it\\'s \"q\"", SingleQuote.escapeQuotes("it's \"q\""))
        assertEquals("it's \\\"q\\\"", DoubleQuote.escapeQuotes("it's \"q\""))

        // escaped backslashes are preserved when escaping quotes
        assertEquals("\\\\\\\"", DoubleQuote.escapeQuotes("\\\\\""))

        // Existing escapes are not re-escaped
        assertEquals("\\\"q\\\" it's", DoubleQuote.escapeQuotes("\\\"q\\\" it's"))
    }

    @Test
    fun testUnescapeForwardSlashes() {
        // Simple \/ becomes /
        assertEquals("/", unescapeForwardSlashes("\\/"))

        // \\/ (escaped backslash + literal slash) is preserved
        assertEquals("\\\\/", unescapeForwardSlashes("\\\\/"))

        // Mixed: `\` `\/` and `\\/`
        assertEquals("/ / \\\\/", unescapeForwardSlashes("\\/ / \\\\/"))

        // No slashes at all — passthrough
        assertEquals("hello\\nworld", unescapeForwardSlashes("hello\\nworld"))

        // Trailing backslash (no next char) — preserved as-is
        assertEquals("trailing\\", unescapeForwardSlashes("trailing\\"))

        // Multiple consecutive \/
        assertEquals("//", unescapeForwardSlashes("\\/\\/"))
    }

    @Test
    fun testRoundTripEscaping() {
        val testStrings = listOf(
            "Hello World",
            "Quote: \"test\"",
            "Backslash: \\",
            "Newline:\nTab:\t",
            "Unicode: €",
            "Emoji: 😀",
            "Path: /home/user",
            "Control: \b\u000C",
            "\u0000\u001F", // control characters
            "\u2028\u2029", // line/paragraph separators
            "Mixed\\\"content\nwith\ttabs"
        )
        
        for (original in testStrings) {
            val escaped = renderForJsonString(original)
            val unescaped = unescapeStringContent(escaped, DoubleQuote)
            assertEquals(original, unescaped, "Failed to round-trip: $original")
        }
    }
} 

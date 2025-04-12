package org.kson.parser

import org.kson.ast.renderForJsonString
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
        // Forward slash can be escaped but isn't required to be, we escape it for completeness
        // and hopes of broad compatibility
        assertJsonStringEscaping("/", "\\/")
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
} 

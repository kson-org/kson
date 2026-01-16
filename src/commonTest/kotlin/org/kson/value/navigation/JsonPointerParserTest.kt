package org.kson.value.navigation

import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.*
import org.kson.value.navigation.json_pointer.PointerParser
import org.kson.value.navigation.json_pointer.JsonPointerParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonPointerParserTest {

    private fun assertParsesTo(source: String, expectedReferenceTokens: List<String>) {
        val result = JsonPointerParser(source).parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        val actualTokens = result.tokens.map { (it as PointerParser.Tokens.Literal).value }
        assertEquals(expectedReferenceTokens, actualTokens)
    }

    private fun assertParserRejectsSource(source: String, expectedMessage: MessageType) {
        val result = JsonPointerParser(source).parse()
        assertTrue(result is PointerParser.ParseResult.Error)
        assertEquals(expectedMessage, result.message.type)
    }

    @Test
    fun testEmptyPointer() {
        assertParsesTo("", emptyList())
    }

    @Test
    fun testSingleEmptyToken() {
        assertParsesTo("/", listOf(""))
    }

    @Test
    fun testTwoEmptyTokens() {
        assertParsesTo("//", listOf("", ""))
    }

    @Test
    fun testSimpleToken() {
        assertParsesTo("/foo", listOf("foo"))
    }

    @Test
    fun testMultipleTokens() {
        assertParsesTo("/foo/bar/baz", listOf("foo", "bar", "baz"))
    }

    @Test
    fun testTokenWithEmptyAtEnd() {
        assertParsesTo("/foo/", listOf("foo", ""))
    }

    @Test
    fun testNumericToken() {
        assertParsesTo("/foo/0", listOf("foo", "0"))
    }

    @Test
    fun testEscapedSlash() {
        assertParsesTo("/a~1b", listOf("a/b"))
    }

    @Test
    fun testEscapedTilde() {
        assertParsesTo("/m~0n", listOf("m~n"))
    }

    @Test
    fun testMultipleEscapes() {
        assertParsesTo("/~1~0/~0~1", listOf("/~", "~/"))
    }

    @Test
    fun testComplexEscapes() {
        assertParsesTo("/foo~1bar~0baz", listOf("foo/bar~baz"))
    }

    @Test
    fun testConsecutiveEscapes() {
        assertParsesTo("/~1~1~0~0", listOf("//~~"))
    }

    @Test
    fun testWithoutHashPrefix() {
        assertParsesTo("/foo/bar", listOf("foo", "bar"))
    }

    @Test
    fun testSpecialCharacters() {
        assertParsesTo("/foo bar/baz@123", listOf("foo bar", "baz@123"))
    }

    @Test
    fun testUnicodeCharacters() {
        assertParsesTo("/你好/世界", listOf("你好", "世界"))
    }

    @Test
    fun testInvalidEscapeSequence() {
        assertParserRejectsSource("/foo~2bar", JSON_POINTER_INVALID_ESCAPE)
    }

    @Test
    fun testInvalidEscapeAtEnd() {
        assertParserRejectsSource("/foo~", JSON_POINTER_INCOMPLETE_ESCAPE)
    }

    @Test
    fun testInvalidStartCharacter() {
        assertParserRejectsSource("foo/bar", JSON_POINTER_BAD_START)
    }

    @Test
    fun testArrayIndexReference() {
        assertParsesTo("/foo/0/bar/1", listOf("foo", "0", "bar", "1"))
    }

    @Test
    fun testDashAsArrayEnd() {
        assertParsesTo("/foo/-", listOf("foo", "-"))
    }

    @Test
    fun testComplexNestedPath() {
        assertParsesTo(
            "/definitions/address/properties/street",
            listOf("definitions", "address", "properties", "street")
        )
    }

    @Test
    fun testRFC6901ExampleTokens() {
        // Examples from RFC 6901 section 5
        val testCases = mapOf(
            "" to emptyList(),
            "/foo" to listOf("foo"),
            "/foo/0" to listOf("foo", "0"),
            "/" to listOf(""),
            "/a~1b" to listOf("a/b"),
            "/c%d" to listOf("c%d"),
            "/e^f" to listOf("e^f"),
            "/g|h" to listOf("g|h"),
            "/i\\j" to listOf("i\\j"),
            "/k\"l" to listOf("k\"l"),
            "/ " to listOf(" "),
            "/m~0n" to listOf("m~n")
        )

        testCases.forEach { (pointer, expectedTokens) ->
            assertParsesTo(pointer, expectedTokens)
        }
    }

    @Test
    fun testEscapeOrderMatters() {
        // According to the assignment, ~1 must be processed before ~0
        // This test verifies that "~01" becomes "~1" (not "/1")
        assertParsesTo("/~01", listOf("~1"))
    }

    @Test
    fun testMixedValidAndEmptyTokens() {
        assertParsesTo("/foo//bar/", listOf("foo", "", "bar", ""))
    }

    @Test
    fun testSpacesInTokensAreValid() {
        // According to RFC 6901, spaces are valid unescaped characters in tokens
        assertParsesTo("/foo/bar extra", listOf("foo", "bar extra"))
        assertParsesTo("/path with spaces", listOf("path with spaces"))
        assertParsesTo("/multi word token", listOf("multi word token"))
    }

    @Test
    fun testInvalidEscapeLetterAfterTilde() {
        // Test invalid escape with letter after tilde
        assertParserRejectsSource("/foo~abc", JSON_POINTER_INVALID_ESCAPE)
    }

    @Test
    fun testInvalidEscapeSpecialCharAfterTilde() {
        // Test invalid escape with special character after tilde
        assertParserRejectsSource("/test~@value", JSON_POINTER_INVALID_ESCAPE)
    }

    @Test
    fun testIncompleteEscapeAtTokenEnd() {
        // Tilde at end of token before slash
        assertParserRejectsSource("/foo~/bar", JSON_POINTER_INVALID_ESCAPE)
    }

    @Test
    fun testEscapeAtStartOfToken() {
        // Escape sequences at the beginning of tokens
        assertParsesTo("/~0foo", listOf("~foo"))
        assertParsesTo("/~1bar", listOf("/bar"))
    }

    @Test
    fun testEscapeAtEndOfToken() {
        // Escape sequences at the end of tokens
        assertParsesTo("/foo~0", listOf("foo~"))
        assertParsesTo("/bar~1", listOf("bar/"))
    }

    @Test
    fun testMultipleConsecutiveTildes() {
        // Multiple tildes in sequence with valid escapes
        assertParsesTo("/~0~0~0", listOf("~~~"))
        assertParsesTo("/~1~1~1", listOf("///"))
    }

    @Test
    fun testMixedEscapesInSingleToken() {
        // Mix of ~0 and ~1 in various positions
        assertParsesTo("/start~0middle~1end", listOf("start~middle/end"))
        assertParsesTo("/~1~0~1~0", listOf("/~/~"))
    }

    @Test
    fun testInvalidEscapeNumbers() {
        // Test other numbers after tilde (not 0 or 1)
        assertParserRejectsSource("/foo~3bar", JSON_POINTER_INVALID_ESCAPE)
        assertParserRejectsSource("/test~9", JSON_POINTER_INVALID_ESCAPE)
    }

    @Test
    fun testEscapeFollowedBySlash() {
        // Valid escape followed immediately by path separator
        assertParsesTo("/~0/~1", listOf("~", "/"))
    }

    @Test
    fun testLongPath() {
        // Test a deeply nested path
        assertParsesTo(
            "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p",
            listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p")
        )
    }

    @Test
    fun testCharacterRangeBoundaries() {
        // Test characters at boundaries of valid ranges
        // 0x2E is '.', 0x2F is '/', 0x30 is '0'
        assertParsesTo("/test.char", listOf("test.char"))  // 0x2E is valid
        // 0x2F (/) must be escaped as ~1
        assertParsesTo("/test~1char", listOf("test/char"))
        assertParsesTo("/test0char", listOf("test0char"))  // 0x30 is valid

        // 0x7D is '}', 0x7E is '~', 0x7F is DEL
        assertParsesTo("/test}char", listOf("test}char"))  // 0x7D is valid
        // 0x7E (~) must be escaped as ~0
        assertParsesTo("/test~0char", listOf("test~char"))
        assertParsesTo("/test\u007Fchar", listOf("test\u007Fchar"))  // 0x7F (DEL) is valid
    }

    @Test
    fun testControlCharacters() {
        // Test various control characters (0x00-0x1F)
        assertParsesTo("/\u0000", listOf("\u0000"))  // NULL
        assertParsesTo("/\u0001", listOf("\u0001"))  // SOH
        assertParsesTo("/\u001F", listOf("\u001F"))  // Unit Separator
        assertParsesTo("/test\ttab", listOf("test\ttab"))  // TAB (0x09)
        assertParsesTo("/test\nline", listOf("test\nline"))  // LF (0x0A)
        assertParsesTo("/test\rreturn", listOf("test\rreturn"))  // CR (0x0D)
    }

    @Test
    fun testHighUnicodeCharacters() {
        // Test high Unicode characters (> 0x7F)
        assertParsesTo("/test\u0080char", listOf("test\u0080char"))  // First char after DEL
        assertParsesTo("/test\u00FFchar", listOf("test\u00FFchar"))  // Latin-1 supplement
        assertParsesTo("/test\u1000char", listOf("test\u1000char"))  // Myanmar
        assertParsesTo("/test\uFFFFchar", listOf("test\uFFFFchar"))  // High surrogate
    }

    @Test
    fun testAllPrintableASCII() {
        // Test that all printable ASCII except / and ~ work unescaped
        val printableASCII = StringBuilder()
        for (c in 0x20..0x7D) {
            if (c != 0x2F && c != 0x7E) {  // Skip / and ~
                printableASCII.append(c.toChar())
            }
        }
        assertParsesTo("/$printableASCII", listOf(printableASCII.toString()))
    }

    @Test
    fun testSpecialPathCases() {
        // Test some special cases that might be edge cases
        assertParsesTo("/~1~1", listOf("//"))  // Double slash escaped
        assertParsesTo("/~0~0", listOf("~~"))  // Double tilde escaped
        assertParsesTo("/~10", listOf("/0"))   // Slash followed by 0
        assertParsesTo("/~01", listOf("~1"))   // Tilde followed by 1
    }
}

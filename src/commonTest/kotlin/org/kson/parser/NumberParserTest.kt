package org.kson.parser

import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NumberParserTest {

    private fun assertParsesTo(numberSource: String, parsedNumber: Double) {
        assertEquals(parsedNumber, NumberParser(numberSource).parse().number)
    }

    /**
     * Assertion helper for testing that [source] is rejected by [NumberParser] with the messages listed in
     * [expectedParseMessageType]
     *
     * @param source is the number source to parse into a [Number]
     * @param expectedParseMessageType the [MessageType] produced by parsing [source]
     * @return the produced error message for further validation
     */
    private fun assertParserRejectsSource(
        source: String,
        expectedParseMessageType: MessageType
    ): Message {
        val numberParseResult = NumberParser(source).parse()

        val error = numberParseResult.error
        assertNotNull(error, "should have an error since we are asserting the parser failed")
        assertEquals(
            expectedParseMessageType,
            error.type,
            "Should have the expected parse errors."
        )

        assertEquals(
            null,
            numberParseResult.number,
            "Should produce a null a number if given string fails to parse"
        )

        return error
    }

    @Test
    fun testNumberParsing() {
        assertParsesTo("42.1", 42.1)
        assertParsesTo("42.1E0", 42.1)
        assertParsesTo("42.1e0", 42.1)
        assertParsesTo("4.21E1", 42.1)
        assertParsesTo("421E-1", 42.1)
        assertParsesTo("4210e-2", 42.1)
        assertParsesTo("0.421e2", 42.1)
        assertParsesTo("0.421e+2", 42.1)
        assertParsesTo("42.1E+0", 42.1)
        assertParsesTo("00042.1E0", 42.1)
        assertParsesTo("-42.1", -42.1)
        assertParsesTo("-42.1E0", -42.1)
        assertParsesTo("-42.1e0", -42.1)
        assertParsesTo("-4.21E1", -42.1)
        assertParsesTo("-421E-1", -42.1)
        assertParsesTo("-4210e-2", -42.1)
        assertParsesTo("-0.421e2", -42.1)
        assertParsesTo("-0.421e+2", -42.1)
        assertParsesTo("-42.1E+0", -42.1)
        assertParsesTo("-00042.1E0", -42.1)
    }

    @Test
    fun testDanglingExponentError() {
        assertParserRejectsSource("42.1E", MessageType.DANGLING_EXP_INDICATOR)
    }

    @Test
    fun testIllegalMinusSign() {
        assertParserRejectsSource("-nope", MessageType.ILLEGAL_MINUS_SIGN)
    }

    @Test
    fun testInvalidCharsInNumber() {
        assertParserRejectsSource("-4.21E1%2", MessageType.INVALID_DIGITS)
        assertParserRejectsSource("1nope", MessageType.INVALID_DIGITS)
        assertParserRejectsSource("nope", MessageType.INVALID_DIGITS)
    }

    @Test
    fun testWhitespaceInNumber() {
        assertParserRejectsSource("12 34", MessageType.INVALID_DIGITS)
        assertParserRejectsSource("12\n34", MessageType.INVALID_DIGITS)
    }

    @Test
    fun testDanglingDecimal() {
        assertParserRejectsSource("-4.", MessageType.DANGLING_DECIMAL)
    }
}
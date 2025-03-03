package org.kson.parser

import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class NumberParserTest {

    private fun assertParsesTo(numberSource: String, expectedNumber: Int) {
        assertParsesTo(numberSource, expectedNumber.toLong())
    }

    private fun assertParsesTo(numberSource: String, expectedNumber: Long) {
        val parsedNumber = NumberParser(numberSource).parse().number
        if (parsedNumber !is NumberParser.ParsedNumber.Integer) {
            fail("$numberSource should have parsed to an integer, but instead parsed to $parsedNumber")
        }
        assertEquals(expectedNumber, parsedNumber.value)
    }

    private fun assertParsesTo(numberSource: String, expectedNumber: Double) {
        val parsedNumber = NumberParser(numberSource).parse().number
        if (parsedNumber !is NumberParser.ParsedNumber.Decimal) {
            fail("$numberSource should have parsed to a decimal, but instead parsed to $parsedNumber")
        }
        assertEquals(expectedNumber, parsedNumber.value)
    }

    private fun assertParsedNumberString(numberSource: String, expectedString: String) {
        val parsedNumber = NumberParser(numberSource).parse().number
        assertNotNull(parsedNumber, "Failed to parse $numberSource")
        assertEquals(expectedString, parsedNumber.asString, "Normalized string of $numberSource should be $expectedString")
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
    fun testDecimalParsing() {
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
        assertParserRejectsSource("1.", MessageType.DANGLING_DECIMAL)
        assertParserRejectsSource("-4.", MessageType.DANGLING_DECIMAL)
    }

    @Test
    fun testIntegerParsing() {
        // Basic integer parsing
        assertParsesTo("0", 0)
        assertParsesTo("42", 42)
        assertParsesTo("123456789", 123456789)
        
        // Leading zeros
        assertParsesTo("00", 0)
        assertParsesTo("0042", 42)
        assertParsesTo("000123456789", 123456789)
        
        // Negative integers
        assertParsesTo("-0", 0)
        assertParsesTo("-42", -42)
        assertParsesTo("-123456789", -123456789)
        assertParsesTo("-000123456789", -123456789)
    }

    @Test
    fun testLongParsing() {
        assertParsesTo("2147483648", Int.MAX_VALUE + 1L)
        assertParsesTo("9223372036854775807", Long.MAX_VALUE)
        assertParsesTo("-9223372036854775808", Long.MIN_VALUE)

        // With leading zeros
        assertParsesTo("0009223372036854775807", Long.MAX_VALUE)
        assertParsesTo("-0009223372036854775808", Long.MIN_VALUE)
    }
    
    @Test
    fun testIntegerOverflow() {
        /**
         * [Long.MAX_VALUE] - 1
         */
        assertParserRejectsSource("9223372036854775808", MessageType.INTEGER_OVERFLOW)

        /**
         * [Long.MIN_VALUE] - 1
         */
        assertParserRejectsSource("-9223372036854775809", MessageType.INTEGER_OVERFLOW)

        // huuuuuuuuuuuuuuuuuuuuuuuge number
        assertParserRejectsSource("10000000000000000000000000000000", MessageType.INTEGER_OVERFLOW)
    }
    
    @Test
    fun testEdgeCaseDecimals() {
        // Very small decimals
        assertParsesTo("0.0000000001", 0.0000000001)
        assertParsesTo("1e-10", 1e-10)
        
        // Very large decimals
        assertParsesTo("1e20", 1e20)
        
        // Negative exponents
        assertParsesTo("1e-5", 0.00001)
        
        // Zero with different representations
        assertParsesTo("0.0", 0.0)
        assertParsesTo("0e0", 0.0)
        assertParsesTo("-0.0", -0.0)
    }
    
    @Test
    fun testExponentEdgeCases() {
        // Exponent with sign but no digits
        assertParserRejectsSource("1e+", MessageType.DANGLING_EXP_INDICATOR)
        assertParserRejectsSource("1e-", MessageType.DANGLING_EXP_INDICATOR)
        
        // Invalid exponent characters
        assertParserRejectsSource("1ex", MessageType.INVALID_DIGITS)
        
        // Multiple exponents
        assertParserRejectsSource("1e2e3", MessageType.INVALID_DIGITS)
    }
    
    @Test
    fun testMultipleDecimalPoints() {
        // Multiple decimal points
        assertParserRejectsSource("1.2.3", MessageType.INVALID_DIGITS)
    }
    
    @Test
    fun testMalformedNumbers() {
        assertParserRejectsSource("", MessageType.INVALID_DIGITS)
        assertParserRejectsSource("-", MessageType.ILLEGAL_MINUS_SIGN)
        assertParserRejectsSource(".", MessageType.INVALID_DIGITS)
        assertParserRejectsSource("e", MessageType.INVALID_DIGITS)
        assertParserRejectsSource("x123", MessageType.INVALID_DIGITS)
        assertParserRejectsSource("1-2", MessageType.INVALID_DIGITS)
    }

    @Test
    fun testNormalizedIntegerStrings() {
        assertParsedNumberString("0", "0")
        assertParsedNumberString("00", "0")
        assertParsedNumberString("000", "0")
        assertParsedNumberString("42", "42")
        assertParsedNumberString("042", "42")
        assertParsedNumberString("00042", "42")

        assertParsedNumberString("-0", "-0")
        assertParsedNumberString("-00", "-0")
        assertParsedNumberString("-42", "-42")
        assertParsedNumberString("-042", "-42")
        assertParsedNumberString("-00042", "-42")

        assertParsedNumberString("0002147483648", "2147483648")
        assertParsedNumberString("000009223372036854775807", "9223372036854775807")
    }

    @Test
    fun testNormalizedDecimalStrings() {
        assertParsedNumberString("0.0", "0.0")
        assertParsedNumberString("00.0", "0.0")
        assertParsedNumberString("0.00", "0.00")
        assertParsedNumberString("042.1", "42.1")

        assertParsedNumberString("42.10", "42.10")
        assertParsedNumberString("-0.0", "-0.0")
        assertParsedNumberString("-00.0", "-0.0")
        assertParsedNumberString("-042.1", "-42.1")

        assertParsedNumberString("1e2", "1e2")
        assertParsedNumberString("01e2", "1e2")
        assertParsedNumberString("1.0e2", "1.0e2")
        assertParsedNumberString("01.0e2", "1.0e2")
        assertParsedNumberString("0.1e2", "0.1e2")
        assertParsedNumberString("00.1e2", "0.1e2")

        assertParsedNumberString("-1e2", "-1e2")
        assertParsedNumberString("-01e2", "-1e2")
        assertParsedNumberString("-0.1e2", "-0.1e2")
        assertParsedNumberString("-00.1e2", "-0.1e2")
    }
}

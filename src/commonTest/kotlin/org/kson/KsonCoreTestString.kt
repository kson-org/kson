package org.kson

import kotlin.test.Test

class KsonCoreTestString : KsonCoreTest {
    @Test
    fun testStringLiteralSource() {
        assertParsesTo(
            """
                "This is a string"
            """,
            "'This is a string'",
            "\"This is a string\"",
            "\"This is a string\""
        )
    }

    @Test
    fun testEmptyString() {
        assertParsesTo("''", "''", "\"\"", "\"\"")
    }

    @Test
    fun testQuotedSimpleString() {
        assertParsesTo(
            """
                "simpleKey": "simpleValue"
            """.trimIndent(),
            """
                simpleKey: simpleValue
            """.trimIndent(),
            """
                simpleKey: simpleValue
            """.trimIndent(),
            """
                {
                  "simpleKey": "simpleValue"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun testStringWithRawWhitespace() {
        assertParsesTo(
            """
            |"This is a string with raw, unescaped whitespace ${'\t'}
            |${'\t'}tabbed-indented second line"
            """.trimMargin(),
            """
            |'This is a string with raw, unescaped whitespace ${'\t'}
            |${'\t'}tabbed-indented second line'
            """.trimMargin(),
            """
            |"This is a string with raw, unescaped whitespace ${'\t'}
            |${'\t'}tabbed-indented second line"
            """.trimMargin(),
            """
            |"This is a string with raw, unescaped whitespace \t\n\ttabbed-indented second line"
            """.trimMargin()
        )
    }

    @Test
    fun testStringEscapes() {
        assertParsesTo(
            """
                "this'll need \"escaping\""
            """.trimIndent(),
            """
                'this\'ll need "escaping"'
            """.trimIndent(),
            """
                "this'll need \"escaping\""
            """.trimIndent(),"""
                "this'll need \"escaping\""
            """.trimIndent())
    }

    @Test
    fun testBackslashEscaping() {
        // Test backslash before delimiter
        assertParsesTo(
            """
                "string with \\ and \""
            """.trimIndent(),
            """
                'string with \\ and "'
            """.trimIndent(),
            """
                "string with \\ and \""
            """.trimIndent(),
            """
                "string with \\ and \""
            """.trimIndent()
        )

        // Test multiple backslashes before delimiter
        assertParsesTo(
            """
                "string with \\\""
            """.trimIndent(),
            """
                'string with \\"'
            """.trimIndent(),
            """
                "string with \\\""
            """.trimIndent(),
            """
                "string with \\\""
            """.trimIndent()
        )
    }

    @Test
    fun testMultipleDelimiters() {
        // Test mix of single and double quotes
        assertParsesTo(
            """
                "string 'with' \"quotes\""
            """.trimIndent(),
            """
                'string \'with\' "quotes"'
            """.trimIndent(),
            """
                "string 'with' \"quotes\""
            """.trimIndent(),
            """
                "string 'with' \"quotes\""
            """.trimIndent()
        )
    }

    @Test
    fun testEdgeCases() {
        // Test string with only delimiters
        assertParsesTo(
            """
                "\"\""
            """.trimIndent(),
            """
                '""'
            """.trimIndent(),
            """
                "\"\""
            """.trimIndent(),
            """
                "\"\""
            """.trimIndent()
        )

        // Test string with alternating backslashes and delimiters
        assertParsesTo(
            """
                "\\\"\\\""
            """.trimIndent(),
            """
                '\\"\\"'
            """.trimIndent(),
            """
                "\\\"\\\""
            """.trimIndent(),
            """
                "\\\"\\\""
            """.trimIndent()
        )
    }

    @Test
    fun testBackslashSequences() {
        // Test sequences of backslashes not followed by delimiters
        assertParsesTo(
            """
                "\\\\n"
            """.trimIndent(),
            """
                '\\\\n'
            """.trimIndent(),
            """
                "\\\\n"
            """.trimIndent(),
            """
                "\\\\n"
            """.trimIndent()
        )

        // Test backslash at end of string
        assertParsesTo(
            """
                "\\"
            """.trimIndent(),
            """
                '\\'
            """.trimIndent(),
            """
                "\\"
            """.trimIndent(),
            """
                "\\"
            """.trimIndent()
        )
    }

    @Test
    fun testUnquotedNonAlphaNumericString() {
        assertParsesTo(
            """
                水滴石穿
            """.trimIndent(),
            """
                水滴石穿
            """.trimIndent(),
            """
                水滴石穿
            """.trimIndent(),
            """
               "水滴石穿"
            """.trimIndent()
        )
    }

    @Test
    fun testReservedKeywordStringsAreQuoted() {
        // Test that strings with reserved keyword content are properly quoted
        assertParsesTo(
            """
                Y
            """.trimIndent(),
            """
                Y
            """.trimIndent(),
            """
                "Y"
            """.trimIndent(),
            """
                "Y"
            """.trimIndent()
        )

        assertParsesTo(
            """
                False
            """.trimIndent(),
            """
                False
            """.trimIndent(),
            """
                "False"
            """.trimIndent(),
            """
                "False"
            """.trimIndent()
        )

        assertParsesTo(
            """
                Null
            """.trimIndent(),
            """
                Null
            """.trimIndent(),
            """
                "Null"
            """.trimIndent(),
            """
                "Null"
            """.trimIndent()
        )

        assertParsesTo(
            """
                No
            """.trimIndent(),
            """
                No
            """.trimIndent(),
            """
                "No"
            """.trimIndent(),
            """
                "No"
            """.trimIndent()
        )
    }

    @Test
    fun testValidEscapeSequences() {
        assertParsesTo(
            """ 
                "\" ' \\ \/ \b \f \n \r \t\""
             """.trimIndent(),
            """
                '" \' \\ \/ \b \f \n \r \t"'
             """.trimIndent(),
            """
                "\" ' \\ / \b \f \n \r \t\""
                """.trimIndent(),
            """
                "\" ' \\ \/ \b \f \n \r \t\""
            """.trimIndent()
        )
    }

    @Test
    fun testForwardSlashEscapeHandling() {
        /**
         * For KSON and JSON, we maintain whatever the user input: it's consistent, legal, and computationally
         * beneficial since we don't have to do a full unescape/re-escape loop for every KSON/JSON format or
         * transpile. YAML on the other hand does not allow escaped forward slashes, so we must be careful to
         * remove any we find
         */
        assertParsesTo(
            """ 
                "\/ / \\/"
             """.trimIndent(),
            """
                '\/ / \\/'
             """.trimIndent(),
            """
                "/ / \\/"
                """.trimIndent(),
            """
                "\/ / \\/"
            """.trimIndent()
        )
    }

    @Test
    fun testValidUnicodeEscape() {
        assertParsesTo(
            "\"\\u0041\\uFFFF\\u1234\"",
            "'\\u0041\\uFFFF\\u1234'",
            "\"\\u0041\\uFFFF\\u1234\"",
            "\"\\u0041\\uFFFF\\u1234\""
        )
    }

}

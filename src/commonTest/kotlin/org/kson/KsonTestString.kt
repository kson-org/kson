package org.kson

import kotlin.test.Test

class KsonTestString : KsonTest {
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
                "true"
            """.trimIndent(),
            """
                'true'
            """.trimIndent(),
            """
                "true"
            """.trimIndent(),
            """
                "true"
            """.trimIndent()
        )

        assertParsesTo(
            """
                "false"
            """.trimIndent(),
            """
                'false'
            """.trimIndent(),
            """
                "false"
            """.trimIndent(),
            """
                "false"
            """.trimIndent()
        )

        assertParsesTo(
            """
                "null"
            """.trimIndent(),
            """
                'null'
            """.trimIndent(),
            """
                "null"
            """.trimIndent(),
            """
                "null"
            """.trimIndent()
        )
    }
}

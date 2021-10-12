package org.kson.parser

import org.kson.parser.TokenType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LexerTest {
    /**
     * Assertion helper for testing [source] produces the sequence of [expectedTokenTypes]
     */
    private fun assertTokenizesTo(source: String, expectedTokenTypes: List<TokenType>) {
        val messageSink = MessageSink()
        val actualTokens = Lexer(source, messageSink).tokenize()
        val actualTokenTypes = actualTokens.map { it.tokenType }.toMutableList()

        // automatically clip off the always-trailing EOF so test-writers don't need to worry about it
        val eof = actualTokenTypes.removeLast()
        if (eof != EOF) {
            throw Exception("Tokenize should always produce a list of tokens ending in EOF... what's going on?")
        }

        assertFalse(messageSink.hasErrors(), "Should not have lexing errors, got:\n\n" + messageSink.print())
        assertEquals(
            expectedTokenTypes,
            actualTokenTypes
        )
    }

    @Test
    fun testStringLiteralSource() {
        assertTokenizesTo(
            """
                "This is a string"
            """,
            listOf(STRING)
        )
    }

    @Test
    fun testNumberLiteralSource() {
        assertTokenizesTo(
            """
                42
            """,
            listOf(NUMBER)
        )
    }

    @Test
    fun testBooleanLiteralSource() {
        assertTokenizesTo(
            """
                true
            """,
            listOf(TRUE)
        )

        assertTokenizesTo(
            """
                false
            """,
            listOf(FALSE)
        )
    }

    @Test
    fun testNilLiteralSource() {
        assertTokenizesTo(
            """
                null
            """,
            listOf(NULL)
        )
    }

    @Test
    fun testEmptyListSource() {
        assertTokenizesTo(
            """
                []
            """,
            listOf(BRACKET_L, BRACKET_R)
        )
    }

    @Test
    fun testListSource() {
        assertTokenizesTo(
            """
                ["a string"]
            """,
            listOf(BRACKET_L, STRING, BRACKET_R)
        )

        assertTokenizesTo(
            """
                [42, 43, 44]
            """,
            listOf(BRACKET_L, NUMBER, COMMA, NUMBER, COMMA, NUMBER, BRACKET_R)
        )
    }

    @Test
    fun testEmptyObjectSource() {
        assertTokenizesTo(
            """
                {}
            """,
            listOf(BRACE_L, BRACE_R)
        )
    }

    @Test
    fun testObjectSource() {
        assertTokenizesTo(
            """
                {
                    key: val
                    "string key": 66
                    hello: "y'all"
                }
            """,
            listOf(BRACE_L, IDENTIFIER, COLON, IDENTIFIER, STRING, COLON, NUMBER, IDENTIFIER, COLON, STRING, BRACE_R)
        )

        assertTokenizesTo(
            """
                key: val
                "string key": 66
                hello: "y'all"
            """,
            listOf(IDENTIFIER, COLON, IDENTIFIER, STRING, COLON, NUMBER, IDENTIFIER, COLON, STRING)
        )
    }

    @Test
    fun testComments() {
        assertTokenizesTo(
            """
                # a comment!
                key: val
                        # wahoo!  Another comment!
                     # follow-up comment with wacky indent 🕺
                "string key": 66
                hello: "y'all"
            """,
            listOf(
                COMMENT,
                IDENTIFIER,
                COLON,
                IDENTIFIER,
                COMMENT,
                COMMENT,
                STRING,
                COLON,
                NUMBER,
                IDENTIFIER,
                COLON,
                STRING
            )
        )
    }
}
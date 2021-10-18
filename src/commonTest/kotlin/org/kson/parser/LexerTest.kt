package org.kson.parser

import org.kson.parser.TokenType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LexerTest {
    /**
     * Assertion helper for testing [source] produces the sequence of [expectedTokenTypes].
     *
     * @param source is the kson source to tokenize
     * @param expectedTokenTypes is the list of expected types for the resulting tokens
     * @param message optionally pass a custom failure message for this assertion
     *
     * Returns the list of whole [Token]s for further validation
     */
    private fun assertTokenizesTo(
        source: String,
        expectedTokenTypes: List<TokenType>,
        message: String? = null
    ): List<Token> {
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
            actualTokenTypes,
            message
        )

        return actualTokens
    }

    /**
     * Ensure we're well-behaved on source which has no leading or trailing whitespace
     */
    @Test
    fun testNoWhitespaceSource() {
        assertTokenizesTo(
            "1",
            listOf(NUMBER)
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

    @Test
    fun testEmbedBlockSource() {
        assertTokenizesTo(
            """
                ```
                    this is a raw embed
                ```
            """,
            listOf(EMBED_START, EMBEDDED_BLOCK, EMBED_END)
        )

        assertTokenizesTo(
            """
                ```sql
                    select * from something
                ```
            """,
            listOf(EMBED_START, EMBED_TAG, EMBEDDED_BLOCK, EMBED_END)
        )
    }

    @Test
    fun testEmbedBlockIndentTrimming() {
        val oneLineEmbedTokens = assertTokenizesTo(
            """
                ```
                this is a raw embed
                ```
            """,
            listOf(EMBED_START, EMBEDDED_BLOCK, EMBED_END)
        )

        assertEquals("this is a raw embed\n", oneLineEmbedTokens[1].value)

        val mulitLineEmbedTokens = assertTokenizesTo(
            """
                ```sql
                    this is a multi-line
                        raw embed
                who's indent will be determined by
                                the leftmost line
                ```
            """,
            listOf(EMBED_START, EMBED_TAG, EMBEDDED_BLOCK, EMBED_END)
        )

        assertEquals(
            """
                this is a multi-line
                    raw embed
            who's indent will be determined by
                            the leftmost line
            
            """.trimIndent(),
            mulitLineEmbedTokens[2].value
        )

        val mulitLineIndentedEmbedTokens = assertTokenizesTo(
            """
                ```sql
                    this is a multi-line
                        raw embed
                who's indent will be determined by
                                the leftmost line,
                which is the end delimiter in this case
              ```
            """,
            listOf(EMBED_START, EMBED_TAG, EMBEDDED_BLOCK, EMBED_END)
        )

        assertEquals(
"""      this is a multi-line
          raw embed
  who's indent will be determined by
                  the leftmost line,
  which is the end delimiter in this case
""",
            mulitLineIndentedEmbedTokens[2].value
        )
    }

    @Test
    fun testEmbedBlockTrialingWhitespace() {
        val trailingNewlineTokens = assertTokenizesTo(
            """
                ```
                this should have a newline at the end
                ```
            """,
            listOf(EMBED_START, EMBEDDED_BLOCK, EMBED_END)
        )

        assertEquals("this should have a newline at the end\n", trailingNewlineTokens[1].value)

        val trailingSpacesTokens = assertTokenizesTo(
            """
                ```
                this lovely embed
                    should have four trailing 
                    spaces and a newline at the end    
                ```
            """,
            listOf(EMBED_START, EMBEDDED_BLOCK, EMBED_END)
        )

        assertEquals(
            """
            this lovely embed
                should have four trailing 
                spaces and a newline at the end    

            """.trimIndent(),
            trailingSpacesTokens[1].value
        )

        val zeroTrailingWhitespaceTokens = assertTokenizesTo(
            """
                ```
                    this on the other hand,
                    should have spaces but no newline at the end    ```
            """,
            listOf(EMBED_START, EMBEDDED_BLOCK, EMBED_END)
        )

        assertEquals(
            "this on the other hand,\nshould have spaces but no newline at the end    ".trimIndent(),
            zeroTrailingWhitespaceTokens[1].value
        )
    }

    @Test
    fun testEmbedBlockTrailingWhitespace() {
        assertTokenizesTo(
            // note the extra whitespace after the opening ```
            """
                ```   
                    this is a raw embed
                ```
            """,
            listOf(EMBED_START, EMBEDDED_BLOCK, EMBED_END),
            "should allow trailing whitespace after the opening '```'"
        )

        assertTokenizesTo(
            // note the extra whitespace after the opening ```
            """   
                ```sql
                    select * from something
                ```
            """,
            listOf(EMBED_START, EMBED_TAG, EMBEDDED_BLOCK, EMBED_END),
            "should allow trailing whitespace after the opening '```embedTag'"
        )
    }
}
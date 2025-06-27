package org.kson

import org.kson.parser.messages.MessageType.*
import kotlin.test.Test

class KsonTestEmbedBlockError : KsonTestError {
    /**
     * Regression test for a parsing problem we had at the boundary:
     * this was blowing up with an index out of bounds rather than
     * parsing with an [EMBED_BLOCK_NO_CLOSE] error as it should
     */
    @Test
    fun testUnclosedEmbedWithEscape() {
        assertParserRejectsSource(
            """
                %%
                %\%
            """.trimIndent(),
            listOf(EMBED_BLOCK_NO_CLOSE)
        )
    }

    @Test
    fun testUnclosedEmbedDelimiterError() {
        assertParserRejectsSource("%%\n", listOf(EMBED_BLOCK_NO_CLOSE))
    }

    @Test
    fun testUnclosedEmbedAlternateDelimiterError() {
        assertParserRejectsSource("$\n", listOf(EMBED_BLOCK_NO_CLOSE))
    }

    @Test
    fun testEmbedBlockPartialDelim() {
        assertParserRejectsSource(
            """
                %
            """,
            listOf(EMBED_BLOCK_NO_CLOSE)
        )

        assertParserRejectsSource(
            """
                $
            """,
            listOf(EMBED_BLOCK_NO_CLOSE)
        )
    }
}

package org.kson

import org.kson.ast.KsonRoot
import org.kson.parser.messages.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * todo many if not all of the tests in [org.kson.parser.ParserTest] likely fit more naturally here since
 *      a [Kson] parse is more holistic, across both the lexing and (grammar) parsing phase
 */
class KsonTest {
    /**
     * Assertion helper for testing that [source] is rejected by the parser with the messages listed in
     * [expectedParseMessages]
     *
     * @param source is the kson source to parse into a [KsonRoot]
     * @param expectedParseMessages a list of [Message]s produced by parsing [source]
     */
    private fun assertParserRejectsSource(source: String,
                                          expectedParseMessages: List<Message>) {
        val parseResult = Kson.parse(source)

        assertEquals(expectedParseMessages, parseResult.messages.map { it.message }, "Should have the expected parse errors.")
        assertTrue (
            parseResult.hasErrors(),
            "Should set the hasErrors flag appropriate when there are errors"
        )
        assertEquals(
            null,
            parseResult.ast,
            "Should produce a null AST when there are errors"
        )
    }

    @Test
    fun testUnclosedEmbedTicksError() {
        assertParserRejectsSource("```\n", listOf(Message.EMBED_BLOCK_NO_CLOSE))
    }
}
package org.kson.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * NOTE: most Kson parsing tests are done the more holistic level in [org.kson.KsonTest].  If/when we have
 * a need to test specifically at the [Parser] class level, those tests belong here.
 */
class ParserTest {

    /**
     * NOTE: testing [Parser] directly rarely makes sense, and this is mostly a placeholder test.
     * Test in [org.kson.KsonTest] instead.  See class level doc above for more detail.
     */
    @Test
    fun testSanityCheckParse() {
        val nullTokenStream = listOf(Token(TokenType.NULL, Lexeme("null", Location(0, 0, 0, 4, 0, 4)), "null"))
        val ksonRoot = Parser(nullTokenStream, MessageSink()).parse()
        assertNotNull(ksonRoot)
        assertEquals(ksonRoot.toKsonSource(0), "null")
    }
}
package org.kson.parser

import org.kson.ast.AstNode
import org.kson.ast.CompileTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        val nullTokenStream = listOf(
            Token(TokenType.NULL, Lexeme("null", Location(0, 0, 0, 4, 0, 4)), "null"),
            Token(TokenType.EOF, Lexeme("", Location(0, 4, 0, 4, 4, 4)), "")
        )
        val builder = KsonBuilder(nullTokenStream)
        Parser(builder).parse()
        val messageSink = MessageSink()
        val ksonRoot = builder.buildTree(messageSink)
        assertNotNull(ksonRoot)
        assertTrue(messageSink.loggedMessages().isEmpty())
        assertEquals(ksonRoot.toCommentedSource(AstNode.Indent(), CompileTarget.Kson), "null")
    }
}
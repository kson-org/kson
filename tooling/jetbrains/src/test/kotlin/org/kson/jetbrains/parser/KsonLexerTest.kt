package org.kson.jetbrains.parser

import com.intellij.testFramework.LightPlatformTestCase
import org.kson.parser.TokenType

class KsonLexerTest : LightPlatformTestCase() {

    fun testLexerWithNonZeroStartOffset() {
        val fullBuffer = """prefix content {key:value}"""
        val lexer = KsonLexer()

        // Start lexing from offset 15 (after "prefix content ")
        val startOffset = 15
        val endOffset = fullBuffer.length
        lexer.start(fullBuffer, startOffset, endOffset, 0)

        // {
        assertEquals(elem(TokenType.CURLY_BRACE_L), lexer.tokenType)
        // Token start should be at position 15 (not 0)
        assertEquals(15, lexer.tokenStart)
        assertEquals(16, lexer.tokenEnd)

        // key
        lexer.advance()
        assertEquals(elem(TokenType.UNQUOTED_STRING), lexer.tokenType)
        // Token positions should be relative to the full buffer
        assertEquals(16, lexer.tokenStart)
        assertEquals(19, lexer.tokenEnd)

        // :
        lexer.advance()
        assertEquals(elem(TokenType.COLON), lexer.tokenType)
        assertEquals(19, lexer.tokenStart)
        assertEquals(20, lexer.tokenEnd)

        // value
        lexer.advance()
        assertEquals(elem(TokenType.UNQUOTED_STRING), lexer.tokenType)
        assertEquals(20, lexer.tokenStart)
        assertEquals(25, lexer.tokenEnd)

        // }
        lexer.advance()
        assertEquals(elem(TokenType.CURLY_BRACE_R), lexer.tokenType)
        assertEquals(25, lexer.tokenStart)
        assertEquals(26, lexer.tokenEnd)
    }
}
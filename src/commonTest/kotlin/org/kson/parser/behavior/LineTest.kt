package org.kson.parser.behavior

import org.kson.parser.behavior.LineEnding.*
import kotlin.test.Test
import kotlin.test.assertEquals

class LineTest {
    @Test
    fun testSplit() {
        assertEquals(
            listOf(Line("a", NEWLINE), Line("b", CRLF), Line("", CRLF), Line("c", CRLF), Line("", NONE)),
            Line.splitToLines("a\nb\r\n\r\nc\r\n")
        )
    }

    @Test
    fun testSplitSlashRIsNotALineEnding() {
        assertEquals(
            listOf(Line("a\rb", NEWLINE), Line("\r", NONE)),
            Line.splitToLines("a\rb\n\r")
        )
    }

    @Test
    fun testSplitEmpty() {
        assertEquals(listOf(Line("", NONE)), Line.splitToLines(""))
    }

    @Test
    fun testLineEndingAt() {
        assertEquals(NEWLINE, LineEnding.at('\n', 'a'))
        assertEquals(NEWLINE, LineEnding.at('\n', null))
        assertEquals(CRLF, LineEnding.at('\r', '\n'))
        assertEquals(NONE, LineEnding.at('\r', 'a'))
        assertEquals(NONE, LineEnding.at('\r', null))
        assertEquals(NONE, LineEnding.at('a', '\n'))
        assertEquals(NONE, LineEnding.at(null, null))
    }
}

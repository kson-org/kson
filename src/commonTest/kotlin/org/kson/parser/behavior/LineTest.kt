package org.kson.parser.behavior

import kotlin.test.Test
import kotlin.test.assertEquals

class LineTest {
    @Test
    fun testSplit() {
        assertEquals(
            listOf(Line("a", "\n"), Line("b", "\r\n"), Line("", "\r\n"), Line("c", "\r\n"), Line("", "")),
            Line.splitToLines("a\nb\r\n\r\nc\r\n")
        )
    }

    @Test
    fun testSplitSlashRIsNotALineEnding() {
        assertEquals(
            listOf(Line("a\rb", "\n"), Line("\r", "")),
            Line.splitToLines("a\rb\n\r")
        )
    }

    @Test
    fun testSplitEmpty() {
        assertEquals(listOf(Line("", "")), Line.splitToLines(""))
    }
}

package org.kson.parser.behavior.embedblock

import kotlin.test.Test
import kotlin.test.assertEquals

class EmbedBlockIndentTest {
    @Test
    fun `test trimMinimumIndent with mixed indentation`() {
        val input = """
            |   first line
            |       second line
            |     third line
            |   fourth line
        """.trimMargin()

        val embedBlockIndent = EmbedBlockIndent(input)

        val expected = """
            |first line
            |    second line
            |  third line
            |fourth line
        """.trimMargin()

        assertEquals(3, embedBlockIndent.computeMinimumIndent())
        assertEquals(expected, embedBlockIndent.trimMinimumIndent())
    }

    @Test
    fun `test trimMinimumIndent with empty lines`() {
        // Note how the empty line contains one space.
        val input = """
            |   first line
            |  
            |   third line
            |   fourth line
        """.trimMargin()

        val embedBlockIndent = EmbedBlockIndent(input)

        val expected = """
            | first line
            |
            | third line
            | fourth line
        """.trimMargin()

        assertEquals(2, embedBlockIndent.computeMinimumIndent())
        assertEquals(expected, embedBlockIndent.trimMinimumIndent())
    }

    @Test
    fun `test trimMinimumIndent with no indentation`() {
        val input = """
            |first line
            |second line
            |third line
        """.trimMargin()

        val expected = """
            |first line
            |second line
            |third line
        """.trimMargin()

        val embedBlockIndent = EmbedBlockIndent(input)
        assertEquals(0, embedBlockIndent.computeMinimumIndent())
        assertEquals(expected, embedBlockIndent.trimMinimumIndent())
    }

    @Test
    fun `test computeMinimumIndent with empty lines`() {
        val input = """
            |   first line
            |   
            |   third line
            |   fourth line
        """.trimMargin()

        val embedBlockIndent = EmbedBlockIndent(input)
        val result = embedBlockIndent.computeMinimumIndent()

        assertEquals(3, result)
    }

    @Test
    fun `test trimMinimumIndent with tabs and spaces`() {
        val input = """
            |   first line
            |\tsecond line
            |\t   third line
            |   fourth line
        """.trimMargin()

        val embedBlockIndent = EmbedBlockIndent(input)

        val expected = """
            |   first line
            |\tsecond line
            |\t   third line
            |   fourth line
        """.trimMargin()

        assertEquals(0, embedBlockIndent.computeMinimumIndent())
        assertEquals(expected, embedBlockIndent.trimMinimumIndent())

    }

    @Test
    fun `test trimMinimumIndent with single line`() {
        val input = "    single line"
        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(4, embedBlockIndent.computeMinimumIndent())
        assertEquals("single line", embedBlockIndent.trimMinimumIndent())

    }

    @Test
    fun `test trimMinimumIndent with empty string`() {
        val input = ""
        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(0, embedBlockIndent.computeMinimumIndent())
        assertEquals("", embedBlockIndent.trimMinimumIndent())
    }

    @Test
    fun `test trimMinimumIndent determined by trailing end-delim`() {
        val input = """
            %
                this should stay indented
                
            %%
        """.trimIndent()
        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(0, embedBlockIndent.computeMinimumIndent())
        assertEquals(input, embedBlockIndent.trimMinimumIndent())
    }
} 

package org.kson.parser.behavior.embedblock

import kotlin.test.Test
import kotlin.test.assertEquals

class EmbedBlockIndentTest {
    @Test
    fun `test trimmedContent with mixed indentation`() {
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

        assertEquals(3, embedBlockIndent.minimumIndent)
        assertEquals(expected, embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test blank line shorter than the minimum does not lower it`() {
        // the blank line holds two spaces, less than the three-space minimum of the content
        val input = "   first line\n  \n   third line\n   fourth line"

        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(3, embedBlockIndent.minimumIndent)
        assertEquals("first line\n\nthird line\nfourth line", embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test empty line does not lower the minimum`() {
        val input = "  # Heading\n\n  some text"

        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(2, embedBlockIndent.minimumIndent)
        assertEquals("# Heading\n\nsome text", embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test blank line longer than the minimum keeps its excess`() {
        // the blank line holds five spaces: two of indent, three of content
        val input = "  first line\n     \n  third line"

        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(2, embedBlockIndent.minimumIndent)
        assertEquals("first line\n   \nthird line", embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test final line always counts because it is the closing delimiter line`() {
        // a blank final line two spaces wide: the closing delimiter sits at column 2
        val closeDelimAtTwo = EmbedBlockIndent("    first line\n\n  ")
        assertEquals(2, closeDelimAtTwo.minimumIndent)
        assertEquals("  first line\n\n", closeDelimAtTwo.trimmedContent)

        // an empty final line: the closing delimiter sits at column 0, so nothing is trimmed
        val closeDelimAtZero = EmbedBlockIndent("    first line\n\n")
        assertEquals(0, closeDelimAtZero.minimumIndent)
        assertEquals("    first line\n\n", closeDelimAtZero.trimmedContent)

        // content that is nothing but blank lines is measured by the closing delimiter alone
        val allBlank = EmbedBlockIndent("      \n  ")
        assertEquals(2, allBlank.minimumIndent)
        assertEquals("    \n", allBlank.trimmedContent)
    }

    @Test
    fun `test trimmedIndentPerLine reports what trimmedContent removed from each line`() {
        val input = "    first line\n\n \n      \n  "
        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(2, embedBlockIndent.minimumIndent)
        assertEquals(listOf(2, 0, 1, 2, 2), embedBlockIndent.trimmedIndentPerLine)
        assertEquals("  first line\n\n\n    \n", embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test trimmedContent with no indentation`() {
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
        assertEquals(0, embedBlockIndent.minimumIndent)
        assertEquals(expected, embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test blank line at the minimum is trimmed to empty`() {
        // the blank line holds three spaces, exactly the minimum of the content
        val input = "   first line\n   \n   third line\n   fourth line"

        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(3, embedBlockIndent.minimumIndent)
        assertEquals("first line\n\nthird line\nfourth line", embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test trimmedContent with tabs and spaces`() {
        // indent is measured in characters, a tab counting as one, so the tab-indented line sets the minimum
        val input = "   first line\n\tsecond line\n\t   third line\n   fourth line"

        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(1, embedBlockIndent.minimumIndent)
        assertEquals("  first line\nsecond line\n   third line\n  fourth line", embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test blank line of tabs shorter than the minimum does not lower it`() {
        val input = "\t\tfirst line\n\t\n\t\tthird line"

        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(2, embedBlockIndent.minimumIndent)
        assertEquals(listOf(2, 1, 2), embedBlockIndent.trimmedIndentPerLine)
        assertEquals("first line\n\nthird line", embedBlockIndent.trimmedContent)
    }

    @Test
    fun `test trimmedContent with single line`() {
        val input = "    single line"
        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(4, embedBlockIndent.minimumIndent)
        assertEquals("single line", embedBlockIndent.trimmedContent)

    }

    @Test
    fun `test trimmedContent with empty string`() {
        val input = ""
        val embedBlockIndent = EmbedBlockIndent(input)

        assertEquals(0, embedBlockIndent.minimumIndent)
        assertEquals("", embedBlockIndent.trimmedContent)
    }
} 

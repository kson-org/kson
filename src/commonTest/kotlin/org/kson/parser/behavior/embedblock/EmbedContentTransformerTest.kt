package org.kson.parser.behavior.embedblock

import org.kson.parser.Coordinates
import org.kson.parser.Location
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbedContentTransformerTest {

    @Test
    fun testSimpleSingleLineNoEscapesNoIndent() {
        val rawEmbedContent = "hello"
        val baseLocation = Location(
            Coordinates(10, 5),
            Coordinates(10, 10),
            100,
            105
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        // Verify no transformation occurred
        assertEquals("hello", transformer.processedContent)

        // Position at "he|llo" (offset 2)
        val result = transformer.mapToOriginal(2, 3)

        // Maps to offset 7 on line 10 of `baseLocation`
        assertEquals(Coordinates(10, 7), result.start)
        assertEquals(Coordinates(10, 8), result.end)
        assertEquals(102, result.startOffset)
        assertEquals(103, result.endOffset)
    }

    @Test
    fun testSingleLineWithEscape() {
        val rawEmbedContent = """{ "key": "val%\%ue" }"""
        val processed = """{ "key": "val%%ue" }"""
        val baseLocation = Location(
            Coordinates(5, 0),
            Coordinates(5, rawEmbedContent.length),
            50,
            50 + rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Position at "val|%%|ue" in processed (offsets 13-15)
        val result = transformer.mapToOriginal(13, 15)

        // In rawEmbedContent, this should map to "val|%\%|ue" (offsets 13-16)
        assertEquals(Coordinates(5, 13), result.start)
        assertEquals(Coordinates(5, 16), result.end)
        assertEquals(63, result.startOffset)
        assertEquals(66, result.endOffset)
    }

    @Test
    fun testMultiLineWithUniformIndent() {
        val rawEmbedContent = """|    {
                                 |      "key": "value"
                                 |    }""".trimMargin()
        // Processed (indent trimmed):
        val processed = """|{
                           |  "key": "value"
                           |}""".trimMargin()

        val baseLocation = Location(
            Coordinates(10, 0),
            Coordinates(12, 5),
            100,
            100 + rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Processed line 1, columns 2-7: "key"
        val result = transformer.mapToOriginal(1, 2, 1, 7)

        // In rawEmbedContent, this is line 11 (second line), columns 6-11 (after 4 spaces indent)
        assertEquals(Coordinates(11, 6), result.start)
        assertEquals(Coordinates(11, 11), result.end)
    }

    @Test
    fun testMultiLineWithEscapes() {
        val rawEmbedContent = "    line 1\n    line %\\% 2\n    line 3"
        val processed = "line 1\nline %% 2\nline 3"

        val baseLocation = Location(
            Coordinates(5, 0),
            Coordinates(7, 10),
            50,
            50 + rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Position at "%%" in processed (line 1, columns 5-7)
        val result = transformer.mapToOriginal(1, 5, 1, 7)

        // Line 1 of `processed` is line 6 of `baseLocation`
        // The "%\%" starts at position 9 in `baseLocation` and ends three characters later at 12 accounting for
        // the escape slash
        assertEquals(Coordinates(6, 9), result.start)
        assertEquals(Coordinates(6, 12), result.end)
    }

    @Test
    fun testOffsetBasedAPI() {
        val rawEmbedContent = "  hello"
        val processed = "hello"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, 7),
            0,
            7
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map offset 0-5 in processed
        val result = transformer.mapToOriginal(0, 5)

        // Should map to offset 2-7 in rawEmbedContent (accounting for indent)
        assertEquals(Coordinates(0, 2), result.start)
        assertEquals(Coordinates(0, 7), result.end)
        assertEquals(2, result.startOffset)
        assertEquals(7, result.endOffset)
    }

    @Test
    fun testLineColumnBasedAPI() {
        val rawEmbedContent = "  line 1\n  line 2"
        val processed = "line 1\nline 2"
        val baseLocation = Location(
            Coordinates(10, 0),
            Coordinates(11, 8),
            100,
            118
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map line 1, columns 0-4 in processed ("line")
        val result = transformer.mapToOriginal(
            startLine = 1,
            startColumn = 0,
            endLine = 1,
            endColumn = 4
        )

        // Should map to line 11, columns 2-6 in original
        assertEquals(Coordinates(11, 2), result.start)
        assertEquals(Coordinates(11, 6), result.end)
    }

    @Test
    fun testEmptyContent() {
        val rawEmbedContent = ""
        val processed = ""
        val baseLocation = Location(
            Coordinates(5, 10),
            Coordinates(5, 10),
            100,
            100
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map zero-length range at start
        val result = transformer.mapToOriginal(0, 0)

        assertEquals(Coordinates(5, 10), result.start)
        assertEquals(Coordinates(5, 10), result.end)
        assertEquals(100, result.startOffset)
        assertEquals(100, result.endOffset)
    }

    @Test
    fun testMultipleEscapesOnSameLine() {
        val rawEmbedContent = """a%\%b%\%c"""
        val processed = "a%%b%%c"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawEmbedContent.length),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "%%b%%" in processed (offsets 1-7)
        val result = transformer.mapToOriginal(1, 7)

        // In rawEmbedContent: "%\%b%\%" (offsets 1-9)
        assertEquals(Coordinates(0, 1), result.start)
        assertEquals(Coordinates(0, 9), result.end)
        assertEquals(1, result.startOffset)
        assertEquals(9, result.endOffset)
    }

    @Test
    fun testEscapeAtStartOfContent() {
        val rawEmbedContent = """%\%hello"""
        val processed = "%%hello"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawEmbedContent.length),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "%%" at start (offsets 0-2)
        val result = transformer.mapToOriginal(0, 2)

        // In rawEmbedContent: "%\%" (offsets 0-3)
        assertEquals(Coordinates(0, 0), result.start)
        assertEquals(Coordinates(0, 3), result.end)
    }

    @Test
    fun testCombinedEscapesAndIndent() {
        val rawEmbedContent = """    a%\%b"""
        val processed = "a%%b"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawEmbedContent.length),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "a%%" in processed (offsets 0-3)
        val result = transformer.mapToOriginal(0, 3)

        // In rawEmbedContent: "    a%\%" (offsets 4-9)
        assertEquals(Coordinates(0, 4), result.start)
        assertEquals(Coordinates(0, 8), result.end)
    }

    @Test
    fun testMultiLineVaryingIndent() {
        val rawEmbedContent = "  line1\n    line2\n  line3"
        val processed = "line1\n  line2\nline3"

        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(2, 8),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "line2" on second line in processed (line 1, columns 2-7)
        val result = transformer.mapToOriginal(1, 2, 1, 7)

        assertEquals(Coordinates(1, 4), result.start)
        assertEquals(Coordinates(1, 9), result.end)
    }

    @Test
    fun testZeroLengthRange() {
        val rawEmbedContent = "  hello"
        val processed = "hello"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, 7),
            0,
            7
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Cursor at position 3 in processed
        val result = transformer.mapToOriginal(3, 3)

        // Should map to position 5 in rawEmbedContent (3 + 2 indent)
        assertEquals(Coordinates(0, 5), result.start)
        assertEquals(Coordinates(0, 5), result.end)
        assertEquals(5, result.startOffset)
        assertEquals(5, result.endOffset)
    }

    @Test
    fun testDoubleEscapedDelimiter() {
        val rawEmbedContent = "%\\\\\\%"
        val processed = "%\\\\%"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawEmbedContent.length),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map the whole processed content
        val result = transformer.mapToOriginal(0, processed.length)

        assertEquals(Coordinates(0, 0), result.start)
        assertEquals(Coordinates(0, rawEmbedContent.length), result.end)
    }

    @Test
    fun testCloseDelimOnOwnLineStripsTrailingNewline() {
        // Raw content ends with \n followed by whitespace only (close delim on own line)
        val rawEmbedContent = "    hello\n    "
        val processed = "hello"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(1, 4),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)
    }

    @Test
    fun testCloseDelimNotOnOwnLinePreservesContent() {
        // Raw content does not end with \n — close delim is inline
        val rawEmbedContent = "    hello"
        val processed = "hello"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawEmbedContent.length),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)
    }

    @Test
    fun testCloseDelimInlineAfterNewlinePreservesContent() {
        // Raw content has newlines, but non-whitespace after the last \n — close delim is inline
        val rawEmbedContent = "  hello\n  world"
        val processed = "hello\nworld"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(1, 7),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)
    }

    @Test
    fun testCloseDelimOnOwnLineWithBlankLinePreservesNewline() {
        // Raw content has a blank indented line before close delim line
        // Content should preserve the inner \n
        val rawEmbedContent = "    hello\n    \n    "
        val processed = "hello\n"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(2, 4),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)
    }

    @Test
    fun testTrailingSpacesContent() {
        // The key fix: a line of spaces followed by close delim on own line
        // produces just the spaces as content
        val rawEmbedContent = "      \n  "
        val processed = "    "
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(1, 2),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)
    }

    @Test
    fun testCloseDelimOnOwnLineWithTabsAndCR() {
        // Close delim on own line with mixed whitespace (tabs, CR)
        val rawEmbedContent = "  hello\n  \t\r"
        val processed = "hello"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(1, 3),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)
    }

    @Test
    fun testSourceMappingWithTrailingNewlineStripping() {
        // Verify source mapping still works when trailing newline is stripped
        val rawEmbedContent = "    hello\n    "
        val processed = "hello"
        val baseLocation = Location(
            Coordinates(5, 0),
            Coordinates(6, 4),
            50,
            50 + rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "ello" in processed (offsets 1-5)
        val result = transformer.mapToOriginal(1, 5)

        // In rawEmbedContent: "    hello" → "ello" starts at offset 5
        assertEquals(Coordinates(5, 5), result.start)
        assertEquals(Coordinates(5, 9), result.end)
        assertEquals(55, result.startOffset)
        assertEquals(59, result.endOffset)
    }

    @Test
    fun testSourceMappingMultiLineWithTrailingNewlineStripping() {
        // Multi-line content with trailing newline stripped — verify mapping across line boundary
        val rawEmbedContent = "  line1\n  line2\n  "
        val processed = "line1\nline2"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(2, 2),
            0,
            rawEmbedContent.length
        )

        val transformer = EmbedContentTransformer(
            rawContent = rawEmbedContent,
            embedDelim = EmbedDelim.Percent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "line2" in processed (offsets 6-11, on second line)
        val result = transformer.mapToOriginal(6, 11)

        // In rawEmbedContent: "  line2" starts at offset 10 (after "  line1\n  ")
        assertEquals(Coordinates(1, 2), result.start)
        assertEquals(Coordinates(1, 7), result.end)
        assertEquals(10, result.startOffset)
        assertEquals(15, result.endOffset)
    }
}

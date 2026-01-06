package org.kson.parser.behavior.quotedstring

import org.kson.parser.Coordinates
import org.kson.parser.Location
import kotlin.test.Test
import kotlin.test.assertEquals

class QuotedStringContentTransformerTest {

    @Test
    fun testSimpleSingleLineNoEscapes() {
        val rawQuotedContent = "hello world"
        val baseLocation = Location(
            Coordinates(10, 5),
            Coordinates(10, 16),
            100,
            111
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        // Verify no transformation occurred
        assertEquals("hello world", transformer.processedContent)

        // Position at "hello| |world" (offset 5-6)
        val result = transformer.mapToOriginal(5, 6)

        // Maps to offset 10-11 on line 10 of `baseLocation`
        assertEquals(Coordinates(10, 10), result.start)
        assertEquals(Coordinates(10, 11), result.end)
        assertEquals(105, result.startOffset)
        assertEquals(106, result.endOffset)
    }

    @Test
    fun testSingleLineWithSimpleEscape() {
        val rawQuotedContent = """hello\nworld"""
        val processed = "hello\nworld"
        val baseLocation = Location(
            Coordinates(5, 0),
            Coordinates(5, rawQuotedContent.length),
            50,
            50 + rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Position at the newline character in processed (offset 5-6)
        val result = transformer.mapToOriginal(5, 6)

        // In rawQuotedContent, this should map to "\n" (offsets 5-7)
        assertEquals(Coordinates(5, 5), result.start)
        assertEquals(Coordinates(5, 7), result.end)
        assertEquals(55, result.startOffset)
        assertEquals(57, result.endOffset)
    }

    @Test
    fun testMultipleEscapesOnSameLine() {
        val rawQuotedContent = """tab\there\tand\tthere"""
        val processed = "tab\there\tand\tthere"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "and" in processed (offsets 9-12)
        // In raw: "tab\there\t|and|\tthere"
        val result = transformer.mapToOriginal(9, 12)

        // Positions shift by two in the raw due to the escapes
        assertEquals(Coordinates(0, 11), result.start)
        assertEquals(Coordinates(0, 14), result.end)
    }

    @Test
    fun testEscapedQuotes() {
        val rawQuotedContent = """say \"hello\""""
        val processed = """say "hello""""
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map the word "hello" in processed (offsets 5-10)
        val result = transformer.mapToOriginal(5, 10)

        // In raw: say \"|hello|\"
        // Positions: 0-3 (say ) + 4-5 (\") + 6-10 (hello)
        assertEquals(Coordinates(0, 6), result.start)
        assertEquals(Coordinates(0, 11), result.end)
    }

    @Test
    fun testEscapedBackslash() {
        val rawQuotedContent = """path\\to\\file"""
        val processed = """path\to\file"""
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "to" in processed (offsets 5-7)
        val result = transformer.mapToOriginal(5, 7)

        // In raw: path\\|to|\\file
        // Positions: 0-3 (path) + 4-5 (\\) + 6-7 (to)
        assertEquals(Coordinates(0, 6), result.start)
        assertEquals(Coordinates(0, 8), result.end)
    }

    @Test
    fun testUnicodeEscape() {
        val rawQuotedContent = """A\u0041B"""
        val processed = "AAB"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map the escaped 'A' in processed (offset 1-2)
        val result = transformer.mapToOriginal(1, 2)

        // In raw: A|\u0041|B (positions 1-7)
        assertEquals(Coordinates(0, 1), result.start)
        assertEquals(Coordinates(0, 7), result.end)
    }

    @Test
    fun testSurrogatePair() {
        // \uD83D\uDE00 is the surrogate pair for the grinning face emoji 😀
        val rawQuotedContent = """\uD83D\uDE00"""
        val processed = "\uD83D\uDE00"  // The actual emoji
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map the emoji in processed (offsets 0-2, as it's 2 chars in Kotlin)
        val result = transformer.mapToOriginal(0, 2)

        // Should map to the entire escape sequence (12 chars: \uD83D\uDE00)
        assertEquals(Coordinates(0, 0), result.start)
        assertEquals(Coordinates(0, 12), result.end)
    }

    @Test
    fun testMultiLineWithRawWhitespace() {
        val rawQuotedContent = "line 1\nline 2\nline 3"
        val processed = "line 1\nline 2\nline 3"
        val baseLocation = Location(
            Coordinates(10, 0),
            Coordinates(12, 6),
            100,
            120
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        // No transformation for raw whitespace
        assertEquals(processed, transformer.processedContent)

        // Map "line 2" on the second line (line 1, columns 0-6 in processed)
        val result = transformer.mapToOriginal(1, 0, 1, 6)

        // Should map to line 11, columns 0-6
        assertEquals(Coordinates(11, 0), result.start)
        assertEquals(Coordinates(11, 6), result.end)
    }

    @Test
    fun testMultiLineWithEscapedWhitespace() {
        val rawQuotedContent = """line 1\nline 2"""
        val processed = "line 1\nline 2"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "line 2" in processed (line 1, columns 0-6)
        val result = transformer.mapToOriginal(1, 0, 1, 6)

        // In raw, this is still on line 0, starting after "\n"
        // "line 1\n|line 2|"
        // Positions: 0-5 (line 1) + 6-7 (\n) + 8-13 (line 2)
        assertEquals(Coordinates(0, 8), result.start)
        assertEquals(Coordinates(0, 14), result.end)
    }

    @Test
    fun testMixedRawAndEscapedNewlines() {
        val rawQuotedContent = "line 1\nline 2\\nline 3"
        val processed = "line 1\nline 2\nline 3"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(1, 13),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "line 3" in processed (line 2, columns 0-6)
        val result = transformer.mapToOriginal(2, 0, 2, 6)

        // In raw: "line 1\nline 2\n|line 3|"
        // The second newline is escaped, so it's on the same line in raw
        assertEquals(Coordinates(1, 8), result.start)
        assertEquals(Coordinates(1, 14), result.end)
    }

    @Test
    fun testOffsetBasedAPI() {
        val rawQuotedContent = """hello\tworld"""
        val processed = "hello\tworld"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map offset 0-5 in processed ("hello")
        val result = transformer.mapToOriginal(0, 5)

        assertEquals(Coordinates(0, 0), result.start)
        assertEquals(Coordinates(0, 5), result.end)
        assertEquals(0, result.startOffset)
        assertEquals(5, result.endOffset)
    }

    @Test
    fun testLineColumnBasedAPI() {
        val rawQuotedContent = "line 1\nline 2"
        val processed = "line 1\nline 2"
        val baseLocation = Location(
            Coordinates(10, 0),
            Coordinates(11, 6),
            100,
            113
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
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

        // Should map to line 11, columns 0-4 in original
        assertEquals(Coordinates(11, 0), result.start)
        assertEquals(Coordinates(11, 4), result.end)
    }

    @Test
    fun testEmptyContent() {
        val rawQuotedContent = ""
        val processed = ""
        val baseLocation = Location(
            Coordinates(5, 10),
            Coordinates(5, 10),
            100,
            100
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
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
    fun testEscapeAtStartOfContent() {
        val rawQuotedContent = """\nhello"""
        val processed = "\nhello"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "\n" at start (offsets 0-1)
        val result = transformer.mapToOriginal(0, 1)

        // In rawQuotedContent: "\n" (offsets 0-2)
        assertEquals(Coordinates(0, 0), result.start)
        assertEquals(Coordinates(0, 2), result.end)
    }

    @Test
    fun testAllCommonEscapes() {
        val rawQuotedContent = """\\\/\b\f\n\r\t\"""" + """\'"""
        val processed = "\\/\b\u000C\n\r\t\"'"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map the entire processed content
        val result = transformer.mapToOriginal(0, processed.length)

        assertEquals(Coordinates(0, 0), result.start)
        assertEquals(Coordinates(0, rawQuotedContent.length), result.end)
    }

    @Test
    fun testZeroLengthRange() {
        val rawQuotedContent = """hello\nworld"""
        val processed = "hello\nworld"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Cursor at the newline position in processed (offset 5)
        val result = transformer.mapToOriginal(5, 5)

        // Should map to position 5 in rawQuotedContent (start of \n)
        assertEquals(Coordinates(0, 5), result.start)
        assertEquals(Coordinates(0, 5), result.end)
        assertEquals(5, result.startOffset)
        assertEquals(5, result.endOffset)
    }

    @Test
    fun testComplexMixedContent() {
        val rawQuotedContent = """Hello\n\tWorld!\nThis is a "test" with 'quotes' and unicode: \u0041"""
        val processed = "Hello\n\tWorld!\nThis is a \"test\" with 'quotes' and unicode: A"
        val baseLocation = Location(
            Coordinates(0, 0),
            Coordinates(0, rawQuotedContent.length),
            0,
            rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "test" in processed
        // In processed: after "This is a \""
        val testStart = processed.indexOf("test")
        val testEnd = testStart + 4
        val result = transformer.mapToOriginal(testStart, testEnd)

        // Verify the mapping makes sense (should be after the escaped quote)
        val rawTestStart = rawQuotedContent.indexOf("test")
        assertEquals(rawTestStart, result.start.column)
    }

    @Test
    fun testMultilineSelectionInComplexMixedContent() {
        val rawQuotedContent = "Hello\n\tWorld!\nThis is a \"test\" with 'quotes' and unicode: \\u0041"
        val processed = "Hello\n\tWorld!\nThis is a \"test\" with 'quotes' and unicode: A"
        val baseLocation = Location(
            Coordinates(4, 0),
            Coordinates(4, rawQuotedContent.length),
            5,
            5 + rawQuotedContent.length
        )

        val transformer = QuotedStringContentTransformer(
            rawContent = rawQuotedContent,
            rawLocation = baseLocation
        )

        assertEquals(processed, transformer.processedContent)

        // Map "\tWorld!\nThis is a "test" with 'quotes' and unicode: A" in processed
        val result = transformer.mapToOriginal(6, 59)

        // Raw text for this test goes from position 6 to 64 in rawQuotedContent plus the baseLocation start of 5
        assertEquals(11, result.startOffset)
        assertEquals(69, result.endOffset)

        assertEquals(5, result.start.line)
        assertEquals(0, result.start.column)
        assertEquals(6, result.end.line)
        assertEquals(50, result.end.column)
    }
}

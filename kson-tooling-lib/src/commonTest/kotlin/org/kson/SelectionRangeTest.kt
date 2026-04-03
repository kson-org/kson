package org.kson

import kotlin.test.*

class SelectionRangeTest {

    @Test
    fun testEmptyDocumentReturnsDocumentRange() {
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse(""), 0, 0)
        // Even an empty document returns the full-document range
        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(0, ranges[0].startColumn)
        assertEquals(0, ranges[0].endLine)
        assertEquals(0, ranges[0].endColumn)
    }

    @Test
    fun testSimpleStringValue() {
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse("\"hello\""), 0, 2)
        assertEquals(2, ranges.size)
        assertEquals(tooling.Range(0, 1, 0, 6), ranges[0])  // string content
        assertEquals(tooling.Range(0, 0, 0, 7), ranges[1])   // document range
    }

    @Test
    fun testObjectPropertyValue() {
        val content = """
            {
              name: "Alice"
              age: 30
            }
        """.trimIndent()

        // Cursor on "Alice" string value (line 1, inside the string)
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse(content), 1, 10)

        assertEquals(3, ranges.size)
        assertEquals(tooling.Range(1, 9, 1, 14), ranges[0])  // string "Alice"
        assertEquals(tooling.Range(1, 2, 1, 14), ranges[1])   // property name: "Alice"
        assertEquals(tooling.Range(0, 0, 3, 1), ranges[2])    // object
    }

    @Test
    fun testCursorOnPropertyKey() {
        val content = """
            {
              name: "Alice"
            }
        """.trimIndent()

        // Cursor on "name" key (line 1, character 3)
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse(content), 1, 3)

        assertEquals(3, ranges.size)
        assertEquals(tooling.Range(1, 2, 1, 6), ranges[0])   // key "name"
        assertEquals(tooling.Range(1, 2, 1, 14), ranges[1])   // property name: "Alice"
        assertEquals(tooling.Range(0, 0, 2, 1), ranges[2])    // object
    }

    @Test
    fun testNestedObjects() {
        val content = """
            {
              person: {
                name: "Alice"
              }
            }
        """.trimIndent()

        // Cursor on "Alice" (line 2, character 12)
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse(content), 2, 12)

        assertEquals(5, ranges.size)
        assertEquals(tooling.Range(2, 11, 2, 16), ranges[0])  // string "Alice"
        assertEquals(tooling.Range(2, 4, 2, 16), ranges[1])    // property name: "Alice"
        assertEquals(tooling.Range(1, 10, 3, 3), ranges[2])    // inner object
        assertEquals(tooling.Range(1, 2, 3, 3), ranges[3])     // property person: {...}
        assertEquals(tooling.Range(0, 0, 4, 1), ranges[4])     // outer object (= document range after dedup)
    }

    @Test
    fun testArrayElements() {
        val content = """
            [
              "one"
              "two"
              "three"
            ]
        """.trimIndent()

        // Cursor on "two" (line 2, character 3)
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse(content), 2, 3)

        assertEquals(2, ranges.size)
        assertEquals(tooling.Range(2, 3, 2, 6), ranges[0])   // string "two"
        assertEquals(tooling.Range(0, 0, 4, 1), ranges[1])    // array
    }

    @Test
    fun testCursorOnContainerDelimiter() {
        val content = """
            {
              name: "Alice"
            }
        """.trimIndent()

        // Cursor on opening brace (line 0, character 0)
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse(content), 0, 0)

        assertEquals(1, ranges.size)
        assertEquals(tooling.Range(0, 0, 2, 1), ranges[0])
    }

    @Test
    fun testStrictlyExpandingChain() {
        val content = """
            {
              items: [
                "hello"
              ]
            }
        """.trimIndent()

        // Cursor on "hello" (line 2, character 6)
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse(content), 2, 6)

        // Verify each range is contained within the next
        for (i in 0 until ranges.size - 1) {
            val inner = ranges[i]
            val outer = ranges[i + 1]

            val innerStartsBefore = inner.startLine < outer.startLine ||
                (inner.startLine == outer.startLine && inner.startColumn < outer.startColumn)
            val innerEndsAfter = inner.endLine > outer.endLine ||
                (inner.endLine == outer.endLine && inner.endColumn > outer.endColumn)

            assertFalse(innerStartsBefore,
                "Level $i starts before level ${i + 1}: (${inner.startLine}:${inner.startColumn}) vs (${outer.startLine}:${outer.startColumn})")
            assertFalse(innerEndsAfter,
                "Level $i ends after level ${i + 1}: (${inner.endLine}:${inner.endColumn}) vs (${outer.endLine}:${outer.endColumn})")
        }
    }

    @Test
    fun testDeduplication() {
        // A bare string "hello" — the string range and document range differ,
        // so we get 2 entries (dedup only removes adjacent identical ranges)
        val ranges = tooling.KsonTooling.getEnclosingRanges(tooling.KsonTooling.parse("\"hello\""), 0, 2)
        assertEquals(2, ranges.size)

        // Verify no adjacent ranges are identical
        for (i in 0 until ranges.size - 1) {
            assertNotEquals(ranges[i], ranges[i + 1], "Adjacent ranges at index $i and ${i + 1} should not be identical")
        }
    }
}

package org.kson

import org.kson.tooling.KsonTooling
import org.kson.tooling.StructuralRangeKind
import kotlin.test.*

class FoldingRangeTest {

    @Test
    fun testSingleLineDocumentHasNoRanges() {
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse("key: value"))
        assertEquals(0, ranges.size)
    }

    @Test
    fun testMultiLineObject() {
        val content = """
            {
              name: "Alice"
              age: 30
            }
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(3, ranges[0].endLine)
        assertEquals(StructuralRangeKind.OBJECT, ranges[0].kind)
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
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        assertEquals(2, ranges.size)
        // Inner object folds from line 1 to line 3
        val innerRange = ranges.find { it.startLine == 1 }
        assertNotNull(innerRange)
        assertEquals(3, innerRange.endLine)
        assertEquals(StructuralRangeKind.OBJECT, innerRange.kind)
        // Outer object folds from line 0 to line 4
        val outerRange = ranges.find { it.startLine == 0 }
        assertNotNull(outerRange)
        assertEquals(4, outerRange.endLine)
        assertEquals(StructuralRangeKind.OBJECT, outerRange.kind)
    }

    @Test
    fun testMultiLineArray() {
        val content = """
            [
              1
              2
              3
            ]
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(4, ranges[0].endLine)
        assertEquals(StructuralRangeKind.ARRAY, ranges[0].kind)
    }

    @Test
    fun testEmbedBlock() {
        val content = """
            query: ${'$'}sql
              SELECT *
              FROM users
              ${'$'}${'$'}
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(3, ranges[0].endLine)
        assertEquals(StructuralRangeKind.EMBED, ranges[0].kind)
    }

    @Test
    fun testSingleLineObjectHasNoRanges() {
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse("{ name: \"Alice\", age: 30 }"))
        assertEquals(0, ranges.size)
    }

    @Test
    fun testEmptyDocument() {
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(""))
        assertEquals(0, ranges.size)
    }

    @Test
    fun testMixedFoldableRegions() {
        val content = """
            {
              items: [
                "one"
                "two"
              ]
              code: ${'$'}js
                console.log("hi")
                ${'$'}${'$'}
            }
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        assertEquals(3, ranges.size)

        val arrayRange = ranges.find { it.kind == StructuralRangeKind.ARRAY }
        assertNotNull(arrayRange)
        assertEquals(1, arrayRange.startLine)
        assertEquals(4, arrayRange.endLine)

        val embedRange = ranges.find { it.kind == StructuralRangeKind.EMBED }
        assertNotNull(embedRange)
        assertEquals(5, embedRange.startLine)
        assertEquals(7, embedRange.endLine)

        val objectRange = ranges.find { it.kind == StructuralRangeKind.OBJECT }
        assertNotNull(objectRange)
        assertEquals(0, objectRange.startLine)
        assertEquals(8, objectRange.endLine)
    }
}

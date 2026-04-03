package org.kson

import kotlin.test.*

class FoldingRangeTest {

    @Test
    fun testSingleLineDocumentHasNoRanges() {
        val ranges = tooling.KsonTooling.getStructuralRanges(tooling.KsonTooling.parse("key: value"))
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
        val ranges = tooling.KsonTooling.getStructuralRanges(tooling.KsonTooling.parse(content))

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(3, ranges[0].endLine)
        assertEquals(tooling.StructuralRangeKind.OBJECT, ranges[0].kind)
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
        val ranges = tooling.KsonTooling.getStructuralRanges(tooling.KsonTooling.parse(content))

        assertEquals(2, ranges.size)
        // Inner object folds from line 1 to line 3
        val innerRange = ranges.find { it.startLine == 1 }
        assertNotNull(innerRange)
        assertEquals(3, innerRange.endLine)
        assertEquals(tooling.StructuralRangeKind.OBJECT, innerRange.kind)
        // Outer object folds from line 0 to line 4
        val outerRange = ranges.find { it.startLine == 0 }
        assertNotNull(outerRange)
        assertEquals(4, outerRange.endLine)
        assertEquals(tooling.StructuralRangeKind.OBJECT, outerRange.kind)
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
        val ranges = tooling.KsonTooling.getStructuralRanges(tooling.KsonTooling.parse(content))

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(4, ranges[0].endLine)
        assertEquals(tooling.StructuralRangeKind.ARRAY, ranges[0].kind)
    }

    @Test
    fun testEmbedBlock() {
        val content = """
            query: ${'$'}sql
              SELECT *
              FROM users
              ${'$'}${'$'}
        """.trimIndent()
        val ranges = tooling.KsonTooling.getStructuralRanges(tooling.KsonTooling.parse(content))

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(3, ranges[0].endLine)
        assertEquals(tooling.StructuralRangeKind.EMBED, ranges[0].kind)
    }

    @Test
    fun testSingleLineObjectHasNoRanges() {
        val ranges = tooling.KsonTooling.getStructuralRanges(tooling.KsonTooling.parse("{ name: \"Alice\", age: 30 }"))
        assertEquals(0, ranges.size)
    }

    @Test
    fun testEmptyDocument() {
        val ranges = tooling.KsonTooling.getStructuralRanges(tooling.KsonTooling.parse(""))
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
        val ranges = tooling.KsonTooling.getStructuralRanges(tooling.KsonTooling.parse(content))

        assertEquals(3, ranges.size)

        val arrayRange = ranges.find { it.kind == tooling.StructuralRangeKind.ARRAY }
        assertNotNull(arrayRange)
        assertEquals(1, arrayRange.startLine)
        assertEquals(4, arrayRange.endLine)

        val embedRange = ranges.find { it.kind == tooling.StructuralRangeKind.EMBED }
        assertNotNull(embedRange)
        assertEquals(5, embedRange.startLine)
        assertEquals(7, embedRange.endLine)

        val objectRange = ranges.find { it.kind == tooling.StructuralRangeKind.OBJECT }
        assertNotNull(objectRange)
        assertEquals(0, objectRange.startLine)
        assertEquals(8, objectRange.endLine)
    }
}

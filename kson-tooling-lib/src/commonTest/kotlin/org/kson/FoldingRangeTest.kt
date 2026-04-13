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
    fun testMultiLineDelimitedObject() {
        val content = """
            {
              name: "Alice"
              age: 30
            }
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val objectRange = ranges.single { it.kind == StructuralRangeKind.OBJECT }
        assertEquals(0, objectRange.startLine)
        assertEquals(3, objectRange.endLine)
    }

    @Test
    fun testNestedDelimitedObjects() {
        val content = """
            {
              person: {
                name: "Alice"
              }
            }
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val outerObject = ranges.single { it.kind == StructuralRangeKind.OBJECT && it.startLine == 0 }
        assertEquals(4, outerObject.endLine)

        val innerObject = ranges.single { it.kind == StructuralRangeKind.OBJECT && it.startLine == 1 }
        assertEquals(3, innerObject.endLine)

        val property = ranges.single { it.kind == StructuralRangeKind.PROPERTY }
        assertEquals(1, property.startLine)
        assertEquals(3, property.endLine)
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

        val arrayRange = ranges.single { it.kind == StructuralRangeKind.ARRAY }
        assertEquals(0, arrayRange.startLine)
        assertEquals(4, arrayRange.endLine)
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

        val embedRange = ranges.single { it.kind == StructuralRangeKind.EMBED }
        assertEquals(0, embedRange.startLine)
        assertEquals(3, embedRange.endLine)
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

        val embedRange = ranges.single { it.kind == StructuralRangeKind.EMBED }
        assertEquals(5, embedRange.startLine)
        assertEquals(7, embedRange.endLine)

        val objectRange = ranges.single { it.kind == StructuralRangeKind.OBJECT }
        assertEquals(0, objectRange.startLine)
        assertEquals(8, objectRange.endLine)

        // items: and code: get PROPERTY folds; the bracket list inside items:
        // does NOT get a separate ARRAY fold since the PROPERTY fold covers it
        val properties = ranges.filter { it.kind == StructuralRangeKind.PROPERTY }
        assertEquals(2, properties.size)
        assertTrue(ranges.none { it.kind == StructuralRangeKind.ARRAY })
    }

    @Test
    fun testDashList() {
        val content = """
            - one
            - two
            - three
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val listRange = ranges.single { it.kind == StructuralRangeKind.ARRAY }
        assertEquals(0, listRange.startLine)
        assertEquals(2, listRange.endLine)
    }

    @Test
    fun testAngleBracketList() {
        val content = """
            items: <
              - one
              - two
            >
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        // Angle-bracket list is a property value — the PROPERTY fold covers it
        assertTrue(ranges.none { it.kind == StructuralRangeKind.ARRAY })
        val property = ranges.single { it.kind == StructuralRangeKind.PROPERTY }
        assertEquals(0, property.startLine)
        assertEquals(3, property.endLine)
    }

    @Test
    fun testMultiLineObjectProperty() {
        val content = """
            person:
              name: "Alice"
              age: 30
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val propertyRange = ranges.single { it.kind == StructuralRangeKind.PROPERTY }
        assertEquals(0, propertyRange.startLine)
        assertEquals(2, propertyRange.endLine)
    }

    @Test
    fun testSingleLinePropertyDoesNotFold() {
        val content = "name: \"Alice\""
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val properties = ranges.filter { it.kind == StructuralRangeKind.PROPERTY }
        assertEquals(0, properties.size)
    }

    @Test
    fun testCommentBlock() {
        val content = """
            # This is a comment
            # that spans multiple lines
            name: "Alice"
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val commentRange = ranges.single { it.kind == StructuralRangeKind.COMMENT }
        assertEquals(0, commentRange.startLine)
        assertEquals(1, commentRange.endLine)
    }

    @Test
    fun testSingleCommentLineDoesNotFold() {
        val content = """
            # Just one comment
            name: "Alice"
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val commentRanges = ranges.filter { it.kind == StructuralRangeKind.COMMENT }
        assertEquals(0, commentRanges.size)
    }

    @Test
    fun testMultipleCommentBlocks() {
        val content = """
            # Block one
            # continues here
            name: "Alice"
            # Block two
            # also continues
            age: 30
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val commentRanges = ranges.filter { it.kind == StructuralRangeKind.COMMENT }
        assertEquals(2, commentRanges.size)
        assertEquals(0, commentRanges[0].startLine)
        assertEquals(1, commentRanges[0].endLine)
        assertEquals(3, commentRanges[1].startLine)
        assertEquals(4, commentRanges[1].endLine)
    }

    @Test
    fun testCommentBlocksSeparatedByBlankLine() {
        val content = """
            # Block one
            # continues here

            # Block two
            # also continues
            name: "Alice"
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        val commentRanges = ranges.filter { it.kind == StructuralRangeKind.COMMENT }
        assertEquals(2, commentRanges.size)
        assertEquals(0, commentRanges[0].startLine)
        assertEquals(1, commentRanges[0].endLine)
        assertEquals(3, commentRanges[1].startLine)
        assertEquals(4, commentRanges[1].endLine)
    }

    @Test
    fun testNestedDashListWithObjects() {
        val content = """
            items:
              - name: "Alice"
                age: 30
              - name: "Bob"
                age: 25
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        // The dash list is a property value, so no separate ARRAY fold —
        // the PROPERTY fold for items: covers it
        assertTrue(ranges.none { it.kind == StructuralRangeKind.ARRAY })
        val property = ranges.single { it.kind == StructuralRangeKind.PROPERTY && it.startLine == 0 }
        assertEquals(4, property.endLine)
    }

    @Test
    fun testEmbedInDashListFoldsIndependently() {
        val content = """
            key: value
            list:
              - this: key: %typescript
                  This is typescript
                  %%
              - 2
              - 3
        """.trimIndent()
        val ranges = KsonTooling.getStructuralRanges(KsonTooling.parse(content))

        // Folding at line 2 should only fold the embed (lines 3-4),
        // not the entire list — "- 2" and "- 3" stay visible
        val embedRange = ranges.single { it.kind == StructuralRangeKind.EMBED }
        assertEquals(2, embedRange.startLine)
        assertEquals(4, embedRange.endLine)

        // No ARRAY fold at line 2 — the PROPERTY fold for list: handles the list
        assertTrue(ranges.none { it.kind == StructuralRangeKind.ARRAY })
    }
}

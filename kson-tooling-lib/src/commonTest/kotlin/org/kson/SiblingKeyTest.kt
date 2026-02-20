package org.kson

import kotlin.test.*

class SiblingKeyTest {

    @Test
    fun testHighlightsSiblingPropertyKeys() {
        val content = """
            name: John
            age: 30
            city: "New York"
        """.trimIndent()

        // Cursor on "name" key
        val ranges = KsonTooling.getSiblingKeys(content, 0, 3)

        assertEquals(3, ranges.size)
        // Check that all three keys are returned
        assertContainsRange(ranges, 0, 0, 0, 4)  // "name"
        assertContainsRange(ranges, 1, 0, 1, 3)  // "age"
        assertContainsRange(ranges, 2, 0, 2, 4)  // "city"
    }

    @Test
    fun testEmptyWhenCursorOnObjectItself() {
        val content = """
            {
            name: John
            age: 30
            }
        """.trimIndent()

        // Cursor after opening brace
        val ranges = KsonTooling.getSiblingKeys(content, 0, 1)
        assertEquals(0, ranges.size)
    }

    @Test
    fun testEmptyWhenCursorOnPropertyValue() {
        val content = """
            name: John
            age: 30
        """.trimIndent()

        // Cursor on "30" value
        val ranges = KsonTooling.getSiblingKeys(content, 1, 7)
        assertEquals(0, ranges.size)
    }

    @Test
    fun testOnlySiblingKeysAtSameLevel() {
        val content = """
            outer: value
            nested: {
              inner1: value1
              inner2: value2
            }
        """.trimIndent()

        // Cursor on "inner2" key
        val ranges = KsonTooling.getSiblingKeys(content, 3, 4)

        assertEquals(2, ranges.size)
        // Should highlight inner1 and inner2, not outer and nested
        assertContainsRange(ranges, 2, 2, 2, 8)  // "inner1"
        assertContainsRange(ranges, 3, 2, 3, 8)  // "inner2"
    }

    @Test
    fun testOuterKeysWhenCursorOnOuterKey() {
        val content = """
            outer: value
            nested:
              inner: value
        """.trimIndent()

        // Cursor on "nested" key
        val ranges = KsonTooling.getSiblingKeys(content, 1, 1)

        assertEquals(2, ranges.size)
        // Should highlight outer and nested, not inner
        assertContainsRange(ranges, 0, 0, 0, 5)  // "outer"
        assertContainsRange(ranges, 1, 0, 1, 6)  // "nested"
    }

    @Test
    fun testDeeplyNestedStructures() {
        val content = """
            {
                "level1": {
                    "level2": {
                        "prop1": "value1",
                        "prop2": "value2",
                        "prop3": "value3"
                    }
                }
            }
        """.trimIndent()

        // Cursor on "prop2" key
        val ranges = KsonTooling.getSiblingKeys(content, 4, 14)

        assertEquals(3, ranges.size)
        // Should highlight all three properties at the same level
        assertContainsRange(ranges, 3, 13, 3, 18)  // "prop1"
        assertContainsRange(ranges, 4, 13, 4, 18)  // "prop2"
        assertContainsRange(ranges, 5, 13, 5, 18)  // "prop3"
    }

    @Test
    fun testObjectKeysWithinArrayElement() {
        val content = """
            [
                {
                    "id": 1,
                    "name": "Item 1"
                },
                {
                    "id": 2,
                    "name": "Item 2"
                }
            ]
        """.trimIndent()

        // Cursor on "id" in first object
        val ranges = KsonTooling.getSiblingKeys(content, 2, 9)

        assertEquals(2, ranges.size)
        // Should highlight keys from first object only
        assertContainsRange(ranges, 2, 9, 2, 11)  // "id"
        assertContainsRange(ranges, 3, 9, 3, 13)  // "name"
    }

    @Test
    fun testNoHighlightsFromDifferentArrayObjects() {
        val content = """
            [
                {
                    "id": 1,
                    "name": "Item 1"
                },
                {
                    "id": 2,
                    "name": "Item 2"
                }
            ]
        """.trimIndent()

        // Cursor on "id" in second object
        val ranges = KsonTooling.getSiblingKeys(content, 6, 9)

        assertEquals(2, ranges.size)
        // Should highlight keys from second object only
        assertContainsRange(ranges, 6, 9, 6, 11)  // "id"
        assertContainsRange(ranges, 7, 9, 7, 13)  // "name"
    }

    @Test
    fun testPropertiesWithObjectValues() {
        val content = """
            {
                "simple": "value",
                "complex": {
                    "nested": "data"
                },
                "another": "value"
            }
        """.trimIndent()

        // Cursor on "complex" key (which has an object value)
        val ranges = KsonTooling.getSiblingKeys(content, 2, 6)

        assertEquals(3, ranges.size)
        // Should highlight all three top-level keys
        assertContainsRange(ranges, 1, 5, 1, 11)  // "simple"
        assertContainsRange(ranges, 2, 5, 2, 12)  // "complex"
        assertContainsRange(ranges, 5, 5, 5, 12)  // "another"
    }

    @Test
    fun testEmptyObject() {
        val ranges = KsonTooling.getSiblingKeys("{}", 0, 1)
        assertEquals(0, ranges.size)
    }

    @Test
    fun testCursorOutsideSymbol() {
        val content = """
            {
                "key": "value"
            }

        """.trimIndent()

        // Cursor on empty line after closing brace
        val ranges = KsonTooling.getSiblingKeys(content, 3, 0)
        assertEquals(0, ranges.size)
    }

    @Test
    fun testInvalidDocumentGraceful() {
        val ranges = KsonTooling.getSiblingKeys("{ invalid }", 0, 5)
        assertEquals(0, ranges.size)
    }

    @Test
    fun testUnquotedKeys() {
        val content = """
            {
                key1: "value1",
                key2: "value2"
            }
        """.trimIndent()

        // Cursor on unquoted key
        val ranges = KsonTooling.getSiblingKeys(content, 1, 5)

        assertEquals(2, ranges.size)
        assertContainsRange(ranges, 1, 4, 1, 8)  // key1
        assertContainsRange(ranges, 2, 4, 2, 8)  // key2
    }

    @Test
    fun testEmptyWhenCursorOnArrayElement() {
        val content = """
            {
                "items": ["a", "b", "c"]
            }
        """.trimIndent()

        // Cursor in array element "b"
        val ranges = KsonTooling.getSiblingKeys(content, 1, 20)
        assertEquals(0, ranges.size)
    }

    @Test
    fun testSinglePropertyObject() {
        val content = """
            {
                "onlyKey": "onlyValue"
            }
        """.trimIndent()

        // Cursor on the only key
        val ranges = KsonTooling.getSiblingKeys(content, 1, 6)

        assertEquals(1, ranges.size)
        assertContainsRange(ranges, 1, 5, 1, 12)  // "onlyKey"
    }

    @Test
    fun testDuplicatePropertyKeys() {
        val content = """
            {
                "value": "first",
                "value": "second",
                "value": "third",
                "other": "data"
            }
        """.trimIndent()

        // Cursor on the last "value" key
        val ranges = KsonTooling.getSiblingKeys(content, 3, 6)

        assertEquals(2, ranges.size, "KSON deduplicates keys, so only 2 unique keys remain")
    }

    @Test
    fun testEmptyDocument() {
        val ranges = KsonTooling.getSiblingKeys("", 0, 0)
        assertEquals(0, ranges.size)
    }

    private fun assertContainsRange(
        ranges: List<Range>,
        startLine: Int, startColumn: Int,
        endLine: Int, endColumn: Int
    ) {
        val found = ranges.any {
            it.startLine == startLine && it.startColumn == startColumn
                && it.endLine == endLine && it.endColumn == endColumn
        }
        assertTrue(
            found,
            "Expected range ($startLine:$startColumn-$endLine:$endColumn) not found in ${
                ranges.map { "(${it.startLine}:${it.startColumn}-${it.endLine}:${it.endColumn})" }
            }"
        )
    }
}

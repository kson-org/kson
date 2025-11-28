package org.kson

import org.kson.navigation.KsonValuePathBuilder
import org.kson.parser.Coordinates
import kotlin.test.*

/**
 * Tests for [KsonValuePathBuilder]
 */
class KsonValuePathBuilderTest {

    /**
     * Helper to build path at the <caret> position in the document
     */
    private fun buildPathAtCaret(documentWithCaret: String): List<String>? {
        val caretMarker = "<caret>"
        val caretIndex = documentWithCaret.indexOf(caretMarker)
        require(caretIndex >= 0) { "Document must contain $caretMarker marker" }

        // Calculate line and column
        val beforeCaret = documentWithCaret.take(caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)

        // Remove caret marker from document
        val document = documentWithCaret.replace(caretMarker, "")

        return KsonValuePathBuilder(document, Coordinates(line, column)).buildPathToPosition()
    }

    @Test
    fun testBuildPathToPosition_simpleProperty() {
        val path = buildPathAtCaret("""
            name: <caret>John
            age: 30
        """.trimIndent())

        assertNotNull(path)
        assertEquals(listOf("name"), path)
    }

    @Test
    fun testBuildPathToPosition_secondProperty() {
        val path = buildPathAtCaret("""
            name: John
            age: <caret>30
        """.trimIndent())

        assertNotNull(path)
        assertEquals(listOf("age"), path)
    }

    @Test
    fun testBuildPathToPosition_nestedProperty() {
        val path = buildPathAtCaret("""
            person:
              name: <caret>Alice
              age: 25
        """.trimIndent())

        assertNotNull(path)
        assertEquals(listOf("person", "name"), path)
    }

    @Test
    fun testBuildPathToPosition_deeplyNestedProperty() {
        val path = buildPathAtCaret("""
            company:
              address:
                city: <caret>Boston
        """.trimIndent())

        assertNotNull(path)
        assertEquals(listOf("company", "address", "city"), path)
    }

    @Test
    fun testBuildPathToPosition_arrayItem() {
        val path = buildPathAtCaret("""
            tags:
              - <caret>kotlin
              - multiplatform
        """.trimIndent())

        assertNotNull(path)
        // Path includes array index
        assertEquals(listOf("tags", "0"), path)
    }

    @Test
    fun testBuildPathToPosition_nestedArrayItem() {
        val path = buildPathAtCaret("""
            users:
              - name: <caret>Alice
                age: 30
              - name: Bob
                age: 25
        """.trimIndent())

        assertNotNull(path)
        // Path includes array index and property name
        assertEquals(listOf("users", "0", "name"), path)
    }

    @Test
    fun testBuildPathToPosition_afterColon() {
        val path = buildPathAtCaret("""
            name: <caret>
            age: 30
        """.trimIndent())

        assertNotNull(path)
        assertEquals(listOf("name"), path)
    }

    @Test
    fun testBuildPathToPosition_emptyDocument() {
        val path = buildPathAtCaret("<caret>")

        // Empty document should an empty list
        assertNotNull(path)
        assertTrue(path.isEmpty())
    }

    @Test
    fun testBuildPathToPosition_rootLevel() {
        val path = buildPathAtCaret("""
            <caret>name: John
        """.trimIndent())

        assertNotNull(path)
        assertTrue(path.isEmpty() || path == listOf("name"))
    }

    @Test
    fun testBuildPathToPosition_multiLevelNesting() {
        val path = buildPathAtCaret("""
            root:
              level1:
                level2:
                  level<caret>3: value
        """.trimIndent())

        assertNotNull(path)
        assertEquals(listOf("root", "level1", "level2"), path)
    }

    @Test
    fun testBuildPathToPosition_fixInvalidDocumentWithoutKey() {
        val path = buildPathAtCaret("""
            key: <caret>
        """.trimIndent())

        assertNotNull(path)
        assertEquals(listOf("key"), path)
    }
}
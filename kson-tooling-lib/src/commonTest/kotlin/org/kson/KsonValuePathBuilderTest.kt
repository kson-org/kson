package org.kson

import org.kson.navigation.KsonValuePathBuilder
import org.kson.parser.Coordinates
import kotlin.test.*

/**
 * Tests for [KsonValuePathBuilder]
 */
class KsonValuePathBuilderTest {

    /**
     * Helper to test path building at the <caret> position in the document.
     *
     * [documentWithCaret] contains a single <caret> marker indicating the cursor position.
     * [expectedPath] is the expected path result.
     * [includePropertyKeys] controls whether property keys should be included in the path.
     */
    private fun assertPathAtCaret(
        documentWithCaret: String,
        expectedPath: List<String>,
        includePropertyKeys: Boolean = true
    ) {
        val caretMarker = "<caret>"
        val caretIndex = documentWithCaret.indexOf(caretMarker)
        require(caretIndex >= 0) { "Document must contain $caretMarker marker" }

        // Calculate line and column
        val beforeCaret = documentWithCaret.take(caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)

        // Remove caret marker from document
        val document = documentWithCaret.replace(caretMarker, "")

        val actualPath = KsonValuePathBuilder(document, Coordinates(line, column))
            .buildPathToPosition(includePropertyKeys = includePropertyKeys)

        assertEquals(expectedPath, actualPath, "Path does not match expected value")
    }

    @Test
    fun testBuildPathToPosition_simpleProperty() {
        assertPathAtCaret(
            """
            name: <caret>John
            age: 30
            """.trimIndent(),
            expectedPath = listOf("name")
        )
    }

    @Test
    fun testBuildPathToPosition_secondProperty() {
        assertPathAtCaret(
            """
            name: John
            age: <caret>30
            """.trimIndent(),
            expectedPath = listOf("age")
        )
    }

    @Test
    fun testBuildPathToPosition_nestedProperty() {
        assertPathAtCaret(
            """
            person:
              name: <caret>Alice
              age: 25
            """.trimIndent(),
            expectedPath = listOf("person", "name")
        )
    }

    @Test
    fun testBuildPathToPosition_deeplyNestedProperty() {
        assertPathAtCaret(
            """
            company:
              address:
                city: <caret>Boston
            """.trimIndent(),
            expectedPath = listOf("company", "address", "city")
        )
    }

    @Test
    fun testBuildPathToPosition_arrayItem() {
        assertPathAtCaret(
            """
            tags:
              - <caret>kotlin
              - multiplatform
            """.trimIndent(),
            expectedPath = listOf("tags", "0") // Path includes array index
        )
    }

    @Test
    fun testBuildPathToPosition_nestedArrayItem() {
        assertPathAtCaret(
            """
            users:
              - name: <caret>Alice
                age: 30
              - name: Bob
                age: 25
            """.trimIndent(),
            expectedPath = listOf("users", "0", "name") // Path includes array index and property name
        )
    }

    @Test
    fun testBuildPathToPosition_afterColon() {
        assertPathAtCaret(
            """
            name: <caret>
            age: 30
            """.trimIndent(),
            expectedPath = listOf("name")
        )
    }

    @Test
    fun testBuildPathToPosition_emptyDocument() {
        assertPathAtCaret(
            "<caret>",
            expectedPath = emptyList() // Empty document should return an empty list
        )
    }

    @Test
    fun testBuildPathToPosition_rootLevel() {
        // When caret is at the start of a line with a property, it returns that property's path
        assertPathAtCaret(
            """
            <caret>name: John
            """.trimIndent(),
            expectedPath = listOf("name")
        )
    }


    @Test
    fun testBuildPathToPosition_classic_simpleProperty() {
        assertPathAtCaret(
            """
            {
              "name": <caret>"John",
              "age": 30
            }
            """.trimIndent(),
            expectedPath = listOf("name")
        )
    }

    @Test
    fun testBuildPathToPosition_classic_secondProperty() {
        assertPathAtCaret(
            """
            {
              "name": "John",
              "age": <caret>30
            }
            """.trimIndent(),
            expectedPath = listOf("age")
        )
    }

    @Test
    fun testBuildPathToPosition_classic_nestedProperty() {
        assertPathAtCaret(
            """
            {
              "person": {
                "name": <caret>"Alice",
                "age": 25
              }
            }
            """.trimIndent(),
            expectedPath = listOf("person", "name")
        )
    }

    @Test
    fun testBuildPathToPosition_classic_deeplyNestedProperty() {
        assertPathAtCaret(
            """
            {
              "company": {
                "address": {
                  "city": <caret>"Boston"
                }
              }
            }
            """.trimIndent(),
            expectedPath = listOf("company", "address", "city")
        )
    }

    @Test
    fun testBuildPathToPosition_classic_arrayItem() {
        assertPathAtCaret(
            """
            {
              "tags": [
                <caret>"kotlin",
                "multiplatform"
              ]
            }
            """.trimIndent(),
            expectedPath = listOf("tags", "0") // Path includes array index
        )
    }

    @Test
    fun testBuildPathToPosition_classic_nestedArrayItem() {
        assertPathAtCaret(
            """
            {
              "users": [
                {
                  "name": <caret>"Alice",
                  "age": 30
                },
                {
                  "name": "Bob",
                  "age": 25
                }
              ]
            }
            """.trimIndent(),
            expectedPath = listOf("users", "0", "name") // Path includes array index and property name
        )
    }

    @Test
    fun testBuildPathToPosition_classic_afterColon() {
        assertPathAtCaret(
            """
            {
              "name": <caret>,
              "age": 30
            }
            """.trimIndent(),
            expectedPath = listOf("name")
        )
    }

    @Test
    fun testBuildPathToPosition_classic_emptyDocument() {
        assertPathAtCaret(
            "<caret>",
            expectedPath = emptyList() // Empty document should return an empty list
        )
    }

    @Test
    fun testBuildPathToPosition_classic_rootLevel() {
        assertPathAtCaret(
            """
            {<caret>
              "name": "John"
            }
            """.trimIndent(),
            expectedPath = emptyList() // At root level inside braces
        )
    }

    @Test
    fun testBuildPathToPosition_classic_multiLevelNesting() {
        assertPathAtCaret(
            """
            {
              "root": {
                "level1": {
                  "level2": {
                    "level<caret>3": "value"
                  }
                }
              }
            }
            """.trimIndent(),
            expectedPath = listOf("root", "level1", "level2", "level3")
        )
    }

    @Test
    fun testBuildPathToPosition_classic_fixInvalidDocumentWithoutKey() {
        assertPathAtCaret(
            """
            {
              "key": <caret>
            }
            """.trimIndent(),
            expectedPath = listOf("key")
        )
    }

    @Test
    fun testBuildPathToPosition_classic_onPropertyKey_forCompletion() {
        // When cursor is on property key, for completion, path should drop last element (go to parent)
        assertPathAtCaret(
            """
            {
              "user<caret>name": "johndoe"
            }
            """.trimIndent(),
            expectedPath = emptyList(), // Drops "username", returns empty (parent of root)
            includePropertyKeys = false
        )
    }

    @Test
    fun testBuildPathToPosition_classic_onPropertyKey_forDefinition() {
        // When cursor is on property key, for definition, path should keep the property
        assertPathAtCaret(
            """
            {
              "user<caret>name": "johndoe"
            }
            """.trimIndent(),
            expectedPath = listOf("username"), // Keeps "username" for definition lookup
            includePropertyKeys = true
        )
    }
}
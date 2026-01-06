package org.kson

import org.kson.navigation.KsonValuePathBuilder
import org.kson.parser.Coordinates
import org.kson.schema.JsonPointer
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
        expectedPath: JsonPointer,
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
            .buildJsonPointerToPosition(includePropertyKeys = includePropertyKeys)

        assertEquals(expectedPath, actualPath, "Path does not match expected value")
    }

    @Test
    fun testBuildJsonPointerToPosition_simpleProperty() {
        assertPathAtCaret(
            """
            name: <caret>John
            age: 30
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("name"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_secondProperty() {
        assertPathAtCaret(
            """
            name: John
            age: <caret>30
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("age"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_nestedProperty() {
        assertPathAtCaret(
            """
            person:
              name: <caret>Alice
              age: 25
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("person", "name"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_deeplyNestedProperty() {
        assertPathAtCaret(
            """
            company:
              address:
                city: <caret>Boston
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("company", "address", "city"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_arrayItem() {
        assertPathAtCaret(
            """
            tags:
              - <caret>kotlin
              - multiplatform
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("tags", "0")) // Path includes array index
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_nestedArrayItem() {
        assertPathAtCaret(
            """
            users:
              - name: <caret>Alice
                age: 30
              - name: Bob
                age: 25
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("users", "0", "name")) // Path includes array index and property name
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_afterColon() {
        assertPathAtCaret(
            """
            name: <caret>
            age: 30
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("name"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_emptyDocument() {
        assertPathAtCaret(
            "<caret>",
            expectedPath = JsonPointer.fromTokens(emptyList()) // Empty document should return an empty list
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_rootLevel() {
        // When caret is at the start of a line with a property, it returns that property's path
        assertPathAtCaret(
            """
            <caret>name: John
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("name"))
        )
    }


    @Test
    fun testBuildJsonPointerToPosition_classic_simpleProperty() {
        assertPathAtCaret(
            """
            {
              "name": "<caret>John",
              "age": 30
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("name"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_secondProperty() {
        assertPathAtCaret(
            """
            {
              "name": "John",
              "age": <caret>30
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("age"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_nestedProperty() {
        assertPathAtCaret(
            """
            {
              "person": {
                "name": "<caret>Alice",
                "age": 25
              }
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("person", "name"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_deeplyNestedProperty() {
        assertPathAtCaret(
            """
            {
              "company": {
                "address": {
                  "city": "<caret>Boston"
                }
              }
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("company", "address", "city"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_arrayItem() {
        assertPathAtCaret(
            """
            {
              "tags": [
                "<caret>kotlin",
                "multiplatform"
              ]
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("tags", "0")) // Path includes array index
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_nestedArrayItem() {
        assertPathAtCaret(
            """
            {
              "users": [
                {
                  "name": "<caret>Alice",
                  "age": 30
                },
                {
                  "name": "Bob",
                  "age": 25
                }
              ]
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("users", "0", "name")) // Path includes array index and property name
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_afterColon() {
        assertPathAtCaret(
            """
            {
              "name": <caret>,
              "age": 30
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("name"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_emptyDocument() {
        assertPathAtCaret(
            "<caret>",
            expectedPath = JsonPointer.fromTokens(emptyList()) // Empty document should return an empty list
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_rootLevel() {
        assertPathAtCaret(
            """
            {<caret>
              "name": "John"
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(emptyList()) // At root level inside braces
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_multiLevelNesting() {
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
            expectedPath = JsonPointer.fromTokens(listOf("root", "level1", "level2", "level3"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_fixInvalidDocumentWithoutKey() {
        assertPathAtCaret(
            """
            {
              "key": <caret>
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("key"))
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_onPropertyKey_forCompletion() {
        // When cursor is on property key, for completion, path should drop last element (go to parent)
        assertPathAtCaret(
            """
            {
              "user<caret>name": "johndoe"
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(emptyList()), // Drops "username", returns empty (parent of root)
            includePropertyKeys = false
        )
    }

    @Test
    fun testBuildJsonPointerToPosition_classic_onPropertyKey_forDefinition() {
        // When cursor is on property key, for definition, path should keep the property
        assertPathAtCaret(
            """
            {
              "user<caret>name": "johndoe"
            }
            """.trimIndent(),
            expectedPath = JsonPointer.fromTokens(listOf("username")), // Keeps "username" for definition lookup
            includePropertyKeys = true
        )
    }
}
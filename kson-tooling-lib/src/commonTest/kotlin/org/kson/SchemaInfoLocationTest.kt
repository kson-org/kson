package org.kson

import kotlin.test.*

/**
 * Tests for [KsonTooling] hover information functionality
 */
class SchemaInfoLocationTest {

    /**
     * Helper to get completions at the <caret> position in the document
     */
    private fun getInfoAtCaret(schema: String, documentWithCaret: String): String? {
        val caretMarker = "<caret>"
        val caretIndex = documentWithCaret.indexOf(caretMarker)
        require(caretIndex >= 0) { "Document must contain $caretMarker marker" }

        // Calculate line and column
        val beforeCaret = documentWithCaret.take(caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)

        // Remove caret marker from document
        val document = documentWithCaret.replace(caretMarker, "")

        return KsonTooling.getSchemaInfoAtLocation(document, schema, line, column)
    }

    @Test
    fun testGetSchemaInfoAtLocation_simpleStringProperty() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "The person's name"
                },
                "age": {
                  "type": "number",
                  "description": "The person's age"
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            nam<caret>e: John
            age: 30
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("The person's name"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_numberProperty() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                },
                "age": {
                  "type": "number",
                  "description": "The person's age",
                  "minimum": 0,
                  "maximum": 120
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            name: John
            age: <caret>30
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("The person's age"))
        assertTrue(hoverInfo.contains("*Type:* `number`"))
        assertTrue(hoverInfo.contains("*Minimum:* 0"))
        assertTrue(hoverInfo.contains("*Maximum:* 120"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_withTitle() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "username": {
                  "type": "string",
                  "title": "Username",
                  "description": "The user's unique identifier"
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            username: <caret>johndoe
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("**Username**"))
        assertTrue(hoverInfo.contains("The user's unique identifier"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_withEnum() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "status": {
                  "type": "string",
                  "enum": ["active", "inactive", "pending"]
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            status: <caret>active
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Allowed values:*"))
        assertTrue(hoverInfo.contains("`active`"))
        assertTrue(hoverInfo.contains("`inactive`"))
        assertTrue(hoverInfo.contains("`pending`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_withPattern() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "email": {
                  "type": "string",
                  "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            email: <caret>'user@example.com'
        """.trimIndent())

        assertNotNull(hoverInfo, "Expected hover info but got null")
        assertTrue(hoverInfo.contains("*Pattern:*"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_nestedObject() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "person": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Person's name"
                    },
                    "age": {
                      "type": "number",
                      "description": "Person's age"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            person:
              name: <caret>Alice
              age: 25
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Person's name"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_arrayItems() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "tags": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "description": "A tag value",
                    "minLength": 2,
                    "maxLength": 50
                  }
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            tags:
              - <caret>kotlin
              - multiplatform
              - json
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("A tag value"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
        assertTrue(hoverInfo.contains("*Min length:* 2"))
        assertTrue(hoverInfo.contains("*Max length:* 50"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_withDefault() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "timeout": {
                  "type": "number",
                  "description": "Request timeout in milliseconds",
                  "default": 3000
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            timeout: <caret>5000
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Request timeout in milliseconds"))
        assertTrue(hoverInfo.contains("*Default:* `3000`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_stringLengthConstraints() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "password": {
                  "type": "string",
                  "minLength": 8,
                  "maxLength": 32
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            password: <caret>secret123
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Min length:* 8"))
        assertTrue(hoverInfo.contains("*Max length:* 32"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_arrayConstraints() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 10,
                  "items": {
                    "type": "string"
                  }
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            items:
              - <caret>first
              - second
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_noSchemaForProperty() {
        val schema = """
            {
              "type": "object",
              "properties": {}
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            undefinedProp: <caret>value
        """.trimIndent())

        // Should return null when no schema matches
        assertNull(hoverInfo)
    }

    @Test
    fun testGetSchemaInfoAtLocation_additionalProperties() {
        val schema = """
            {
              "type": "object",
              "additionalProperties": {
                "type": "string",
                "description": "Any additional string field"
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            customField: <caret>customValue
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Any additional string field"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_unionType() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "value": {
                  "type": ["string", "number"],
                  "description": "Can be either string or number"
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            value: <caret>123
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Can be either string or number"))
        assertTrue(hoverInfo.contains("*Type:* `string | number`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_deeplyNestedArray() {
        val schema = """
            {
              "type": "object",
              "properties": {
                "matrix": {
                  "type": "array",
                  "items": {
                    "type": "array",
                    "items": {
                      "type": "number",
                      "description": "Matrix cell value"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            matrix:
              -
                - <caret>1
                - 2
              -
                - 3
                - 4
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Matrix cell value"))
        assertTrue(hoverInfo.contains("*Type:* `number`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_invalidDocument() {
        val schema = """
            {
              "type": "object"
            }
        """.trimIndent()

        // Should return null for invalid document
        val hoverInfo = getInfoAtCaret(schema, """
            {inva<caret>lid kson
        """.trimIndent())

        assertNull(hoverInfo)
    }

    @Test
    fun testGetSchemaInfoAtLocation_patternProperties() {
        val schema = """
            {
              "type": "object",
              "patternProperties": {
                "^field_": {
                  "type": "string",
                  "description": "A field matching the pattern"
                }
              }
            }
        """.trimIndent()

        val hoverInfo = getInfoAtCaret(schema, """
            field_1: <caret>value1
            field_2: value2
        """.trimIndent())

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("A field matching the pattern"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_refDefinition() {
        // Simplified test for $ref resolution
        val schema = """
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "items": {
                    "${'$'}ref": "#/${'$'}defs/Item"
                  }
                }
              },
              "${'$'}defs": {
                "Item": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Item name from ref"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        // This should resolve the $ref to #/$defs/Item and show the name field schema
        val hoverInfo = getInfoAtCaret(schema, """
            items:
              - name: <caret>foo
        """.trimIndent())

        assertNotNull(hoverInfo, "Expected hover info for name field. Got null")
        assertTrue(hoverInfo.contains("Item name from ref"), "Expected description from resolved ref. Got: $hoverInfo")
        assertTrue(hoverInfo.contains("*Type:* `string`"), "Expected type from resolved ref. Got: $hoverInfo")
    }

    @Test
    fun testGetSchemaInfoAtLocation_anyOf_combinedInfo() {
        // When multiple anyOf branches are valid, their info should be combined
        val schema = """
            {
              "type": "object",
              "properties": {
                "value": {
                  "anyOf": [
                    {
                      "type": "string",
                      "description": "String value representation",
                      "minLength": 1
                    },
                    {
                      "type": "string",
                      "description": "String value with pattern",
                      "pattern": "^[A-Z]+"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        // Both anyOf branches accept strings, so both should be valid
        val hoverInfo = getInfoAtCaret(schema, """
            value: <caret>TEST
        """.trimIndent())

        assertNotNull(hoverInfo, "Expected hover info for value field")

        // Should contain info from both branches, separated
        assertTrue(hoverInfo.contains("String value representation"), "Expected first branch description. Got: $hoverInfo")
        assertTrue(hoverInfo.contains("String value with pattern"), "Expected second branch description. Got: $hoverInfo")

        // Should have separator between the two
        assertTrue(hoverInfo.contains("---"), "Expected separator between branches. Got: $hoverInfo")
    }
}
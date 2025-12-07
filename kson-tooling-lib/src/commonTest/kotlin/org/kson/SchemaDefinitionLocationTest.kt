package org.kson

import org.kson.parser.Coordinates
import kotlin.test.*
import org.kson.parser.Location

/**
 * Tests for [KsonTooling.getSchemaLocationAtLocation] "jump to definition" functionality
 */
class SchemaDefinitionLocationTest {

    /**
     * Helper to test definition location.
     *
     * [schemaWithCaret] contains two <caret>'s, to signal the 'start' and 'end' [Location],
     * [documentWithCaret] contains <caret>. The test verifies that the returned location
     * points to that [Location] in the schema.
     *
     * If schemaWithCaret does NOT contain <caret>, the test verifies that the result is null.
     */
    private fun assertDefinitionLocation(schemaWithCaret: String, documentWithCaret: String) {
        val caretMarker = "<caret>"

        fun createCoordinates(document: String, caretIndex: Int): Coordinates? {
            if (caretIndex == -1) {
                return null
            }

            val beforeCaret = document.take(caretIndex)
            val docLine = beforeCaret.count { it == '\n' }

            val docColumn = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)
            return Coordinates(docLine, docColumn)
        }

        // Process document
        val docCaretIndex = documentWithCaret.indexOf(caretMarker)
        val docCoordinates = createCoordinates(documentWithCaret, docCaretIndex) ?: throw IllegalArgumentException("Document must contain $caretMarker marker")
        val document = documentWithCaret.replace(caretMarker, "")

        // Process schema
        val schemaCaretIndexes =
            (schemaWithCaret.indexOf(caretMarker) to schemaWithCaret.lastIndexOf(caretMarker)).let {
                createCoordinates(schemaWithCaret, it.first) to createCoordinates(schemaWithCaret, it.second)
            }
        val schema = schemaWithCaret.replace(caretMarker, "")


        // Get the actual location result
        val location = KsonTooling.getSchemaLocationAtLocation(document, schema, docCoordinates.line, docCoordinates.column)

        if (schemaCaretIndexes.first == null) {
            // No <caret> in schema means we expect null
            assertNull(location, "Expected null when no schema definition is found")
        } else {
            // Calculate expected position in schema
            assertNotNull(location, "Expected definition location but got null")

            // The location should start at the first <caret> position in the schema and end at the second <caret> position
            assertEquals(
                schemaCaretIndexes.first?.column to schemaCaretIndexes.first?.line, location.startColumn to location.startLine,
                "Expected start coordinates to be ${schemaCaretIndexes.first} but was ${location.startColumn to location.startLine}"
            )
            assertEquals(
                schemaCaretIndexes.second?.column to schemaCaretIndexes.second?.line, location.endColumn to location.endLine,
                "Expected end coordinates to be ${schemaCaretIndexes.second} but was ${location.endColumn to location.endLine}"
            )
        }
    }

    @Test
    fun testGetSchemaLocationAtLocation_simpleStringProperty() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {
                    "name": <caret>{
                      "type": "string",
                      "description": "The person's name"
                    }<caret>,
                    "age": {
                      "type": "number"
                    }
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                name: <caret>John
                age: 30
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_numberProperty() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string"
                    },
                    "age": <caret>{
                      "type": "number",
                      "description": "The person's age"
                    }<caret>
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                name: John
                age: <caret>30
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_nestedObject() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {
                    "person": {
                      "type": "object",
                      "properties": {
                        "name": <caret>{
                          "type": "string",
                          "description": "Person's name"
                        }<caret>,
                        "age": {
                          "type": "number"
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                person:
                  name: <caret>Alice
                  age: 25
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_arrayItems() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {
                    "tags": {
                      "type": "array",
                      "items": <caret>{
                        "type": "string",
                        "description": "A tag value"
                      }<caret>
                    }
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                tags:
                  - <caret>kotlin
                  - multiplatform
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_deeplyNestedArray() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {
                    "matrix": {
                      "type": "array",
                      "items": {
                        "type": "array",
                        "items": <caret>{
                          "type": "number",
                          "description": "Matrix cell value"
                        }<caret>
                      }
                    }
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                matrix:
                  -
                    - <caret>1
                    - 2
                  -
                    - 3
                    - 4
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_noSchemaForProperty() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {}
                }
            """.trimIndent(),
            documentWithCaret = """
                undefinedProp: <caret>value
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_invalidDocument() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object"
                }
            """.trimIndent(),
            documentWithCaret = """
                {inva<caret>lid kson
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_refDefinition() {
        assertDefinitionLocation(
            schemaWithCaret = """
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
                        "name": <caret>{
                          "type": "string",
                          "description": "Item name from ref"
                        }<caret>
                      }
                    }
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                items:
                  - name: <caret>foo
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_additionalProperties() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "additionalProperties": <caret>{
                    "type": "string",
                    "description": "Any additional string field"
                  }<caret>
                }
            """.trimIndent(),
            documentWithCaret = """
                customField: <caret>customValue
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_patternProperties() {
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "patternProperties": {
                    "^field_": <caret>{
                      "type": "string",
                      "description": "A field matching the pattern"
                    }<caret>
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                field_1: <caret>value1
                field_2: value2
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_onPropertyKey() {
        // When cursor is on the property key, with forDefinition=true,
        // it should point to the property's schema definition (not drop the last element)
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {
                    "username": <caret>{
                      "type": "string"
                    }<caret>
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                user<caret>name: johndoe
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_refDefinition_onPropertyKey() {
        assertDefinitionLocation(
            schemaWithCaret = """
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
                        "name": <caret>{
                          "type": "string",
                          "description": "Item name from ref"
                        }<caret>
                      }
                    }
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                items:
                  - name<caret>: foo
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_anyOf_filteredLocation() {
        // Property defined in multiple anyOf branches should return only the valid location
        // Document has name: value (string), so only the string branch validates
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "anyOf": [
                    {
                      "properties": {
                        "name": <caret>{
                          "type": "string",
                          "description": "Name from first branch"
                        }<caret>
                      }
                    },
                    {
                      "properties": {
                        "name": {
                          "type": "number",
                          "description": "Name from second branch"
                        }
                      }
                    }
                  ]
                }
            """.trimIndent(),
            documentWithCaret = """
                name: <caret>value
            """.trimIndent()
        )
    }

    @Test
    fun testGetSchemaLocationAtLocation_allOf_multipleLocations() {
        // Property defined in multiple allOf branches should return multiple locations
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "allOf": [
                    {
                      "properties": {
                        "shared": <caret>{
                          "type": "string"
                        }<caret>
                      }
                    },
                    {
                      "properties": {
                        "shared": <caret>{
                          "minLength": 5
                        }<caret>
                      }
                    }
                  ]
                }
            """.trimIndent(),
            documentWithCaret = """
                shared: <caret>test
            """.trimIndent()
        )
    }

    @Test
    fun testJumpToDefinition_anyOf_filteredByValidation() {
        // For anyOf with discriminator, only the valid branch should be shown
        // Document has type: email, so only the email branch validates
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {
                    "notification": {
                      "anyOf": [
                        {
                          "type": "object",
                          "properties": {
                            "type": <caret>{
                              "const": "email"
                            }<caret>,
                            "recipient": {
                              "type": "string"
                            }
                          }
                        },
                        {
                          "type": "object",
                          "properties": {
                            "type": {
                              "const": "sms"
                            },
                            "phoneNumber": {
                              "type": "string"
                            }
                          }
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                notification:
                  type<caret>: email
            """.trimIndent()
        )
    }

    @Test
    fun testJumpToDefinition_oneOf_filteredByType() {
        // When a property appears in multiple oneOf branches with different types,
        // only the valid branch should be shown based on the actual value
        // Document has value: test (a string), so only the string branch validates
        assertDefinitionLocation(
            schemaWithCaret = """
                {
                  "type": "object",
                  "properties": {
                    "data": {
                      "oneOf": [
                        {
                          "type": "object",
                          "properties": {
                            "value": <caret>{
                              "type": "string",
                              "description": "String value"
                            }<caret>
                          }
                        },
                        {
                          "type": "object",
                          "properties": {
                            "value": {
                              "type": "number",
                              "description": "Numeric value"
                            }
                          }
                        },
                        {
                          "type": "object",
                          "properties": {
                            "value": {
                              "type": "array",
                              "items": {
                                "type": "string"
                              },
                              "description": "Array value"
                            }
                          }
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            documentWithCaret = """
                data:
                  value<caret>: test
            """.trimIndent()
        )
    }

}
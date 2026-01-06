package org.kson

import org.kson.parser.Coordinates
import kotlin.test.*

/**
 * Tests for [KsonTooling.getSchemaLocationAtLocation] "jump to definition" functionality
 */
class SchemaDefinitionLocationTest {

    /**
     * Helper to test definition location.
     *
     * [schemaWithCaret] contains <caret> markers to signal expected ranges.
     * Multiple ranges can be specified for scenarios where multiple schema locations match (e.g., combinators).
     * [documentWithCaret] contains a single <caret> marker. The test verifies that the returned locations
     * match all the expected ranges in the schema.
     *
     * If schemaWithCaret does NOT contain any <caret> markers, the test verifies that the result is null or empty.
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

        // Process schema - find all <caret> markers (they come in pairs: open and close)
        val expectedRanges = mutableListOf<Pair<Coordinates, Coordinates>>()
        var searchText = schemaWithCaret
        var offset = 0

        while (true) {
            val startIdx = searchText.indexOf(caretMarker)
            if (startIdx == -1) break

            val endIdx = searchText.indexOf(caretMarker, startIdx + caretMarker.length)
            if (endIdx == -1) break

            val startCoords = createCoordinates(schemaWithCaret, offset + startIdx)!!
            val endCoords = createCoordinates(schemaWithCaret, offset + endIdx)!!

            expectedRanges.add(startCoords to endCoords)

            // Move past this pair
            offset += endIdx + caretMarker.length
            searchText = searchText.substring(endIdx + caretMarker.length)
        }

        val schema = schemaWithCaret.replace(caretMarker, "")

        // Get the actual location result
        val locations = KsonTooling.getSchemaLocationAtLocation(document, schema, docCoordinates.line, docCoordinates.column)

        // Insert <caret> markers into the actual schema to visualize where the returned locations are
        val actualSchemaWithCarets = buildActualSchemaWithCarets(schema, locations)

        // Use string assertion to show differences clearly
        assertEquals(
            schemaWithCaret,
            actualSchemaWithCarets,
            "Schema definition locations do not match. Expected vs Actual with <caret> markers:"
        )

    }

    /**
     * Helper function to insert <caret> markers into the schema at the locations returned by the function.
     * This allows for easy visual comparison of expected vs actual locations.
     */
    private fun buildActualSchemaWithCarets(schema: String, locations: List<Range>): String {
        val caretMarker = "<caret>"

        // Sort locations by position (descending) so we can insert from end to start without affecting indices
        val sortedLocations = locations.sortedWith(
            compareByDescending<Range> { it.startLine }
                .thenByDescending { it.startColumn }
        )

        val lines = schema.lines().toMutableList()

        // Insert carets from end to start to preserve indices
        for (location in sortedLocations) {
            // Insert end marker
            val endLine = lines[location.endLine]
            lines[location.endLine] = endLine.substring(0, location.endColumn) + caretMarker + endLine.substring(location.endColumn)

            // Insert start marker
            val startLine = lines[location.startLine]
            lines[location.startLine] = startLine.substring(0, location.startColumn) + caretMarker + startLine.substring(location.startColumn)
        }

        return lines.joinToString("\n")
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

    @Test
    fun testJumpToDefinition_anyOfInArray_Test() {
        assertDefinitionLocation(
            schemaWithCaret = """
                '${'$'}defs':
                  UrlSource:
                    type: object
                    properties:
                      url:
                        <caret>type: string
                        .<caret>
                      .
                    .
                  .
                anyOf:
                  - '${'$'}ref': '#/${'$'}defs/UrlSource'
                  - type: array
                    items:
                      anyOf:
                        - '${'$'}ref': '#/${'$'}defs/UrlSource'
            """.trimIndent(),
            documentWithCaret = """
                - ur<caret>l: 'https://example.com/file.exe'
            """.trimIndent()
        )
    }

}
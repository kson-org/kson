package org.kson

import org.kson.parser.Coordinates
import kotlin.test.*

/**
 * Tests for [KsonTooling.resolveRefAtLocation] - jump to definition within schema documents
 */
class SchemaRefResolutionTest {

    /**
     * Helper to test $ref resolution.
     *
     * [schemaWithMarkers] contains markers:
     * - <target>...</target> marks the expected target location (where the ref should resolve to)
     * - <cursor> marks where the cursor is positioned (on the $ref value)
     *
     * If schemaWithMarkers does NOT contain <target> markers,
     * the test verifies that the result is null or empty.
     */
    private fun assertRefResolution(schemaWithMarkers: String) {
        val targetStartMarker = "<target>"
        val targetEndMarker = "</target>"
        val cursorMarker = "<cursor>"

        fun createCoordinates(document: String, index: Int): Coordinates {
            val beforeIndex = document.take(index)
            val line = beforeIndex.count { it == '\n' }
            val column = index - (beforeIndex.lastIndexOf('\n') + 1)
            return Coordinates(line, column)
        }

        // Find the cursor position
        val cursorIndex = schemaWithMarkers.indexOf(cursorMarker)
        if (cursorIndex == -1) {
            throw IllegalArgumentException("Schema must contain a $cursorMarker marker for cursor position")
        }

        val cursorCoordinates = createCoordinates(schemaWithMarkers, cursorIndex)

        // Find the expected target range
        val targetStartIdx = schemaWithMarkers.indexOf(targetStartMarker)
        val targetEndIdx = schemaWithMarkers.indexOf(targetEndMarker)

        val expectedRange = if (targetStartIdx != -1 && targetEndIdx != -1) {
            val startCoords = createCoordinates(schemaWithMarkers, targetStartIdx)
            val endCoords = createCoordinates(schemaWithMarkers, targetEndIdx)
            startCoords to endCoords
        } else {
            null
        }

        val schema = schemaWithMarkers
            .replace(targetStartMarker, "")
            .replace(targetEndMarker, "")
            .replace(cursorMarker, "")

        // Get the actual resolution result
        val locations = KsonTooling.resolveRefAtLocation(schema, cursorCoordinates.line, cursorCoordinates.column)

        if (expectedRange == null) {
            // No expected range means we expect null or empty
            assertTrue(locations == null || locations.isEmpty(),
                "Expected null or empty when \$ref should not resolve")
        } else {
            // We expect locations to match the expected range
            assertNotNull(locations, "Expected \$ref to resolve but got null")
            assertEquals(1, locations.size, "Expected exactly one location")

            val location = locations.first()
            assertEquals(expectedRange.first.line, location.startLine, "Start line mismatch")
            assertEquals(expectedRange.first.column, location.startColumn, "Start column mismatch")
            assertEquals(expectedRange.second.line, location.endLine, "End line mismatch")
            assertEquals(expectedRange.second.column, location.endColumn, "End column mismatch")
        }
    }

    @Test
    fun testResolveRef_simpleDefinition() {
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "user": {
                  "${'$'}ref": "#/${'$'}defs/User<cursor>"
                }
              },
              "${'$'}defs": {
                "User": <target>{
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string"
                    }
                  }
                }</target>
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_jsonPointer() {
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "data": {
                  "${'$'}ref": "#/properties/user/properties/name<cursor>"
                },
                "user": {
                  "type": "object",
                  "properties": {
                    "name": <target>{
                      "type": "string"
                    }</target>
                  }
                }
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_nestedDefinitions() {
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "company": {
                  "${'$'}ref": "#/${'$'}defs/Company<cursor>"
                }
              },
              "${'$'}defs": {
                "Company": <target>{
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string"
                    },
                    "employees": {
                      "type": "array",
                      "items": {
                        "${'$'}ref": "#/${'$'}defs/Person"
                      }
                    }
                  }
                }</target>,
                "Person": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string"
                    }
                  }
                }
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_refToRef() {
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "data": {
                  "${'$'}ref": "#/${'$'}defs/Alias<cursor>"
                }
              },
              "${'$'}defs": {
                "Alias": <target>{
                  "${'$'}ref": "#/${'$'}defs/Target"
                }</target>,
                "Target": {
                  "type": "string"
                }
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_invalidRef() {
        // Ref points to non-existent definition
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "data": {
                  "${'$'}ref": "#/${'$'}defs/NonExistent<cursor>"
                }
              },
              "${'$'}defs": {
                "Other": {
                  "type": "string"
                }
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_externalRef() {
        // External refs (not starting with #) should not resolve
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "data": {
                  "${'$'}ref": "./external.schema.kson#/${'$'}defs/User<cursor>"
                }
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_cursorNotOnRef() {
        // Cursor on a property that is not a $ref
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string<cursor>"
                }
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_cursorOnRefKey() {
        // Cursor on the "$ref" key itself, not the value
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "user": {
                  "${'$'}ref<cursor>": "#/${'$'}defs/User"
                }
              },
              "${'$'}defs": {
                "User": {
                  "type": "object"
                }
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_definitions() {
        // Test with "definitions" (older JSON Schema draft) instead of "$defs"
        assertRefResolution("""
            {
              "type": "object",
              "properties": {
                "item": {
                  "${'$'}ref": "#/definitions/Item<cursor>"
                }
              },
              "definitions": {
                "Item": <target>{
                  "type": "object",
                  "properties": {
                    "id": {
                      "type": "number"
                    }
                  }
                }</target>
              }
            }
        """.trimIndent())
    }

    @Test
    fun testResolveRef_rootRef() {
        // Ref to root schema
        assertRefResolution("""
            <target>{
              "type": "object",
              "properties": {
                "recursive": {
                  "${'$'}ref": "#<cursor>"
                }
              }
            }</target>
        """.trimIndent())
    }

    @Test
    fun testResolveRef_withSchemaId() {
        // Schema with $id should still resolve internal refs correctly
        assertRefResolution("""
            {
              "${'$'}id": "https://example.com/my-schema",
              "type": "object",
              "properties": {
                "data": {
                  "${'$'}ref": "#/${'$'}defs/Data<cursor>"
                }
              },
              "${'$'}defs": {
                "Data": <target>{
                  "type": "string"
                }</target>
              }
            }
        """.trimIndent())
    }
}

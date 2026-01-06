package org.kson

import org.kson.navigation.extractSchemaInfo
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaInfoDisplayTest {

    /**
     * Helper to extract hover info from a schema source
     */
    private fun getHoverInfo(schemaSource: String): String? {
        val schema = KsonCore.parseToAst(schemaSource).ksonValue ?: error("Failed to parse schema:\n$schemaSource")
        return schema.extractSchemaInfo()
    }

    @Test
    fun testExtractHoverWithTitleAndDescription() {
        val hoverInfo = getHoverInfo("""
            {
                title: "User Name"
                description: "The full name of the user"
                type: "string"
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("**User Name**"))
        assertTrue(hoverInfo.contains("The full name of the user"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testExtractHoverWithDescriptionOnly() {
        val hoverInfo = getHoverInfo("""
            {
                description: "A simple description"
                type: "number"
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("A simple description"))
        assertTrue(hoverInfo.contains("*Type:* `number`"))
    }

    @Test
    fun testExtractHoverWithTitleOnly() {
        val hoverInfo = getHoverInfo("""
            {
                title: "Important Field"
                type: "boolean"
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("**Important Field**"))
        assertTrue(hoverInfo.contains("*Type:* `boolean`"))
    }

    @Test
    fun testExtractHoverWithDefaultValue() {
        val hoverInfo = getHoverInfo("""
            {
                type: "string"
                default: "defaultValue"
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Default:* `defaultValue`"))
    }

    @Test
    fun testExtractHoverWithDefaultNumber() {
        val hoverInfo = getHoverInfo("""
            {
                type: "integer"
                default: 42
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Default:* `42`"))
    }

    @Test
    fun testExtractHoverWithDefaultBoolean() {
        val hoverInfo = getHoverInfo("""
            {
                type: "boolean"
                default: true
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Default:* `true`"))
    }

    @Test
    fun testExtractHoverWithEnumValues() {
        val hoverInfo = getHoverInfo("""
            {
                type: "string"
                enum: ["option1", "option2", "option3"]
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Allowed values:*"))
        assertTrue(hoverInfo.contains("`option1`"))
        assertTrue(hoverInfo.contains("`option2`"))
        assertTrue(hoverInfo.contains("`option3`"))
    }

    @Test
    fun testExtractHoverWithPattern() {
        val hoverInfo = getHoverInfo("""
            {
                type: "string"
                pattern: "^[A-Z][a-z]+$"
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Pattern:* `^[A-Z][a-z]+\$`"))
    }

    @Test
    fun testExtractHoverWithNumericConstraints() {
        val hoverInfo = getHoverInfo("""
            {
                type: "integer"
                minimum: 0
                maximum: 100
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Minimum:* 0"))
        assertTrue(hoverInfo.contains("*Maximum:* 100"))
    }

    @Test
    fun testExtractHoverWithStringLengthConstraints() {
        val hoverInfo = getHoverInfo("""
            {
                type: "string"
                minLength: 3
                maxLength: 50
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Min length:* 3"))
        assertTrue(hoverInfo.contains("*Max length:* 50"))
    }

    @Test
    fun testExtractHoverWithArrayItemConstraints() {
        val hoverInfo = getHoverInfo("""
            {
                type: "array"
                minItems: 1
                maxItems: 10
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Min items:* 1"))
        assertTrue(hoverInfo.contains("*Max items:* 10"))
    }

    @Test
    fun testExtractHoverWithUnionType() {
        val hoverInfo = getHoverInfo("""
            {
                type: ["string", "number", "null"]
                description: "Can be string, number, or null"
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Type:* `string | number | null`"))
        assertTrue(hoverInfo.contains("Can be string, number, or null"))
    }

    @Test
    fun testExtractHoverWithAllFeatures() {
        val hoverInfo = getHoverInfo("""
            {
                title: "Age"
                description: "The user's age in years"
                type: "integer"
                default: 0
                minimum: 0
                maximum: 150
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("**Age**"))
        assertTrue(hoverInfo.contains("The user's age in years"))
        assertTrue(hoverInfo.contains("*Type:* `integer`"))
        assertTrue(hoverInfo.contains("*Default:* `0`"))
        assertTrue(hoverInfo.contains("*Minimum:* 0"))
        assertTrue(hoverInfo.contains("*Maximum:* 150"))
    }

    @Test
    fun testExtractHoverWithMinimalSchema() {
        val hoverInfo = getHoverInfo("""
            {
                type: "string"
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testExtractHoverWithEmptySchema() {
        val hoverInfo = getHoverInfo("{}")
        // Empty schema should return null or empty
        assertNull(hoverInfo)
    }

    @Test
    fun testExtractHoverFromNonObjectSchema() {
        val hoverInfo = getHoverInfo("true")
        // Boolean schema (true/false) doesn't have properties to extract
        assertNull(hoverInfo)
    }

    @Test
    fun testExtractHoverFromSchemaWithOnlyTypeNoOtherInfo() {
        val hoverInfo = getHoverInfo("""
            {
                type: "object"
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Type:* `object`"))
    }

    @Test
    fun testGetSchemaHoverInfoEndToEnd() {
        // Test the full integration: document + schema
        val document = """
            {
                user: {
                    name: "John"
                    age: 30
                }
            }
        """.trimIndent()

        val schema = """
            {
                type: "object"
                properties: {
                    user: {
                        type: "object"
                        properties: {
                            name: {
                                type: "string"
                                description: "User's full name"
                            }
                            age: {
                                type: "integer"
                                description: "User's age in years"
                                minimum: 0
                                maximum: 150
                            }
                        }
                    }
                }
            }
        """

        // Get hover info for the name field (line 2, pointing to "John")
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 2, 15)
        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("User's full name"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaHoverInfoForNestedArrayElement() {
        // language="kson"
        val document = """
            {
                users: [
                    { name: "Alice" }
                    { name: "Bob" }
                ]
            }
        """.trimIndent()

        val schema = """
            {
                type: "object"
                properties: {
                    users: {
                        type: "array"
                        items: {
                            type: "object"
                            properties: {
                                name: {
                                    type: "string"
                                    title: "User Name"
                                    description: "The name of the user"
                                }
                            }
                        }
                    }
                }
            }
        """

        // Get hover info (line 2, pointing to "Alice" value)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 2, 17)
        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("**User Name**"))
        assertTrue(hoverInfo.contains("The name of the user"))
    }

    @Test
    fun testExtractHoverWithComplexDefaultValue() {
        val hoverInfo = getHoverInfo("""
            {
                type: "array"
                default: ["item1", "item2", "item3"]
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Default:*"))
        assertTrue(hoverInfo.contains("item1"))
    }

    @Test
    fun testExtractHoverWithFloatingPointConstraints() {
        val hoverInfo = getHoverInfo("""
            {
                type: "number"
                minimum: 0.5
                maximum: 99.9
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Minimum:* 0.5"))
        assertTrue(hoverInfo.contains("*Maximum:* 99.9"))
    }

    @Test
    fun testExtractHoverWithMixedEnumTypes() {
        val hoverInfo = getHoverInfo("""
            {
                enum: ["string", 42, true, null]
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Allowed values:*"))
        assertTrue(hoverInfo.contains("`string`"))
        assertTrue(hoverInfo.contains("`42`"))
        assertTrue(hoverInfo.contains("`true`"))
        assertTrue(hoverInfo.contains("`null`"))
    }

    @Test
    fun testExtractHoverDoesNotIncludePropertiesOrItems() {
        // Hover info should focus on the schema itself, not list all sub-properties
        val hoverInfo = getHoverInfo("""
            {
                type: "object"
                description: "A user object"
                properties: {
                    name: { type: "string" }
                    age: { type: "integer" }
                }
            }
        """)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("A user object"))
        assertTrue(hoverInfo.contains("*Type:* `object`"))
        // Should not enumerate all properties in hover
    }
}

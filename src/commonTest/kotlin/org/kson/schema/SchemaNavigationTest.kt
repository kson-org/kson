package org.kson.schema

import org.kson.KsonCore
import org.kson.value.KsonValue as InternalKsonValue
import org.kson.value.KsonObject as InternalKsonObject
import org.kson.value.KsonString as InternalKsonString
import org.kson.value.KsonBoolean as InternalKsonBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SchemaNavigationTest {

    /**
     * Helper to navigate schema and get the result value
     */
    private fun navigateSchema(schema: String, path: List<String>): InternalKsonValue? {
        val resolved = KsonCore.parseToAst(schema).ksonValue?.let {
            SchemaIdLookup(it).navigateByDocumentPath(path)
        }
        return resolved?.resolvedValue
    }

    @Test
    fun testNavigateEmptyPath() {
        val schema = """
            {
                type: "object"
                properties: {
                    name: { type: "string" }
                }
            }
        """
        val parsedSchema = KsonCore.parseToAst(schema).ksonValue!!
        val result = navigateSchema(schema, emptyList())
        assertNotNull(result)
        assertEquals(parsedSchema, result )
    }

    @Test
    fun testNavigateSimpleObjectProperty() {
        val schema = """
            {
                type: "object"
                properties: {
                    name: {
                        type: "string"
                        description: "The name property"
                    }
                }
            }
        """

        val result = navigateSchema(schema, listOf("name"))
        assertNotNull(result)

        // Verify we got the correct schema node
        val nameSchema = result as InternalKsonObject
        assertEquals("string", (nameSchema.propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("The name property", (nameSchema.propertyLookup["description"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateNestedObjectProperties() {
        val schema = """
            {
                type: "object"
                properties: {
                    user: {
                        type: "object"
                        properties: {
                            name: {
                                type: "string"
                                description: "User name"
                            }
                        }
                    }
                }
            }
        """

        val result = navigateSchema(schema, listOf("user", "name"))
        assertNotNull(result)

        val nameSchema = result as InternalKsonObject
        assertEquals("string", (nameSchema.propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("User name", (nameSchema.propertyLookup["description"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateArrayItems() {
        val schema = """
            {
                type: "object"
                properties: {
                    users: {
                        type: "array"
                        items: {
                            type: "object"
                            properties: {
                                name: { type: "string" }
                            }
                        }
                    }
                }
            }
        """

        // Navigate to users[0] - should give us the items schema
        val itemSchema = navigateSchema(schema, listOf("users", "0"))
        assertNotNull(itemSchema)
        assertEquals(
            "object",
            ((itemSchema as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
        )

        // Navigate deeper: users[0].name
        val nameSchema = navigateSchema(schema, listOf("users", "0", "name"))
        assertNotNull(nameSchema)
        assertEquals(
            "string",
            ((nameSchema as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
        )
    }

    @Test
    fun testNavigateArrayItemsAnyIndex() {
        val schema = """
            {
                type: "object"
                properties: {
                    tags: {
                        type: "array"
                        items: { type: "string" }
                    }
                }
            }
        """

        // All indices should return the same items schema
        val item0 = navigateSchema(schema, listOf("tags", "0"))
        val item5 = navigateSchema(schema, listOf("tags", "5"))
        val item99 = navigateSchema(schema, listOf("tags", "99"))

        assertNotNull(item0)
        assertNotNull(item5)
        assertNotNull(item99)

        // They should all be the same schema (type: string)
        assertEquals("string", ((item0 as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("string", ((item5 as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("string", ((item99 as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateAdditionalProperties() {
        val schema = """
            {
                type: "object"
                properties: {
                    knownProp: { type: "string" }
                }
                additionalProperties: {
                    type: "number"
                    description: "Any other property is a number"
                }
            }
        """

        // Known property should use the specific schema
        val knownProp = navigateSchema(schema, listOf("knownProp"))
        assertNotNull(knownProp)
        assertEquals("string", ((knownProp as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // Unknown property should use additionalProperties
        val unknownProp = navigateSchema(schema, listOf("unknownProp"))
        assertNotNull(unknownProp)
        assertEquals(
            "number",
            ((unknownProp as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
        )
        assertEquals(
            "Any other property is a number",
            (unknownProp.propertyLookup["description"] as? InternalKsonString)?.value
        )
    }

    @Test
    fun testNavigatePatternProperties() {
        val schema = """
            {
                type: "object"
                patternProperties: {
                    "^age_.*": {
                        type: "integer"
                        description: "Age fields"
                    }
                    "^name_.*": {
                        type: "string"
                        description: "Name fields"
                    }
                }
            }
        """

        // Property matching age_ pattern
        val ageProp = navigateSchema(schema, listOf("age_child"))
        assertNotNull(ageProp)
        assertEquals("integer", ((ageProp as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("Age fields", (ageProp.propertyLookup["description"] as? InternalKsonString)?.value)

        // Property matching name_ pattern
        val nameProp = navigateSchema(schema, listOf("name_first"))
        assertNotNull(nameProp)
        assertEquals("string", ((nameProp as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("Name fields", (nameProp.propertyLookup["description"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateNonExistentProperty() {
        val schema = """
            {
                type: "object"
                properties: {
                    name: { type: "string" }
                }
                additionalProperties: false
            }
        """

        // Non-existent property with additionalProperties: false
        val result = navigateSchema(schema, listOf("nonexistent"))
        // Should return the additionalProperties value (false as KsonBoolean)
        assertNotNull(result)
        assertEquals(false, (result as? InternalKsonBoolean)?.value)
    }

    @Test
    fun testNavigateInvalidPath() {
        val schema = """
            {
                type: "object"
                properties: {
                    name: { type: "string" }
                }
            }
        """

        // Try to navigate deeper into a primitive type
        val result = navigateSchema(schema, listOf("name", "invalid", "path"))
        assertNull(result)
    }

    @Test
    fun testNavigateComplexNestedPath() {
        val schema = """
            {
                type: "object"
                properties: {
                    company: {
                        type: "object"
                        properties: {
                            departments: {
                                type: "array"
                                items: {
                                    type: "object"
                                    properties: {
                                        employees: {
                                            type: "array"
                                            items: {
                                                type: "object"
                                                properties: {
                                                    name: {
                                                        type: "string"
                                                        description: "Employee name"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        // Navigate: company.departments[0].employees[0].name
        val path = listOf("company", "departments", "0", "employees", "0", "name")
        val result = navigateSchema(schema, path)
        assertNotNull(result)

        val nameSchema = result as InternalKsonObject
        assertEquals("string", (nameSchema.propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("Employee name", (nameSchema.propertyLookup["description"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateWithSchemaId() {
        val schema = $$"""
            {
                '$id': "https://example.com/schema"
                type: "object"
                properties: {
                    name: {
                        '$id': "name-schema"
                        type: "string"
                    }
                }
            }
        """

        val result = navigateSchema(schema, listOf("name"))
        assertNotNull(result)

        // The base URI should be updated based on the $id
        // (This tests the URI tracking functionality)
        assertEquals(
            "string",
            ((result as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
        )
    }

    @Test
    fun testNavigateSchemaWithNoProperties() {
        val schema = """
            {
                type: "object"
            }
        """

        // Try to navigate to a property when none are defined
        val result = navigateSchema(schema, listOf("anyProp"))
        // Should return null since there's no properties or additionalProperties
        assertNull(result)
    }

    @Test
    fun testNavigateAdditionalItemsForArray() {
        val schema = """
            {
                type: "object"
                properties: {
                    tuple: {
                        type: "array"
                        additionalItems: {
                            type: "boolean"
                            description: "Extra items are booleans"
                        }
                    }
                }
            }
        """

        // Navigate to tuple array items - should use additionalItems
        val result = navigateSchema(schema, listOf("tuple", "0"))
        assertNotNull(result)
        assertEquals("boolean", ((result as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateWithInvalidRegexInPatternProperties() {
        // Test that invalid regex patterns are handled gracefully
        val schema = """
            {
                type: "object"
                patternProperties: {
                    "^[invalid(regex": { type: "string" }
                    "^valid_.*": { type: "number" }
                }
                additionalProperties: { type: "boolean" }
            }
        """

        // Should skip the invalid pattern and use additionalProperties
        val result = navigateSchema(schema, listOf("someProp"))
        assertNotNull(result)
        assertEquals("boolean", ((result as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // Valid pattern should still work
        val validResult = navigateSchema(schema, listOf("valid_prop"))
        assertNotNull(validResult)
        assertEquals(
            "number",
            ((validResult as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
        )
    }
}

package org.kson.schema

import org.kson.KsonCore
import org.kson.value.navigation.jsonPointer.JsonPointer
import org.kson.value.KsonValue as InternalKsonValue
import org.kson.value.KsonObject as InternalKsonObject
import org.kson.value.KsonString as InternalKsonString
import org.kson.value.KsonBoolean as InternalKsonBoolean
import kotlin.test.Test
import kotlin.test.assertEquals


class SchemaNavigationTest {

    /**
     * Helper to navigate schema and get all result values
     */
    private fun navigateSchema(schema: String, path: List<String>): List<InternalKsonValue> {
        return KsonCore.parseToAst(schema).ksonValue?.let {
            SchemaIdLookup(it).navigateByDocumentPointer(JsonPointer.fromTokens(path))
                .map { it.resolvedValue }
        } ?: emptyList()
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
        val results = navigateSchema(schema, emptyList())
        assertEquals(1, results.size, "Expected exactly one result for empty path")
        assertEquals(parsedSchema, results.single())
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

        val results = navigateSchema(schema, listOf("name"))
        assertEquals(1, results.size, "Expected exactly one result")

        // Verify we got the correct schema node
        val nameSchema = results.single() as InternalKsonObject
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

        val results = navigateSchema(schema, listOf("user", "name"))
        assertEquals(1, results.size, "Expected exactly one result")

        val nameSchema = results.single() as InternalKsonObject
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
        val itemResults = navigateSchema(schema, listOf("users", "0"))
        assertEquals(1, itemResults.size)
        assertEquals(
            "object",
            ((itemResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
        )

        // Navigate deeper: users[0].name
        val nameResults = navigateSchema(schema, listOf("users", "0", "name"))
        assertEquals(1, nameResults.size)
        assertEquals(
            "string",
            ((nameResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
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

        assertEquals(1, item0.size)
        assertEquals(1, item5.size)
        assertEquals(1, item99.size)

        // They should all be the same schema (type: string)
        assertEquals("string", ((item0.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("string", ((item5.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("string", ((item99.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
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
        assertEquals(1, knownProp.size)
        assertEquals("string", ((knownProp.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // Unknown property should use additionalProperties
        val unknownProp = navigateSchema(schema, listOf("unknownProp"))
        assertEquals(1, unknownProp.size)
        val unknownPropSchema = unknownProp.single() as InternalKsonObject
        assertEquals(
            "number",
            (unknownPropSchema.propertyLookup["type"] as? InternalKsonString)?.value
        )
        assertEquals(
            "Any other property is a number",
            (unknownPropSchema.propertyLookup["description"] as? InternalKsonString)?.value
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
        assertEquals(1, ageProp.size)
        val agePropSchema = ageProp.single() as InternalKsonObject
        assertEquals("integer", (agePropSchema.propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("Age fields", (agePropSchema.propertyLookup["description"] as? InternalKsonString)?.value)

        // Property matching name_ pattern
        val nameProp = navigateSchema(schema, listOf("name_first"))
        assertEquals(1, nameProp.size)
        val namePropSchema = nameProp.single() as InternalKsonObject
        assertEquals("string", (namePropSchema.propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("Name fields", (namePropSchema.propertyLookup["description"] as? InternalKsonString)?.value)
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
        val results = navigateSchema(schema, listOf("nonexistent"))
        // Should return the additionalProperties value (false as KsonBoolean)
        assertEquals(1, results.size)
        assertEquals(false, (results.single() as? InternalKsonBoolean)?.value)
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
        val results = navigateSchema(schema, listOf("name", "invalid", "path"))
        assertEquals(0, results.size, "Expected empty list for invalid path")
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
        val results = navigateSchema(schema, path)
        assertEquals(1, results.size)

        val nameSchema = results.single() as InternalKsonObject
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

        val results = navigateSchema(schema, listOf("name"))
        assertEquals(1, results.size)

        // The base URI should be updated based on the $id
        // (This tests the URI tracking functionality)
        assertEquals(
            "string",
            ((results.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
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
        val results = navigateSchema(schema, listOf("anyProp"))
        // Should return empty list since there's no properties or additionalProperties
        assertEquals(0, results.size)
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
        val results = navigateSchema(schema, listOf("tuple", "0"))
        assertEquals(1, results.size)
        assertEquals("boolean", ((results.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
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
        val results = navigateSchema(schema, listOf("someProp"))
        assertEquals(1, results.size)
        assertEquals("boolean", ((results.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // Valid pattern should still work
        val validResults = navigateSchema(schema, listOf("valid_prop"))
        assertEquals(1, validResults.size)
        assertEquals(
            "number",
            ((validResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value
        )
    }

    @Test
    fun testNavigateAnyOf() {
        val schema = """
            {
                type: "object"
                anyOf:
                  - properties:
                      name: { type: "string" }
                  - properties:
                      age: { type: "number" }
            }
        """

        // name is defined in first anyOf branch
        val nameResults = navigateSchema(schema, listOf("name"))
        assertEquals(1, nameResults.size)
        assertEquals("string", ((nameResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // age is defined in second anyOf branch
        val ageResults = navigateSchema(schema, listOf("age"))
        assertEquals(1, ageResults.size)
        assertEquals("number", ((ageResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateOneOf() {
        val schema = """
            {
                type: "object"
                oneOf:
                  - properties:
                      id: { type: "integer" }
                  - properties:
                      uuid: { type: "string" }
            }
        """

        // id is defined in first oneOf branch
        val idResults = navigateSchema(schema, listOf("id"))
        assertEquals(1, idResults.size)
        assertEquals("integer", ((idResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // uuid is defined in second oneOf branch
        val uuidResults = navigateSchema(schema, listOf("uuid"))
        assertEquals(1, uuidResults.size)
        assertEquals("string", ((uuidResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateAllOf() {
        val schema = """
            {
                type: "object"
                allOf:
                  - properties:
                      name: { type: "string" }
                  - properties:
                      email: { type: "string", format: "email" }
            }
        """

        // name is defined in first allOf branch
        val nameResults = navigateSchema(schema, listOf("name"))
        assertEquals(1, nameResults.size)
        assertEquals("string", ((nameResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // email is defined in second allOf branch
        val emailResults = navigateSchema(schema, listOf("email"))
        assertEquals(1, emailResults.size)
        val emailSchema = emailResults.single() as InternalKsonObject
        assertEquals("string", (emailSchema.propertyLookup["type"] as? InternalKsonString)?.value)
        assertEquals("email", (emailSchema.propertyLookup["format"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateAnyOfWithMultipleMatches() {
        val schema = """
            {
                type: "object"
                anyOf:
                  - properties:
                      name: { type: "string", minLength: 1 }
                  - properties:
                      name: { type: "string", maxLength: 100 }
            }
        """

        // name is defined in BOTH anyOf branches - should return both
        val nameResults = navigateSchema(schema, listOf("name"))
        assertEquals(2, nameResults.size, "Expected two results for property defined in multiple anyOf branches")

        // Both should be string type
        nameResults.forEach { result ->
            assertEquals("string", ((result as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
        }
    }

    @Test
    fun testNavigateAnyOfWithRef() {
        val schema = """
            '${'$'}defs':
              StringType:
                type: string
                minLength: 1
              NumberType:
                type: number
                minimum: 0
            anyOf:
              - '${'$'}ref': '#/${'$'}defs/StringType'
              - '${'$'}ref': '#/${'$'}defs/NumberType'
        """

        // This test verifies that navigation works when anyOf contains $ref
        val results = navigateSchema(schema, emptyList())
        assertEquals(1, results.size, "Root schema should return single result")
    }

    @Test
    fun testNavigateAnyOfWithArray() {
        val schema = """
            '${'$'}defs':
              StringType:
                type: string
                minLength: 1
                .
              NumberType:
                type: number
                minimum: 0
                .
              .
            anyOf:
              - '${'$'}ref': '#/${'$'}defs/StringType'
              - type: array
                items:
                  anyOf:
                    - '${'$'}ref': '#/${'$'}defs/NumberType'
        """

        // This test verifies that navigation works when anyOf contains $ref
        val results = navigateSchema(schema, listOf("0"))
        assertEquals(1, results.size, "Root schema should return single result")
    }

    @Test
    fun testNavigateDefs() {
        // This is the original failing test - property defined deep in a $ref within anyOf
        val schema = """
            '${'$'}defs':
              ComplexRecipe:
                additionalProperties: false
                properties:
                  context:
                    anyOf:
                      - type: object
                      - type: 'null'
                        .
                    default: null
                    description: 'Defines arbitrary key-value pairs for Jinja interpolation'
                    title: Context
                    .
                  source:
                    type: string
                    .
                  .
                title: ComplexRecipe
                type: object
                .
              .
            anyOf:
              - '${'$'}ref': '#/${'$'}defs/ComplexRecipe'
        """

        val results = navigateSchema(schema, listOf("context"))
        assertEquals(1, results.size, "Should find context property through anyOf → \$ref")

        val contextSchema = results.single() as InternalKsonObject
        assertEquals("Context", (contextSchema.propertyLookup["title"] as? InternalKsonString)?.value)
        assertEquals("Defines arbitrary key-value pairs for Jinja interpolation",
                     (contextSchema.propertyLookup["description"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateNestedCombinators() {
        val schema = """
            {
                type: "object"
                anyOf:
                  - allOf:
                      - properties:
                          name: { type: "string" }
                      - properties:
                          email: { type: "string" }
            }
        """

        // name is in anyOf → allOf → properties
        val nameResults = navigateSchema(schema, listOf("name"))
        assertEquals(1, nameResults.size)
        assertEquals("string", ((nameResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // email is in anyOf → allOf → properties
        val emailResults = navigateSchema(schema, listOf("email"))
        assertEquals(1, emailResults.size)
        assertEquals("string", ((emailResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
    }

    @Test
    fun testNavigateMixedPropertiesAndCombinators() {
        val schema = """
            {
                type: "object"
                properties:
                    id: { type: "integer" }
                    .
                allOf:
                    - properties:
                        name: { type: "string" }
                    - properties:
                        email: { type: "string" }
            }
        """

        // id is in direct properties
        val idResults = navigateSchema(schema, listOf("id"))
        assertEquals(1, idResults.size)
        assertEquals("integer", ((idResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // name is in allOf
        val nameResults = navigateSchema(schema, listOf("name"))
        assertEquals(1, nameResults.size)
        assertEquals("string", ((nameResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)

        // email is in allOf
        val emailResults = navigateSchema(schema, listOf("email"))
        assertEquals(1, emailResults.size)
        assertEquals("string", ((emailResults.single() as InternalKsonObject).propertyLookup["type"] as? InternalKsonString)?.value)
    }
}

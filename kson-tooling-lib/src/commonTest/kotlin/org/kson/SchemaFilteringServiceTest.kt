package org.kson

import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.schema.SchemaIdLookup
import org.kson.value.KsonValue
import kotlin.test.*

class SchemaFilteringServiceTest {

    private fun getValidSchemasForDocument(
        schema: String,
        document: String,
        documentPointer: JsonPointer = JsonPointer("")
    ): List<KsonValue> {
        val parsedSchema = KsonCore.parseToAst(schema).ksonValue ?: fail("Schema should parse")
        val parsedDocument = KsonCore.parseToAst(document).ksonValue
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val filteringService = SchemaFilteringService(schemaIdLookup)
        val candidateSchemas = schemaIdLookup.navigateByDocumentPointer(documentPointer)
        return filteringService.getValidSchemas(candidateSchemas, parsedDocument, documentPointer).map { it.resolvedValue }
    }

    @Test
    fun testGetValidSchemas_withOneOfCombinator_filtersIncompatibleSchemas() {
        val schema = """
            oneOf:
              - type: object
                properties:
                  type:
                    const: email
                  recipient:
                    type: string
              - type: object
                properties:
                  type:
                    const: sms
                  phoneNumber:
                    type: string
        """.trimIndent()

        val document = """
            type: email
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        assertTrue(validSchemas.size >= 1, "Should have at least 1 valid schema")
    }

    @Test
    fun testGetValidSchemas_withAnyOfCombinator_filtersIncompatibleSchemas() {
        val schema = """
            anyOf:
              - type: object
                properties:
                  name:
                    type: string
                  age:
                    type: number
              - type: object
                properties:
                  name:
                    type: string
                  role:
                    type: string
        """.trimIndent()

        val document = """
            name: John
            age: 30
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        assertTrue(validSchemas.size >= 1, "Should have at least 1 valid schema")
    }

    @Test
    fun testGetValidSchemas_withAllOfCombinator_includesAllBranches() {
        val schema = """
            allOf:
              - type: object
                properties:
                  name:
                    type: string
              - type: object
                properties:
                  age:
                    type: number
        """.trimIndent()

        val document = """
            name: John
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        assertEquals(3, validSchemas.size, "allOf should include parent + all branches without filtering")
    }

    @Test
    fun testGetValidSchemas_withInvalidDocument_fallsBackToUnfilteredSchemas() {
        val schema = """
            oneOf:
              - type: string
              - type: number
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, "{ invalid json")

        assertEquals(3, validSchemas.size, "Should return parent + all branches when document doesn't parse")
    }

    @Test
    fun testGetValidSchemas_withNoCombinators_returnsAllSchemas() {
        val schema = """
            type: object
            properties:
              name:
                type: string
              age:
                type: number
        """.trimIndent()

        val document = """
            name: John
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        assertEquals(1, validSchemas.size, "Should return all schemas when no combinators")
    }

    @Test
    fun testGetValidSchemas_ignoresRequiredPropertyErrors() {
        val schema = """
            oneOf:
              - type: object
                required: [name, email]
                properties:
                  name:
                    type: string
                  email:
                    type: string
              - type: object
                required: [name, phone]
                properties:
                  name:
                    type: string
                  phone:
                    type: string
        """.trimIndent()

        val document = """
            name: John
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        assertEquals(3, validSchemas.size, "Parent + both branches should be valid - missing required props are ignored")
    }

    @Test
    fun testGetValidSchemas_withNonRootPointerToMissingValue_returnsAllBranches() {
        val schema = """
            type: object
            properties:
              query:
                oneOf:
                  - type: string
                  - type: number
        """.trimIndent()

        val document = """
            name: test
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document, JsonPointer("/query"))

        assertEquals(3, validSchemas.size, "Parent + both oneOf branches should be returned when target value doesn't exist yet")
    }

    @Test
    fun testGetValidSchemas_filtersBasedOnTypeViolations() {
        val schema = """
            oneOf:
              - type: object
                properties:
                  value:
                    type: string
              - type: object
                properties:
                  value:
                    type: number
        """.trimIndent()

        val document = """
            value: hello
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        assertEquals(2, validSchemas.size, "Should match parent + the string branch")
    }

    @Test
    fun testGetValidSchemas_withTypeMismatchAtTarget_filtersOutIncompatibleBranches() {
        val schema = """
            oneOf:
              - type: object
                properties:
                  field:
                    type: string
              - type: object
                properties:
                  and:
                    type: array
        """.trimIndent()

        val document = """
            - item
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        assertEquals(1, validSchemas.size, "Only parent schema should remain when target type doesn't match any branch")
    }
}

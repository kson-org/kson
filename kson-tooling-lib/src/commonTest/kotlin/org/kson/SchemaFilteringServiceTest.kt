package org.kson

import org.kson.schema.SchemaIdLookup
import org.kson.tooling.KsonTooling
import org.kson.tooling.SchemaFilteringService
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.JsonPointer
import kotlin.test.*

/**
 * Unit tests for [SchemaFilteringService]
 *
 * These tests focus on the schema filtering logic in isolation,
 * without going through the full public API.
 */
class SchemaFilteringServiceTest {

    /**
     * Parses [schema] and [document], navigates to [documentPointer] in the schema,
     * and returns the valid schemas after [SchemaFilteringService] has expanded
     * combinators and filtered incompatible branches.
     */
    private fun getValidSchemasForDocument(
        schema: String,
        document: String,
        documentPointer: JsonPointer = JsonPointer("")
    ): List<KsonValue> {
        val parsedSchema = KsonCore.parseToAst(schema).ksonValue ?: fail("Schema should parse")
        val parsedDocument = KsonTooling.parse(document)
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val filteringService = SchemaFilteringService(schemaIdLookup)
        val candidateSchemas = schemaIdLookup.navigateByDocumentPointer(documentPointer, parsedDocument.ksonValue)
        return filteringService.getValidSchemas(candidateSchemas, parsedDocument, documentPointer).map { it.resolvedValue }
    }

    /**
     * Reads the string value at `properties.<propertyName>.<fieldName>` for a
     * schema branch, or null if the branch doesn't have one. Used to identify
     * which branch of a `oneOf` survived filtering by reading a discriminating
     * sub-field of a property's schema (e.g. `properties.type.const` or
     * `properties.value.type`).
     */
    private fun propertyFieldOf(
        schema: KsonValue,
        propertyName: String,
        fieldName: String
    ): String? {
        val obj = schema as? KsonObject ?: return null
        val properties = obj.propertyLookup["properties"] as? KsonObject ?: return null
        val prop = properties.propertyLookup[propertyName] as? KsonObject ?: return null
        return (prop.propertyLookup[fieldName] as? KsonString)?.value
    }

    @Test
    fun testGetValidSchemas_withOneOfCombinator_filtersIncompatibleSchemas() {
        // Two branches discriminated by `type` const. Document says `type: email`,
        // so only the email branch should survive; the sms branch must be dropped.
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

        // Parent + email branch (sms filtered out)
        assertEquals(2, validSchemas.size, "Should return parent + email branch only")
        val survivingConsts = validSchemas.mapNotNull { propertyFieldOf(it, "type", "const") }
        assertEquals(listOf("email"), survivingConsts, "Only the email branch should survive")
    }

    @Test
    fun testGetValidSchemas_withAnyOfCombinator_bothBranchesCompatible() {
        // Document has `name` and `age`. Branch 1 describes name+age, branch 2 describes
        // name+role. Both branches are compatible because missing required props are
        // ignored and there's no additionalProperties:false on branch 2. So both survive.
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

        // Parent + both branches — nothing to filter out.
        assertEquals(3, validSchemas.size, "Should return parent + both anyOf branches (both are compatible)")
    }

    @Test
    fun testGetValidSchemas_withAllOfCombinator_includesAllBranches() {
        // allOf never filters — all branches must hold simultaneously, so the
        // filter returns them all verbatim (plus the parent schema).
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
        // When the document doesn't parse, the filter has nothing to validate against
        // and must fall back to the fully expanded schema list (parent + all branches).
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
        // No combinators means no filtering — the single candidate passes through.
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

        assertEquals(1, validSchemas.size, "Should return a single schema when there are no combinators")
    }

    @Test
    fun testGetValidSchemas_ignoresRequiredPropertyErrors() {
        // Document has only `name`, but both branches require additional properties.
        // Missing-required errors are expected during completion and must not
        // disqualify a branch, so both survive.
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

        assertEquals(
            3, validSchemas.size,
            "Parent + both branches should be valid — missing required props are ignored"
        )
    }

    @Test
    fun testGetValidSchemas_withNonRootPointerToMissingValue_returnsAllBranches() {
        // Navigation to /query returns null because the document doesn't have
        // a `query` key yet. The filter must return all expanded candidates
        // rather than validating against the root document (which would fail
        // additionalProperties:false on every branch).
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

        assertEquals(
            3, validSchemas.size,
            "Parent + both oneOf branches should be returned when the target value doesn't exist yet"
        )
    }

    @Test
    fun testGetValidSchemas_filtersBasedOnTypeViolations() {
        // Document has `value: hello` (a string). Only the string branch is compatible;
        // the number branch is filtered out.
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

        assertEquals(2, validSchemas.size, "Should match parent + exactly one branch")
        val survivingValueTypes = validSchemas.mapNotNull { propertyFieldOf(it, "value", "type") }
        assertEquals(
            listOf("string"), survivingValueTypes,
            "Only the string branch should survive; the number branch must be filtered out"
        )
    }

    @Test
    fun testGetValidSchemas_withIfThen_filtersIncompatibleConditionalBranches() {
        // allOf with if/then blocks that select a $ref based on a sibling property.
        // params is only reachable via if/then (no anyOf), isolating the filtering.
        val schema = """
            {
                "${'$'}defs": {
                    "DogParams": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                            "treats": { "type": "integer" }
                        }
                    },
                    "CatParams": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                            "naps": { "type": "integer" }
                        }
                    }
                },
                "type": "object",
                "properties": {
                    "kind": { "type": "string" }
                },
                "allOf": [
                    {
                        "if": { "properties": { "kind": { "const": "dog" } } },
                        "then": { "properties": { "params": { "${'$'}ref": "#/${'$'}defs/DogParams" } } }
                    },
                    {
                        "if": { "properties": { "kind": { "const": "cat" } } },
                        "then": { "properties": { "params": { "${'$'}ref": "#/${'$'}defs/CatParams" } } }
                    }
                ]
            }
        """.trimIndent()

        val document = """
            {
                "kind": "dog",
                "params": {
                    "treats": 5
                }
            }
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document, JsonPointer("/params"))

        assertEquals(1, validSchemas.size, "Only DogParams should survive filtering")

        val schema0 = validSchemas.single() as org.kson.value.KsonObject
        val propertyNames = (schema0.propertyLookup["properties"] as? org.kson.value.KsonObject)?.propertyLookup?.keys ?: emptySet()
        assertTrue("treats" in propertyNames, "DogParams properties should be present, got: $propertyNames")
    }

    @Test
    fun testGetValidSchemas_withTypeMismatchAtTarget_filtersOutAllBranches() {
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

        // Only the parent oneOf container remains — both branches expect objects
        // but the document is a list, so neither branch is compatible.
        assertEquals(1, validSchemas.size, "Only the parent oneOf should remain when no branch matches the document type")
    }
}

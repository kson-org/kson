package org.kson

import org.kson.schema.SchemaIdLookup
import org.kson.tooling.CompletionKind
import org.kson.tooling.KsonTooling
import org.kson.tooling.navigation.SchemaNavigator
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.JsonPointer
import kotlin.test.*

/**
 * Tests for combinator/conditional branch narrowing during schema navigation.
 *
 * Narrowing is doc-aware and lives entirely in [org.kson.tooling.navigation.SchemaNavigator]
 * (`SchemaNavigator.flatten`): each oneOf/anyOf/if-then branch is dropped
 * where it is reached if it contradicts the document at that level.  These tests exercise
 * that narrowing directly via navigation, without going through the full public API.
 */
class SchemaBranchNarrowingTest : SchemaCompletionTest {

    /**
     * Parses [schema] and [document], then navigates to [documentPointer] in the schema,
     * returning the branches that survive doc-aware narrowing.
     */
    private fun getValidSchemasForDocument(
        schema: String,
        document: String,
        documentPointer: JsonPointer = JsonPointer("")
    ): List<KsonValue> {
        val parsedSchema = KsonCore.parseToAst(schema).ksonValue ?: fail("Schema should parse")
        val parsedDocument = KsonTooling.parse(document)
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        return SchemaNavigator(schemaIdLookup).navigate(documentPointer, parsedDocument.ksonValue)
            .map { it.resolvedValue }
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

    /** Union of `properties` keys offered across all surviving branches — the completion candidates. */
    private fun offeredPropertyNames(schemas: List<KsonValue>): Set<String> =
        schemas.filterIsInstance<KsonObject>()
            .mapNotNull { it.propertyLookup["properties"] as? KsonObject }
            .flatMap { it.propertyLookup.keys }
            .toSet()

    /**
     * Real-world repro shape: `config` is `anyOf[ allOf[Base, oneOf[per-discriminator branch]] ]`,
     * each branch selecting a `$ref` Model by an `integration` discriminator, each Model carrying
     * its own `required`.  A partially-filled (or empty) `params` must still narrow to the matching
     * Model's properties.
     */
    private fun discriminatedConfigSchema(): String = """
        {
            "${'$'}defs": {
                "AlphaModel": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["job_type"],
                    "properties": {
                        "job_type": { "type": "string" },
                        "workspace_id": { "type": "string" }
                    }
                },
                "BetaModel": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["region"],
                    "properties": {
                        "region": { "type": "string" }
                    }
                }
            },
            "type": "object",
            "properties": {
                "config": {
                    "anyOf": [
                        {
                            "allOf": [
                                { "properties": { "integration": { "type": "string" } } },
                                {
                                    "oneOf": [
                                        {
                                            "required": ["integration", "params"],
                                            "properties": {
                                                "integration": { "const": "ALPHA" },
                                                "params": { "${'$'}ref": "#/${'$'}defs/AlphaModel" }
                                            }
                                        },
                                        {
                                            "required": ["integration", "params"],
                                            "properties": {
                                                "integration": { "const": "BETA" },
                                                "params": { "${'$'}ref": "#/${'$'}defs/BetaModel" }
                                            }
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
            }
        }
    """.trimIndent()

    @Test
    fun testGetValidSchemas_partiallyFilledDiscriminatedSubObject_offersRemainingModelProps() {
        // `integration` set and `params` partially filled inside allOf[Base, oneOf[...]]: the matching
        // Model must still offer its remaining properties — its own `required` is incompleteness, not
        // a contradiction, so the branch survives partial validation.
        val document = """
            config:
              integration: ALPHA
              params:
                job_type: sync
        """.trimIndent()

        val schemas = getValidSchemasForDocument(discriminatedConfigSchema(), document, JsonPointer("/config/params"))
        val offered = offeredPropertyNames(schemas)
        assertTrue("job_type" in offered && "workspace_id" in offered, "AlphaModel props should be offered, got: $offered")
        assertTrue("region" !in offered, "BetaModel props must not leak in, got: $offered")
    }

    @Test
    fun testGetValidSchemas_emptyDiscriminatedSubObject_offersModelProps() {
        // `params: {}` is empty but present: the matching Model still offers its properties.
        val document = """
            config:
              integration: ALPHA
              params: {}
        """.trimIndent()

        val schemas = getValidSchemasForDocument(discriminatedConfigSchema(), document, JsonPointer("/config/params"))
        val offered = offeredPropertyNames(schemas)
        assertTrue("job_type" in offered && "workspace_id" in offered, "AlphaModel props should be offered, got: $offered")
        assertTrue("region" !in offered, "BetaModel props must not leak in, got: $offered")
    }

    @Test
    fun testGetValidSchemas_discriminatorSelectsMatchingModelOnly_guardrail() {
        // GUARDRAIL: integration AIRBYTE_CLOUD-style discriminator resolves to exactly its branch's
        // params, never another integration's.  Here BETA is selected, so only `region` is offered.
        val document = """
            config:
              integration: BETA
              params:
                region: us
        """.trimIndent()

        val schemas = getValidSchemasForDocument(discriminatedConfigSchema(), document, JsonPointer("/config/params"))
        val offered = offeredPropertyNames(schemas)
        assertTrue("region" in offered, "BetaModel props should be offered, got: $offered")
        assertTrue("job_type" !in offered && "workspace_id" !in offered, "AlphaModel props must not leak in, got: $offered")
    }

    @Test
    fun testGetValidSchemas_unknownDiscriminatorValue_resolvesToNoModel_guardrail() {
        // GUARDRAIL: an integration value matching no branch's `const` resolves to no Model at all —
        // every oneOf branch is actively contradicted, so none survive to offer params.
        val document = """
            config:
              integration: NOT_A_REAL_VALUE
              params:
                job_type: sync
        """.trimIndent()

        val schemas = getValidSchemasForDocument(discriminatedConfigSchema(), document, JsonPointer("/config/params"))
        val offered = offeredPropertyNames(schemas)
        assertTrue(
            "job_type" !in offered && "workspace_id" !in offered && "region" !in offered,
            "No Model's props should be offered for an unknown discriminator, got: $offered"
        )
    }

    @Test
    fun testGetValidSchemas_presentValueOfWrongType_dropsBranch_guardrail() {
        // GUARDRAIL: `integration` is declared `type: string` in Base; a number actively contradicts
        // it, dropping the whole anyOf branch even in partial mode, so no Model props are offered.
        val document = """
            config:
              integration: 5
              params:
                job_type: sync
        """.trimIndent()

        val schemas = getValidSchemasForDocument(discriminatedConfigSchema(), document, JsonPointer("/config/params"))
        val offered = offeredPropertyNames(schemas)
        assertTrue(
            "job_type" !in offered && "workspace_id" !in offered && "region" !in offered,
            "A wrong-typed present value must drop the branch, got: $offered"
        )
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
    fun testGetValidSchemas_branchDiscriminatedByRequired_doesNotNarrow() {
        // Branches discriminate via `required`, not via `properties.<name>.const/enum/type`.
        // Sibling-compat filtering only inspects the branch's `properties` map, so the
        // `required` arrays cannot narrow during completion — only the `a`-branch is
        // satisfied by the document, but both branches still survive.  Pins the
        // documented limitation in `isBranchCompatibleWithSiblings`.
        val schema = """
            oneOf:
              - required: [a]
                properties:
                  payload:
                    type: string
              - required: [b]
                properties:
                  payload:
                    type: number
        """.trimIndent()

        val document = """
            a: present
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)
        val survivingPayloadTypes = validSchemas.mapNotNull { propertyFieldOf(it, "payload", "type") }
        assertEquals(
            listOf("number", "string"), survivingPayloadTypes.sorted(),
            "Both branches survive: required-based discrimination does not narrow during completion"
        )
    }

    @Test
    fun testGetValidSchemas_whenEveryBranchContradictsSiblings_dropsAllBranches() {
        // Branches gate on `kind` const. Document says `kind: gamma`, which matches
        // neither branch. Completing /payload, sibling-compat filtering must drop
        // both branches' payload schemas — no silent fallback to the unfiltered set,
        // which would surface completions from incompatible branches.
        val schema = """
            oneOf:
              - properties:
                  kind:
                    const: alpha
                  payload:
                    type: string
              - properties:
                  kind:
                    const: beta
                  payload:
                    type: number
        """.trimIndent()

        val document = """
            kind: gamma
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document, JsonPointer("/payload"))
        val survivingPayloadTypes = validSchemas.mapNotNull { (it as? KsonObject)?.propertyLookup?.get("type") as? KsonString }
            .map { it.value }
        assertEquals(
            emptyList(), survivingPayloadTypes,
            "No branch's payload schema should survive when sibling kind matches no branch's const"
        )
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

    @Test
    fun testCommittedKeyCountStillNarrowsWhileEditingValue() {
        // Three keys are committed; the maxProperties: 2 branch is contradicted by the keys alone,
        // so its enum values must not be offered while the third value is being typed.
        val schema = """
            {
              oneOf: [
                {
                  title: "compact"
                  type: "object"
                  maxProperties: 2
                  properties: { color: { enum: ["red", "green"] } }
                }
                {
                  title: "full"
                  type: "object"
                  properties: {
                    a: { type: "string" }
                    b: { type: "string" }
                    color: { enum: ["blue"] }
                  }
                }
              ]
            }
        """
        val document = """
            a: x
            b: y
            color: "r<caret>"
        """.trimIndent()
        val completions = getCompletionsAtCaret(schema, document)
        assertNotNull(completions)
        assertEquals(
            listOf("blue"),
            completions.filter { it.kind == CompletionKind.VALUE }.map { it.label },
            "Only the branch compatible with the committed keys should offer values"
        )
    }

    @Test
    fun testIfDiscriminatorMidTypingOffersBothBranches() {
        // The caret is mid-typing the discriminator itself ("f" toward "fast"): neither the then
        // nor the else branch is decidable yet, so both branches' values are offered.
        val schema = """
            {
              type: "object"
              properties: { mode: { type: "string" } }
              if: { properties: { mode: { const: "fast" } }, required: ["mode"] }
              then: { properties: { mode: { enum: ["fast"] } } }
              else: { properties: { mode: { enum: ["slow", "medium"] } } }
            }
        """
        val completions = getCompletionsAtCaret(schema, """mode: "f<caret>"""")
        assertNotNull(completions)
        val labels = completions.map { it.label }
        assertTrue("fast" in labels, "then-branch value should be offered, got: $labels")
        assertTrue("slow" in labels && "medium" in labels, "else-branch values should be offered, got: $labels")
    }
}

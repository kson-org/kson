package org.kson.schema

import org.kson.KsonCore
import org.kson.schema.SchemaDecisions.accepts
import org.kson.schema.SchemaDecisions.breaks
import org.kson.schema.SchemaDecisions.narrowBranches
import org.kson.schema.SchemaDecisions.narrowOnDiscriminators
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaDecisionsTest {
    private fun kson(source: String): KsonValue =
        assertNotNull(KsonCore.parseToAst(source).ksonValue, "should parse: $source")

    private fun compiled(schema: String): JsonSchema {
        val node = kson(schema)
        return assertNotNull(SchemaDecisions.compile(node, "", SchemaIdLookup(node)), "should compile: $schema")
    }

    private fun locationOf(property: String, document: KsonValue) =
        (document as KsonObject).propertyLookup.getValue(property).location

    private fun branchesOf(union: String): List<JsonSchema> {
        val root = kson(union) as KsonObject
        val idLookup = SchemaIdLookup(root)
        return (root.propertyLookup.getValue("oneOf") as KsonList).elements.map {
            assertNotNull(SchemaDecisions.compile(it, idLookup.rootBaseUri, idLookup), "should compile: $it")
        }
    }

    private val shapes = branchesOf(
        """
            oneOf:
              - properties:
                  kind:
                    const: circle
                    .
                  radius:
                    type: number

              - properties:
                  kind:
                    const: square
                    .
                  side:
                    type: number

              - properties:
                  kind:
                    const: triangle
                    .
                  base:
                    type: number
        """.trimIndent()
    )

    /** Asserts order too: rejected properties and their values are reported in the order they are listed. */
    private fun assertNoBranchFits(decision: PinDecision, vararg rejected: Pair<String, List<String>>) {
        val noBranchFits = assertIs<PinDecision.NoBranchFits>(decision)
        assertEquals(
            rejected.map { (property, values) -> property to values.map(::kson) },
            noBranchFits.rejected.map { (property, values) -> property to values.toList() }
        )
    }

    private fun assertSelected(decision: PinDecision, chosen: List<Int>) {
        val selected = assertIs<PinDecision.Selected>(decision)
        assertEquals(chosen, selected.chosen, "chosen")
    }

    private fun assertOpen(decision: PinDecision, survivors: List<Int>) {
        val open = assertIs<PinDecision.Open>(decision)
        assertEquals(survivors, open.survivors, "survivors")
    }

    @Test
    fun compileFollowsARef() {
        val schema = compiled(
            $$"""
                '$ref': '#/$defs/count'
                '$defs':
                  count:
                    type: number
            """.trimIndent()
        )

        assertFalse(breaks(schema, kson("3")))
        assertTrue(breaks(schema, kson("three")))
    }

    @Test
    fun compileReadsRefsUnderTheBaseUriItIsGiven() {
        val root = kson(
            $$"""
                '$id': 'https://example.org/root'
                '$ref': '#/$defs/count'
                '$defs':
                  count: {}
            """.trimIndent()
        )
        val idLookup = SchemaIdLookup(root)

        assertNotNull(SchemaDecisions.compile(root, "https://example.org/root", idLookup))
        assertNull(SchemaDecisions.compile(root, "https://example.org/elsewhere", idLookup))
    }

    @Test
    fun compileAnswersNullForWhatIsNoSchema() {
        val notASchema = kson("3")
        assertNull(SchemaDecisions.compile(notASchema, "", SchemaIdLookup(notASchema)))

        val danglingRef = kson($$"'$ref': '#/$defs/missing'")
        assertNull(SchemaDecisions.compile(danglingRef, "", SchemaIdLookup(danglingRef)))
    }

    @Test
    fun compileThrowsOnAMalformedRefPointer() {
        val malformedPointer = kson($$"'$ref': '#/$defs/a~2b'")

        assertFailsWith<IllegalArgumentException> {
            SchemaDecisions.compile(malformedPointer, "", SchemaIdLookup(malformedPointer))
        }
    }

    @Test
    fun compileLeavesAMalformedKeywordUnconstraining() {
        val schema = compiled(
            """
                type: 5
                const: circle
            """.trimIndent()
        )

        assertFalse(breaks(schema, kson("circle")))
        assertTrue(breaks(schema, kson("square")))
    }

    @Test
    fun compileAcceptsBooleanSchemas() {
        assertFalse(breaks(compiled("true"), kson("anything")))
        assertTrue(breaks(compiled("false"), kson("anything")))
    }

    @Test
    fun aValueThatFitsDoesNotBreak() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertFalse(breaks(schema, kson("kind: circle")))
    }

    @Test
    fun whatIsOnlyMissingDoesNotBreak() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertFalse(breaks(schema, kson("radius: 3")))
    }

    @Test
    fun aPresentValueTheSchemaForbidsBreaks() {
        assertTrue(breaks(compiled("type: object"), kson("[1, 2]")))
        assertTrue(breaks(compiled("properties: { kind: { const: circle } }"), kson("kind: square")))
        assertTrue(breaks(compiled("additionalProperties: false"), kson("radius: 3")))
    }

    @Test
    fun aProblemInsideTheForgivenSpanDoesNotBreak() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
            """.trimIndent()
        )
        val document = kson("kind: cir")

        assertTrue(breaks(schema, document))
        assertFalse(breaks(schema, document, forgiving = locationOf("kind", document)))
    }

    @Test
    fun aProblemOutsideTheForgivenSpanStillBreaks() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
                    .
                  radius:
                    type: number
            """.trimIndent()
        )
        val document = kson(
            """
                kind: cir
                radius: big
            """.trimIndent()
        )

        assertTrue(breaks(schema, document, forgiving = locationOf("kind", document)))
    }

    @Test
    fun aValueThatFitsIsAccepted() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertTrue(accepts(schema, kson("kind: circle")))
    }

    @Test
    fun aPresentValueTheSchemaForbidsIsNotAccepted() {
        assertFalse(accepts(compiled("type: object"), kson("[1, 2]")))
        assertFalse(accepts(compiled("properties: { kind: { const: circle } }"), kson("kind: square")))
        assertFalse(accepts(compiled("additionalProperties: false"), kson("radius: 3")))
    }

    @Test
    fun whatIsOnlyUnfinishedDoesNotBreakButIsNotAccepted() {
        val unfinished = listOf(
            "required: [kind]" to "radius: 3",
            "minProperties: 2" to "radius: 3",
            "minItems: 2" to "[1]",
            "minLength: 2" to "a",
            "contains: { const: 1 }" to "[2]",
            "dependencies: { radius: [kind] }" to "radius: 3",
            "not: { type: string }" to "a",
            "oneOf: [true, true]" to "a"
        )

        for ((source, document) in unfinished) {
            val schema = compiled(source)
            val value = kson(document)

            assertFalse(breaks(schema, value), "breaks: $source")
            assertFalse(accepts(schema, value), "accepts: $source")
        }
    }

    @Test
    fun aMatchingPinDropsTheOthersAndSelectsItsBranch() {
        assertSelected(narrowBranches(shapes, kson("kind: square")), chosen = listOf(1))
    }

    @Test
    fun anEnumPinAdmitsEachOfItsValues() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        enum:
                          - circle
                          - ellipse
                          =
                  - properties:
                      kind:
                        const: square
            """.trimIndent()
        )

        assertSelected(narrowBranches(branches, kson("kind: ellipse")), chosen = listOf(0))
    }

    @Test
    fun aPinThatAdmitsNothingIsContradictedByAnyPresentValue() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        enum: []
                  - properties:
                      kind:
                        const: square
            """.trimIndent()
        )

        assertNoBranchFits(narrowBranches(branches, kson("kind: Z")), "kind" to listOf("square"))
    }

    @Test
    fun pinsAreReadThroughARefAndAllOf() {
        val branches = branchesOf(
            $$"""
                oneOf:
                  - '$ref': '#/$defs/Circle'
                  - allOf:
                      - properties:
                          kind:
                            const: square
                            .
                          .
                        .
                    .
                '$defs':
                  Circle:
                    properties:
                      kind:
                        const: circle
            """.trimIndent()
        )

        assertSelected(narrowBranches(branches, kson("kind: circle")), chosen = listOf(0))
        assertSelected(narrowBranches(branches, kson("kind: square")), chosen = listOf(1))
    }

    @Test
    fun onlyPinsNarrow() {
        val wrongRadius = kson(
            """
                kind: circle
                radius: big
            """.trimIndent()
        )

        assertSelected(narrowBranches(shapes, wrongRadius), chosen = listOf(0))
    }

    @Test
    fun aSurvivorWithoutThePinStaysAmongTheOthers() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: circle
                  - properties:
                      name:
                        type: string
            """.trimIndent()
        )

        assertSelected(narrowBranches(branches, kson("kind: circle")), chosen = listOf(0))
    }

    @Test
    fun aPinSharedBySurvivorsChoosesNothing() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                  - properties:
                      kind:
                        const: A
                  - properties:
                      kind:
                        const: B
            """.trimIndent()
        )

        assertOpen(narrowBranches(branches, kson("kind: A")), survivors = listOf(0, 1))
    }

    @Test
    fun aPinSharedOnlyWithADroppedBranchStillChooses() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      version:
                        const: 1

                  - properties:
                      kind:
                        const: A
            """.trimIndent()
        )
        val document = kson(
            """
                kind: A
                version: 2
            """.trimIndent()
        )

        assertSelected(narrowBranches(branches, document), chosen = listOf(1))
    }

    @Test
    fun aSharedPinLeavesTheBranchItDidNotChooseAmongTheOthers() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      mode:
                        const: fast

                  - properties:
                      kind:
                        const: A
            """.trimIndent()
        )
        val document = kson(
            """
                kind: A
                mode: fast
            """.trimIndent()
        )

        assertSelected(narrowBranches(branches, document), chosen = listOf(0))
    }

    @Test
    fun eachBranchAloneOnAPinIsChosen() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: circle
                  - properties:
                      mode:
                        const: fast
            """.trimIndent()
        )
        val document = kson(
            """
                kind: circle
                mode: fast
            """.trimIndent()
        )

        assertSelected(narrowBranches(branches, document), chosen = listOf(0, 1))
    }

    @Test
    fun aLoneOptionalPinIsNoDiscriminator() {
        // `kind` is required, but not by the branch pinning it
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: circle
                  - properties:
                      kind:
                        type: string
                        .
                      .
                    required:
                      - kind
            """.trimIndent()
        )
        val document = kson("kind: square")

        assertOpen(narrowBranches(branches, document), survivors = listOf(1))
        assertOpen(narrowOnDiscriminators(branches, document), survivors = listOf(0, 1))
        assertOpen(narrowOnDiscriminators(branches, kson("kind: circle")), survivors = listOf(0, 1))
    }

    @Test
    fun aLonePinItsBranchRequiresIsADiscriminator() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: circle
                        .
                      .
                    required:
                      - kind
                      =

                  - properties:
                      name:
                        type: string
            """.trimIndent()
        )

        assertSelected(narrowOnDiscriminators(branches, kson("kind: circle")), chosen = listOf(0))
        assertOpen(narrowOnDiscriminators(branches, kson("kind: square")), survivors = listOf(1))
    }

    @Test
    fun aPinTwoBranchesShareIsADiscriminator() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: circle
                  - properties:
                      kind:
                        const: square
                  - properties:
                      name:
                        type: string
            """.trimIndent()
        )

        assertSelected(narrowOnDiscriminators(branches, kson("kind: square")), chosen = listOf(1))
        assertOpen(narrowOnDiscriminators(branches, kson("kind: circel")), survivors = listOf(2))
    }

    @Test
    fun aPinTheDocumentLacksDecidesNothing() {
        assertOpen(narrowBranches(shapes, kson("radius: 3")), survivors = listOf(0, 1, 2))

        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: circle
                  - properties:
                      name:
                        type: string
            """.trimIndent()
        )
        assertOpen(narrowBranches(branches, kson("name: disc")), survivors = listOf(0, 1))
    }

    @Test
    fun branchesWithoutPinsDecideNothing() {
        val branches = branchesOf(
            """
                oneOf:
                  - true
                  - false
                  - type: string
                  - required:
                      - kind
            """.trimIndent()
        )

        assertOpen(narrowBranches(branches, kson("radius: 3")), survivors = listOf(0, 1, 2, 3))
    }

    @Test
    fun aValueNoBranchAdmitsListsEveryPinnedValue() {
        assertNoBranchFits(
            narrowBranches(shapes, kson("kind: circel")),
            "kind" to listOf("circle", "square", "triangle")
        )
    }

    @Test
    fun aValuePinnedByManyBranchesIsListedOnce() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        enum:
                          - A
                          - B
                          =
                  - properties:
                      kind:
                        enum:
                          - B
                          - C
            """.trimIndent()
        )

        assertNoBranchFits(narrowBranches(branches, kson("kind: Z")), "kind" to listOf("A", "B", "C"))
    }

    @Test
    fun onlyPropertiesEveryBranchRejectsAreListed() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      mode:
                        const: read

                  - properties:
                      kind:
                        const: B
                        .
                      mode:
                        const: write

                  - properties:
                      kind:
                        const: C
                        .
                      mode:
                        const: sync
            """.trimIndent()
        )

        assertNoBranchFits(
            narrowBranches(
                branches,
                kson(
                    """
                        kind: Z
                        mode: Y
                    """.trimIndent()
                )
            ),
            "kind" to listOf("A", "B", "C"),
            "mode" to listOf("read", "write", "sync")
        )
        assertNoBranchFits(
            narrowBranches(
                branches,
                kson(
                    """
                        kind: Z
                        mode: read
                    """.trimIndent()
                )
            ),
            "kind" to listOf("A", "B", "C")
        )
    }

    @Test
    fun rejectedPropertiesFollowTheFirstBranchNotTheDocument() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      mode:
                        const: read

                  - properties:
                      mode:
                        const: write
                        .
                      kind:
                        const: B
            """.trimIndent()
        )
        val modeWrittenFirst = kson(
            """
                mode: Y
                kind: Z
            """.trimIndent()
        )

        assertNoBranchFits(
            narrowBranches(branches, modeWrittenFirst),
            "kind" to listOf("A", "B"),
            "mode" to listOf("read", "write")
        )
    }

    @Test
    fun branchesRejectingDifferentPropertiesListNothing() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                  - properties:
                      mode:
                        const: read
            """.trimIndent()
        )
        val document = kson(
            """
                kind: Z
                mode: Y
            """.trimIndent()
        )

        assertNoBranchFits(narrowBranches(branches, document))
    }

    @Test
    fun aRejectedValueRulesBranchesOutWhileOneSurvives() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: circle
                  - properties:
                      kind:
                        const: square
                  - properties:
                      name:
                        type: string
            """.trimIndent()
        )

        assertOpen(narrowBranches(branches, kson("kind: circel")), survivors = listOf(2))
    }

    @Test
    fun aLoneBranchListsNothing() {
        val branches = branchesOf(
            """
                oneOf:
                  - properties:
                      kind:
                        const: circle
            """.trimIndent()
        )

        assertNoBranchFits(narrowBranches(branches, kson("kind: circel")))
    }

    @Test
    fun noBranchesDecideNothing() {
        assertOpen(narrowBranches(emptyList(), kson("kind: circle")), survivors = emptyList())
    }

    @Test
    fun aBranchThatIsARefCyclePinsNothing() {
        val cycles = listOf(
            $$"A: { '$ref': '#/$defs/B' }, B: { '$ref': '#/$defs/A' }",
            $$"A: { '$ref': '#/$defs/A' }",
            $$"A: { allOf: [{ '$ref': '#/$defs/A' }] }"
        )
        for (cycle in cycles) {
            val branches = branchesOf(
                $$"""
                    oneOf:
                      - '$ref': '#/$defs/A'
                      - properties:
                          kind:
                            const: square
                            .
                          .
                        .
                    '$defs': { $$cycle }
                """.trimIndent()
            )

            assertOpen(narrowBranches(branches, kson("kind: Z")), survivors = listOf(0))
        }
    }

    @Test
    fun aRefCycleKeepsThePinsItPasses() {
        val branches = branchesOf(
            $$"""
                oneOf:
                  - '$ref': '#/$defs/A'
                  - properties:
                      kind:
                        const: square
                        .
                      .
                    .
                '$defs':
                  A:
                    allOf:
                      - '$ref': '#/$defs/A'
                      - properties:
                          kind:
                            const: circle
            """.trimIndent()
        )

        assertSelected(narrowBranches(branches, kson("kind: circle")), chosen = listOf(0))
    }

    /** Each trip around this cycle applies the `$id` again, so it never returns under the same base URI. */
    @Test
    fun aRefCycleLengtheningItsBaseUriPinsNothing() {
        val branches = branchesOf(
            $$"""
                '$id': 'http://x/root/'
                oneOf:
                  - '$ref': 'sub/'
                  - properties:
                      kind:
                        const: B
                        .
                      .
                    .
                '$defs':
                  N:
                    '$id': 'sub/'
                    type: array
                    allOf:
                      - '$ref': 'http://x/root/sub/'
            """.trimIndent()
        )

        assertOpen(narrowBranches(branches, kson("kind: Z")), survivors = listOf(0))
    }

    @Test
    fun aValueThatIsNotAnObjectRulesOutNoBranch() {
        val everyBranch = listOf(0, 1, 2)

        assertOpen(narrowBranches(shapes, kson("3")), survivors = everyBranch)
        assertOpen(narrowBranches(shapes, kson("circel")), survivors = everyBranch)
        assertOpen(narrowBranches(shapes, kson("[1, 2]")), survivors = everyBranch)
        assertOpen(narrowBranches(shapes, kson("%kind\n  circel\n%%")), survivors = everyBranch)
    }

    @Test
    fun aValueThatIsNotAnObjectReadsNoPin() {
        // reading the first branch's pins would follow its `$ref` to the malformed pointer
        val branches = branchesOf(
            $$"""
                oneOf:
                  - '$ref': '#/$defs/Bad'
                  - true
                '$defs':
                  Bad:
                    '$ref': '#/$defs/a~2b'
            """.trimIndent()
        )

        assertOpen(narrowBranches(branches, kson("3")), survivors = listOf(0, 1))
    }

    @Test
    fun aBranchThatDidNotCompilePinsNothingAndStaysOpen() {
        val circle = shapes.first()

        assertOpen(narrowBranches(listOf(null, circle), kson("kind: square")), survivors = listOf(0))
        assertSelected(narrowBranches(listOf(null, circle), kson("kind: circle")), chosen = listOf(1))
        assertOpen(narrowBranches(listOf(null, null), kson("kind: circle")), survivors = listOf(0, 1))
    }
}

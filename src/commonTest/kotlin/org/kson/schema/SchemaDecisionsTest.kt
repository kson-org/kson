package org.kson.schema

import org.kson.KsonCore
import org.kson.parser.MessageSink
import org.kson.schema.SchemaDecisions.narrowByAllPins
import org.kson.schema.SchemaDecisions.narrowByDiscriminators
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SchemaDecisionsTest {
    private fun kson(source: String): KsonValue =
        assertNotNull(KsonCore.parseToAst(source).ksonValue, "should parse: $source")

    private fun compile(node: KsonValue, baseUri: String, idLookup: SchemaIdLookup): JsonSchema? =
        SchemaParser.parseSchemaElement(node, MessageSink(), baseUri, idLookup)

    private fun compiled(schema: String): JsonSchema {
        val node = kson(schema)
        return assertNotNull(compile(node, "", SchemaIdLookup(node)), "should compile: $schema")
    }

    private fun branchesOf(union: String): List<JsonSchema> {
        val root = kson(union) as KsonObject
        val idLookup = SchemaIdLookup(root)
        return (root.propertyLookup.getValue("oneOf") as KsonList).elements.map {
            assertNotNull(compile(it, idLookup.rootBaseUri, idLookup), "should compile: $it")
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
    private fun assertNone(pins: Pins, vararg rejected: Pair<String, List<String>>) {
        val none = assertIs<Pins.None>(pins)
        assertEquals(
            rejected.map { (property, values) -> property to values.map(::kson) },
            none.rejected.map { (property, values) -> property to values.toList() }
        )
    }

    private fun assertPinned(pins: Pins, branches: List<Int>) {
        val pinned = assertIs<Pins.Pinned>(pins)
        assertEquals(branches, pinned.branches, "branches")
    }

    private fun assertCandidates(pins: Pins, branches: List<Int>) {
        val candidates = assertIs<Pins.Candidates>(pins)
        assertEquals(branches, candidates.branches, "branches")
    }

    @Test
    fun aMatchingPinDropsTheOthersAndSelectsItsBranch() {
        assertPinned(narrowByAllPins(shapes, kson("kind: square")), branches = listOf(1))
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

        assertPinned(narrowByAllPins(branches, kson("kind: ellipse")), branches = listOf(0))
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

        assertNone(narrowByAllPins(branches, kson("kind: Z")), "kind" to listOf("square"))
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

        assertPinned(narrowByAllPins(branches, kson("kind: circle")), branches = listOf(0))
        assertPinned(narrowByAllPins(branches, kson("kind: square")), branches = listOf(1))
    }

    @Test
    fun onlyPinsNarrow() {
        val wrongRadius = kson(
            """
                kind: circle
                radius: big
            """.trimIndent()
        )

        assertPinned(narrowByAllPins(shapes, wrongRadius), branches = listOf(0))
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

        assertPinned(narrowByAllPins(branches, kson("kind: circle")), branches = listOf(0))
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

        assertCandidates(narrowByAllPins(branches, kson("kind: A")), branches = listOf(0, 1))
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

        assertPinned(narrowByAllPins(branches, document), branches = listOf(1))
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

        assertPinned(narrowByAllPins(branches, document), branches = listOf(0))
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

        assertPinned(narrowByAllPins(branches, document), branches = listOf(0, 1))
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

        assertCandidates(narrowByAllPins(branches, document), branches = listOf(1))
        assertCandidates(narrowByDiscriminators(branches, document), branches = listOf(0, 1))
        assertCandidates(narrowByDiscriminators(branches, kson("kind: circle")), branches = listOf(0, 1))
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

        assertPinned(narrowByDiscriminators(branches, kson("kind: circle")), branches = listOf(0))
        assertCandidates(narrowByDiscriminators(branches, kson("kind: square")), branches = listOf(1))
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

        assertPinned(narrowByDiscriminators(branches, kson("kind: square")), branches = listOf(1))
        assertCandidates(narrowByDiscriminators(branches, kson("kind: circel")), branches = listOf(2))
    }

    @Test
    fun aPinTheDocumentLacksDecidesNothing() {
        assertCandidates(narrowByAllPins(shapes, kson("radius: 3")), branches = listOf(0, 1, 2))

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
        assertCandidates(narrowByAllPins(branches, kson("name: disc")), branches = listOf(0, 1))
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

        assertCandidates(narrowByAllPins(branches, kson("radius: 3")), branches = listOf(0, 1, 2, 3))
    }

    @Test
    fun aValueNoBranchAdmitsListsEveryPinnedValue() {
        assertNone(
            narrowByAllPins(shapes, kson("kind: circel")),
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

        assertNone(narrowByAllPins(branches, kson("kind: Z")), "kind" to listOf("A", "B", "C"))
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

        assertNone(
            narrowByAllPins(
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
        assertNone(
            narrowByAllPins(
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

        assertNone(
            narrowByAllPins(branches, modeWrittenFirst),
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

        assertNone(narrowByAllPins(branches, document))
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

        assertCandidates(narrowByAllPins(branches, kson("kind: circel")), branches = listOf(2))
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

        assertNone(narrowByAllPins(branches, kson("kind: circel")))
    }

    @Test
    fun noBranchesDecideNothing() {
        assertCandidates(narrowByAllPins(emptyList(), kson("kind: circle")), branches = emptyList())
    }

    /** A union whose first branch has `kind` as a lone `$ref` to the definition `A` in [definitions]. */
    private fun unionWithKindRef(definitions: String) = branchesOf(
        $$"""
            oneOf:
              - properties:
                  kind:
                    '$ref': '#/$defs/A'
              - properties:
                  kind:
                    const: square
                    .
                  .
                .
            '$defs': { $$definitions }
        """.trimIndent()
    )

    @Test
    fun aPropertyThatIsARefToAConstOrEnumPins() {
        val toConst = unionWithKindRef("A: { const: circle }")
        assertPinned(narrowByAllPins(toConst, kson("kind: circle")), branches = listOf(0))

        val toEnum = unionWithKindRef("A: { enum: [circle, ellipse] }")
        assertNone(narrowByAllPins(toEnum, kson("kind: Z")), "kind" to listOf("circle", "ellipse", "square"))
    }

    @Test
    fun aPinIsReadThroughARefChain() {
        val branches = unionWithKindRef($$"A: { '$ref': '#/$defs/B' }, B: { const: circle }")

        assertPinned(narrowByAllPins(branches, kson("kind: circle")), branches = listOf(0))
    }

    @Test
    fun aRefTargetPinsByTheSameRuleAsAnInlineSchema() {
        val typeIsNoValidator = unionWithKindRef("A: { type: string, const: circle }")
        assertPinned(narrowByAllPins(typeIsNoValidator, kson("kind: circle")), branches = listOf(0))

        val twoValidators = unionWithKindRef("A: { enum: [circle], minLength: 1 }")
        assertCandidates(narrowByAllPins(twoValidators, kson("kind: circle")), branches = listOf(0))
    }

    @Test
    fun aPropertyThatIsARefCyclePinsNothing() {
        val cycles = listOf(
            $$"A: { '$ref': '#/$defs/B' }, B: { '$ref': '#/$defs/A' }",
            $$"A: { '$ref': '#/$defs/A' }"
        )
        for (cycle in cycles) {
            assertCandidates(narrowByAllPins(unionWithKindRef(cycle), kson("kind: Z")), branches = listOf(0))
        }
    }

    @Test
    fun aPropertyThatRefsItsOwnSchemaPinsNothing() {
        val branches = branchesOf(
            $$"""
                oneOf:
                  - '$ref': '#/$defs/Node'
                  - properties:
                      child:
                        const: leaf
                        .
                      .
                    .
                '$defs':
                  Node:
                    properties:
                      child:
                        '$ref': '#/$defs/Node'
            """.trimIndent()
        )

        assertCandidates(narrowByAllPins(branches, kson("child: Z")), branches = listOf(0))
    }

    @Test
    fun onlyPropertiesTheDocumentHasAreReadForPins() {
        val branch = compiled(
            """
                properties:
                  kind:
                    const: A
                    .
                  mode:
                    const: fast
            """.trimIndent()
        )

        assertEquals(setOf("kind"), assertIs<JsonObjectSchema>(branch).pinnedProperties(setOf("kind", "other")).keys)
    }

    @Test
    fun aMalformedPropertyRefDoesNotThrowAndPinsStillDecide() {
        val branches = branchesOf(
            $$"""
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      extra:
                        '$ref': '#/$defs/Bad'

                  - properties:
                      kind:
                        const: B
                        .
                      .
                    .
                '$defs':
                  Bad:
                    '$ref': '#/$defs/a~2b'
            """.trimIndent()
        )

        val document = kson(
            """
                kind: Z
                extra: 1
            """.trimIndent()
        )

        assertNone(narrowByAllPins(branches, document), "kind" to listOf("A", "B"))
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

            assertCandidates(narrowByAllPins(branches, kson("kind: Z")), branches = listOf(0))
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

        assertPinned(narrowByAllPins(branches, kson("kind: circle")), branches = listOf(0))
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

        assertCandidates(narrowByAllPins(branches, kson("kind: Z")), branches = listOf(0))
    }

    @Test
    fun aValueThatIsNotAnObjectRulesOutNoBranch() {
        val everyBranch = listOf(0, 1, 2)

        assertCandidates(narrowByAllPins(shapes, kson("3")), branches = everyBranch)
        assertCandidates(narrowByAllPins(shapes, kson("circel")), branches = everyBranch)
        assertCandidates(narrowByAllPins(shapes, kson("[1, 2]")), branches = everyBranch)
        assertCandidates(narrowByAllPins(shapes, kson("%kind\n  circel\n%%")), branches = everyBranch)
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

        assertCandidates(narrowByAllPins(branches, kson("3")), branches = listOf(0, 1))
    }

    @Test
    fun aBranchThatDidNotCompilePinsNothingAndStaysACandidate() {
        val circle = shapes.first()

        assertCandidates(narrowByAllPins(listOf(null, circle), kson("kind: square")), branches = listOf(0))
        assertPinned(narrowByAllPins(listOf(null, circle), kson("kind: circle")), branches = listOf(1))
        assertCandidates(narrowByAllPins(listOf(null, null), kson("kind: circle")), branches = listOf(0, 1))
    }
}

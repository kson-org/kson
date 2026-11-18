package org.kson.schema

import org.kson.KsonCore
import org.kson.schema.SchemaDecisions.accepts
import org.kson.schema.SchemaDecisions.breaks
import org.kson.schema.SchemaDecisions.narrowByAllPins
import org.kson.schema.SchemaDecisions.narrowByDiscriminators
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.InvalidJsonPointerException
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

        assertFailsWith<InvalidJsonPointerException> {
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
        assertSelected(narrowByAllPins(shapes, kson("kind: square")), chosen = listOf(1))
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

        assertSelected(narrowByAllPins(branches, kson("kind: ellipse")), chosen = listOf(0))
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

        assertNoBranchFits(narrowByAllPins(branches, kson("kind: Z")), "kind" to listOf("square"))
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

        assertSelected(narrowByAllPins(branches, kson("kind: circle")), chosen = listOf(0))
        assertSelected(narrowByAllPins(branches, kson("kind: square")), chosen = listOf(1))
    }

    @Test
    fun onlyPinsNarrow() {
        val wrongRadius = kson(
            """
                kind: circle
                radius: big
            """.trimIndent()
        )

        assertSelected(narrowByAllPins(shapes, wrongRadius), chosen = listOf(0))
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

        assertSelected(narrowByAllPins(branches, kson("kind: circle")), chosen = listOf(0))
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

        assertOpen(narrowByAllPins(branches, kson("kind: A")), survivors = listOf(0, 1))
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

        assertSelected(narrowByAllPins(branches, document), chosen = listOf(1))
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

        assertSelected(narrowByAllPins(branches, document), chosen = listOf(0))
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

        assertSelected(narrowByAllPins(branches, document), chosen = listOf(0, 1))
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

        assertOpen(narrowByAllPins(branches, document), survivors = listOf(1))
        assertOpen(narrowByDiscriminators(branches, document), survivors = listOf(0, 1))
        assertOpen(narrowByDiscriminators(branches, kson("kind: circle")), survivors = listOf(0, 1))
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

        assertSelected(narrowByDiscriminators(branches, kson("kind: circle")), chosen = listOf(0))
        assertOpen(narrowByDiscriminators(branches, kson("kind: square")), survivors = listOf(1))
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

        assertSelected(narrowByDiscriminators(branches, kson("kind: square")), chosen = listOf(1))
        assertOpen(narrowByDiscriminators(branches, kson("kind: circel")), survivors = listOf(2))
    }

    @Test
    fun aPinTheDocumentLacksDecidesNothing() {
        assertOpen(narrowByAllPins(shapes, kson("radius: 3")), survivors = listOf(0, 1, 2))

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
        assertOpen(narrowByAllPins(branches, kson("name: disc")), survivors = listOf(0, 1))
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

        assertOpen(narrowByAllPins(branches, kson("radius: 3")), survivors = listOf(0, 1, 2, 3))
    }

    @Test
    fun aValueNoBranchAdmitsListsEveryPinnedValue() {
        assertNoBranchFits(
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

        assertNoBranchFits(narrowByAllPins(branches, kson("kind: Z")), "kind" to listOf("A", "B", "C"))
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
        assertNoBranchFits(
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

        assertNoBranchFits(
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

        assertNoBranchFits(narrowByAllPins(branches, document))
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

        assertOpen(narrowByAllPins(branches, kson("kind: circel")), survivors = listOf(2))
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

        assertNoBranchFits(narrowByAllPins(branches, kson("kind: circel")))
    }

    @Test
    fun noBranchesDecideNothing() {
        assertOpen(narrowByAllPins(emptyList(), kson("kind: circle")), survivors = emptyList())
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
        assertSelected(narrowByAllPins(toConst, kson("kind: circle")), chosen = listOf(0))

        val toEnum = unionWithKindRef("A: { enum: [circle, ellipse] }")
        assertNoBranchFits(narrowByAllPins(toEnum, kson("kind: Z")), "kind" to listOf("circle", "ellipse", "square"))
    }

    @Test
    fun aPinIsReadThroughARefChain() {
        val branches = unionWithKindRef($$"A: { '$ref': '#/$defs/B' }, B: { const: circle }")

        assertSelected(narrowByAllPins(branches, kson("kind: circle")), chosen = listOf(0))
    }

    @Test
    fun aRefTargetPinsByTheSameRuleAsAnInlineSchema() {
        val typeIsNoValidator = unionWithKindRef("A: { type: string, const: circle }")
        assertSelected(narrowByAllPins(typeIsNoValidator, kson("kind: circle")), chosen = listOf(0))

        val twoValidators = unionWithKindRef("A: { enum: [circle], minLength: 1 }")
        assertOpen(narrowByAllPins(twoValidators, kson("kind: circle")), survivors = listOf(0))
    }

    @Test
    fun aPropertyThatIsARefCyclePinsNothing() {
        val cycles = listOf(
            $$"A: { '$ref': '#/$defs/B' }, B: { '$ref': '#/$defs/A' }",
            $$"A: { '$ref': '#/$defs/A' }"
        )
        for (cycle in cycles) {
            assertOpen(narrowByAllPins(unionWithKindRef(cycle), kson("kind: Z")), survivors = listOf(0))
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

        assertOpen(narrowByAllPins(branches, kson("child: Z")), survivors = listOf(0))
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

        assertNoBranchFits(narrowByAllPins(branches, document), "kind" to listOf("A", "B"))
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

            assertOpen(narrowByAllPins(branches, kson("kind: Z")), survivors = listOf(0))
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

        assertSelected(narrowByAllPins(branches, kson("kind: circle")), chosen = listOf(0))
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

        assertOpen(narrowByAllPins(branches, kson("kind: Z")), survivors = listOf(0))
    }

    @Test
    fun aValueThatIsNotAnObjectRulesOutNoBranch() {
        val everyBranch = listOf(0, 1, 2)

        assertOpen(narrowByAllPins(shapes, kson("3")), survivors = everyBranch)
        assertOpen(narrowByAllPins(shapes, kson("circel")), survivors = everyBranch)
        assertOpen(narrowByAllPins(shapes, kson("[1, 2]")), survivors = everyBranch)
        assertOpen(narrowByAllPins(shapes, kson("%kind\n  circel\n%%")), survivors = everyBranch)
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

        assertOpen(narrowByAllPins(branches, kson("3")), survivors = listOf(0, 1))
    }

    @Test
    fun aBranchThatDidNotCompilePinsNothingAndStaysOpen() {
        val circle = shapes.first()

        assertOpen(narrowByAllPins(listOf(null, circle), kson("kind: square")), survivors = listOf(0))
        assertSelected(narrowByAllPins(listOf(null, circle), kson("kind: circle")), chosen = listOf(1))
        assertOpen(narrowByAllPins(listOf(null, null), kson("kind: circle")), survivors = listOf(0, 1))
    }
}

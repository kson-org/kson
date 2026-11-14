package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Narrowing on discriminators, whether inline, through a lone `$ref`, or mixed.
 * They needn't be disjoint.
 */
class OneOfDiscriminatedUnionTest : JsonSchemaTest {
    /**
     * `kind` repeats (A, A, B), `kind_job` is distinct, and the wildcard pins neither.
     */
    private val duplicateConstUnionWithWildcard = """
        oneOf:
          - properties:
              kind:
                const: A
                .
              kind_job:
                const: J1
                .
              params:
                type: object
                required:
                  - p1
                .
              .
            required:
              - kind
              - kind_job
              - params
              =

          - properties:
              kind:
                const: A
                .
              kind_job:
                const: J2
                .
              params:
                type: object
                required:
                  - p2
                .
              .
            required:
              - kind
              - kind_job
              - params
              =

          - properties:
              kind:
                const: B
                .
              kind_job:
                const: J3
                .
              params:
                type: object
                required:
                  - p3
                .
              .
            required:
              - kind
              - kind_job
              - params
              =

          - properties:
              kind:
                type: string
                .
              kind_job:
                not:
                  enum:
                    - J1
                    - J2
                    - J3
                  .
                .
              params:
                type: object
                required:
                  - p4
                .
              .
            required:
              - kind
              - kind_job
              - params
    """.trimIndent()

    /**
     * Branches written as `$ref`s into `$defs`, the dominant real-world shape.
     */
    private val refDiscriminatedUnion = $$"""
        oneOf:
          - '$ref': '#/$defs/A'
          - '$ref': '#/$defs/B'
            .
        '$defs':
          A:
            properties:
              kind:
                const: A
                .
              params:
                type: object
                required:
                  - alpha
                .
              .
            required:
              - kind
            .
          B:
            properties:
              kind:
                const: B
                .
              params:
                type: object
                required:
                  - beta
                .
              .
            required:
              - kind
    """.trimIndent()

    private fun lonePinUnion(pinnedBranchRequired: String) = """
        oneOf:
          - title: Pinned
            properties:
              mode:
                const: fast
                .
              .
            required: $pinnedBranchRequired

          - title: Other
            properties:
              mode:
                type: string
                .
              .
            required:
              - needs_other
    """.trimIndent()

    private val loneRequiredPinUnion = lonePinUnion("[mode, needs_pinned]")

    private val loneOptionalPinUnion = lonePinUnion("[needs_pinned]")

    /**
     * `kind: "A"` selects branch A, so its deeper `params` failure leads, ahead of branch B's errors.
     */
    @Test
    fun testOneOfDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      params:
                        type: object
                        required:
                          - alpha
                        .
                      .
                    required:
                      - kind
                      =

                  - properties:
                      kind:
                        const: B
                        .
                      params:
                        type: object
                        required:
                          - beta
                        .
                      .
                    required:
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            ),
            listOf(
                // inside `params`, the value the user must actually fix
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * The real-world shape is `{ type: string, const: X }`; the sibling `type` parses into a separate
     * validator, so the property still pins.
     */
    @Test
    fun testOneOfDiscriminatorWithSiblingTypeSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        type: string
                        const: A
                        .
                      params:
                        type: object
                        required:
                          - alpha
                        .
                      .
                    required:
                      - kind
                      =

                  - properties:
                      kind:
                        type: string
                        const: B
                        .
                      params:
                        type: object
                        required:
                          - beta
                        .
                      .
                    required:
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * Narrowing applies through the `allOf[ base, oneOf[ branches ] ]` wrapper.
     */
    @Test
    fun testOneOfDiscriminatorThroughAllOfWrapper() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                kind_job: "A_JOB"
                params: {}
            """.trimIndent(),
            """
                allOf:
                  - type: object
                  - oneOf:
                      - properties:
                          kind:
                            const: A
                            .
                          kind_job:
                            const: A_JOB
                            .
                          params:
                            type: object
                            required:
                              - alpha
                            .
                          .
                        required:
                          - kind
                          - kind_job
                          - params
                          =

                      - properties:
                          kind:
                            const: B
                            .
                          kind_job:
                            const: B_JOB
                            .
                          params:
                            type: object
                            required:
                              - beta
                            .
                          .
                        required:
                          - kind
                          - kind_job
                          - params
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            ),
            listOf(
                // inside `params`
                Location(Coordinates(2, 8), Coordinates(2, 10), 36, 38),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * `kind_job: J1` is admitted by the J1 branch alone, which selects it over the `kind`-matching J2
     * branch and the wildcard.
     */
    @Test
    fun testOneOfDiscriminatorToleratesDuplicateConstsAndWildcard() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                kind_job: "J1"
                params: {}
            """.trimIndent(),
            duplicateConstUnionWithWildcard,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertContains(errors[0].message.toString(), "p1")
    }

    /**
     * No enum error: the wildcard pins no `kind_job`, so it might accept `J9`.  The three contradicted
     * branches can't lead, so the wildcard does.
     */
    @Test
    fun testOneOfDiscriminatorWildcardNoMatchNarrowsToWildcardBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                kind_job: "J9"
                params: {}
            """.trimIndent(),
            duplicateConstUnionWithWildcard,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertContains(errors[0].message.toString(), "p4")
    }

    /**
     * Selection never resurrects a contradicted branch: `kind_job: "J1"` is admitted by the J1 branch
     * alone and `kind: "B"` by the J3 branch alone, but each contradicts the other's pin, so the
     * surviving wildcard leads instead.
     */
    @Test
    fun testOneOfSelectionNeverResurrectsContradictedBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
                kind_job: "J1"
                params: {}
            """.trimIndent(),
            duplicateConstUnionWithWildcard,
            listOf(
                SCHEMA_NOT_VALIDATION_FAILED,
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertEquals("Missing required properties: p4", errors[1].message.toString())
    }

    /**
     * No pin outranks another: `kind` selects branch A and `version` branch W.  The unpinned branch
     * survives elimination and matches by presence, but neither pin selects it.
     */
    @Test
    fun testOneOfEverySelectingPinCounts() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                version: 2
            """.trimIndent(),
            """
                oneOf:
                  - title: BranchA
                    properties:
                      kind:
                        const: A
                        .
                      needs_a:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - needs_a
                      =

                  - title: BranchB
                    properties:
                      kind:
                        const: B
                        .
                      needs_b:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - needs_b
                      =

                  - title: BranchW
                    properties:
                      version:
                        const: 2
                        .
                      needs_w:
                        type: string
                        .
                      .
                    required:
                      - version
                      - needs_w
                      =

                  - title: UnpinnedBranch
                    properties:
                      kind:
                        type: string
                        .
                      version:
                        type: number
                        .
                      .
                    required:
                      - needs_unpinned
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- BranchA:\n    - Missing required properties: needs_a (1:1)\n" +
                "- BranchW:\n    - Missing required properties: needs_w (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * `kind: "A"` matches two branches, so nothing picks one; it still eliminates the `B` branch.
     */
    @Test
    fun testOneOfDuplicateConstPropertyDoesNotSelect() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      params:
                        type: object
                        required:
                          - p1
                        .
                      .
                    required:
                      - kind
                      - params
                      =

                  - properties:
                      kind:
                        const: A
                        .
                      params:
                        type: object
                        required:
                          - p2
                        .
                      .
                    required:
                      - kind
                      - params
                      =

                  - properties:
                      kind:
                        const: B
                        .
                      params:
                        type: object
                        required:
                          - p3
                        .
                      .
                    required:
                      - kind
                      - params
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        val report = errors[1].message.toString()
        assertContains(report, "p1")
        assertContains(report, "p2")
        assertFalse(report.contains("p3"))
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- JSON Object Schema:\n" +
                "    - Value must be exactly equal to \"B\" (1:8)\n" +
                "    - Missing required properties: p3 (2:9)",
            errors[2].message.toString()
        )
    }

    /**
     * A one-value `enum` pins like `const`.
     */
    @Test
    fun testOneOfSingleValueEnumDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        enum:
                          - A
                        .
                      params:
                        type: object
                        required:
                          - alpha
                        .
                      .
                    required:
                      - kind
                      =

                  - properties:
                      kind:
                        enum:
                          - B
                        .
                      params:
                        type: object
                        required:
                          - beta
                        .
                      .
                    required:
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * `kind: "B"` is the second value of branch A's set: the whole set admits, not just its first value.
     */
    @Test
    fun testOneOfMultiValueEnumDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "B"
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        enum:
                          - A
                          - B
                        .
                      params:
                        type: object
                        required:
                          - alpha
                        .
                      .
                    required:
                      - kind
                      =

                  - properties:
                      kind:
                        enum:
                          - C
                        .
                      params:
                        type: object
                        required:
                          - gamma
                        .
                      .
                    required:
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * Though the sets overlap, only branch A admits `kind: "A"`; branch B is eliminated and A is selected.
     */
    @Test
    fun testOneOfOverlappingEnumSetsEliminateContradictedBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        enum:
                          - A
                          - B
                        .
                      params:
                        type: object
                        required:
                          - alpha
                        .
                      .
                    required:
                      - kind
                      =

                  - properties:
                      kind:
                        enum:
                          - B
                          - C
                        .
                      params:
                        type: object
                        required:
                          - beta
                        .
                      .
                    required:
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
        assertFalse(errors[0].message.toString().contains("beta"))
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- JSON Object Schema:\n" +
                "    - Value must be one of: \"B\", \"C\" (1:8)\n" +
                "    - Missing required properties: beta (2:9)",
            errors[1].message.toString()
        )
    }

    /**
     * `enum: []` rejects every value but adds nothing to the allowed list, so just `"A"` is listed.
     */
    @Test
    fun testOneOfEmptyEnumPinAddsNoAllowedValue() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        enum:
                          - A
                        .
                      params:
                        type: object
                        required:
                          - alpha
                        .
                      .
                    required:
                      - kind
                      =

                  - properties:
                      kind:
                        enum: []
                        .
                      params:
                        type: object
                        required:
                          - beta
                        .
                      .
                    required:
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED,
                SCHEMA_SUB_SCHEMA_ERRORS
            ),
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertEquals("Value must be one of: \"A\"", errors[0].message.toString())
    }

    /**
     * Pins are read through `$ref` branches.
     */
    @Test
    fun testOneOfRefBranchDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            refDiscriminatedUnion,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    @Test
    fun testOneOfRefBranchDiscriminatorMatchesNoBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            refDiscriminatedUnion,
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED,
                SCHEMA_SUB_SCHEMA_ERRORS
            ),
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertContains(errors[0].message.toString(), "Value must be one of: \"A\", \"B\"")
    }

    /**
     * Both the inline pin and the ref target's are read.
     */
    @Test
    fun testOneOfMixedInlineAndRefPinsSelect() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "B"
                params: {}
            """.trimIndent(),
            $$"""
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      params:
                        type: object
                        required:
                          - alpha
                        .
                      .
                    required:
                      - kind
                      =

                  - '$ref': '#/$defs/B'
                    .
                '$defs':
                  B:
                    properties:
                      kind:
                        const: B
                        .
                      params:
                        type: object
                        required:
                          - beta
                        .
                      .
                    required:
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertContains(errors[0].message.toString(), "beta")
    }

    @Test
    fun testOneOfSharedOptionalPinSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrors(
            """mode: "fast"""",
            """
                oneOf:
                  - title: Fast
                    properties:
                      mode:
                        const: fast
                        .
                      .
                    required:
                      - needs_fast
                      =

                  - title: Slow
                    properties:
                      mode:
                        const: slow
                        .
                      .
                    required:
                      - needs_slow
                      =

                  - title: Other
                    properties:
                      mode:
                        type: string
                        .
                      .
                    required:
                      - needs_other
            """.trimIndent(),
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Missing required properties: needs_fast", errors[0].message.toString())
    }

    @Test
    fun testOneOfLoneRequiredPinEliminatesContradictedBranch() {
        val errors = assertKsonSchemaErrors(
            """mode: "slow"""",
            loneRequiredPinUnion,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Missing required properties: needs_other", errors[0].message.toString())
    }

    @Test
    fun testOneOfLoneRequiredPinSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrors(
            """mode: "fast"""",
            loneRequiredPinUnion,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Missing required properties: needs_pinned", errors[0].message.toString())
    }

    @Test
    fun testOneOfLoneOptionalPinDoesNotEliminate() {
        val errors = assertKsonSchemaErrors(
            """mode: "slow"""",
            loneOptionalPinUnion,
            listOf(SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- Pinned:\n" +
                "    - Value must be exactly equal to \"fast\" (1:8)\n" +
                "    - Missing required properties: needs_pinned (1:1)\n" +
                "- Other:\n" +
                "    - Missing required properties: needs_other (1:1)",
            errors[1].message.toString()
        )
    }

    @Test
    fun testOneOfLoneOptionalPinDoesNotSelect() {
        val errors = assertKsonSchemaErrors(
            """mode: "fast"""",
            loneOptionalPinUnion,
            listOf(SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- Pinned:\n" +
                "    - Missing required properties: needs_pinned (1:1)\n" +
                "- Other:\n" +
                "    - Missing required properties: needs_other (1:1)",
            errors[1].message.toString()
        )
    }

    @Test
    fun testOneOfLonePinRequiredOnlyByAnotherBranchDoesNotEliminate() {
        val errors = assertKsonSchemaErrors(
            """mode: "slow"""",
            """
                oneOf:
                  - title: Pinned
                    properties:
                      mode:
                        const: fast
                        .
                      .
                    required:
                      - needs_pinned
                      =

                  - title: Other
                    properties:
                      mode:
                        type: string
                        .
                      .
                    required:
                      - mode
                      - needs_other
            """.trimIndent(),
            listOf(SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- Pinned:\n" +
                "    - Value must be exactly equal to \"fast\" (1:8)\n" +
                "    - Missing required properties: needs_pinned (1:1)\n" +
                "- Other:\n" +
                "    - Missing required properties: needs_other (1:1)",
            errors[1].message.toString()
        )
    }

    @Test
    fun testOneOfPartialModeNarrowsOnDiscriminatorsLikeFull() {
        val closedUnion = """
            oneOf:
              - properties:
                  kind:
                    const: A
                    .
                  params:
                    type: object
                    required:
                      - alpha
                    .
                  .
                required:
                  - kind
                  =

              - properties:
                  kind:
                    const: B
                    .
                  params:
                    type: object
                    required:
                      - beta
                    .
                  .
                required:
                  - kind
        """.trimIndent()
        val document = """
            kind: "Z"
            params: {}
        """.trimIndent()

        assertKsonSchemaErrors(
            document,
            closedUnion,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertKsonSchemaErrors(
            document,
            closedUnion,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }

    @Test
    fun testOneOfLoneOptionalPinEliminatesOnlyInPartialMode() {
        val union = """
            oneOf:
              - title: Pinned
                properties:
                  mode:
                    const: fast

              - title: Other
                properties:
                  mode:
                    type: number
        """.trimIndent()
        val document = """mode: "f""""

        val fullErrors = assertKsonSchemaErrors(
            document,
            union,
            listOf(SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )
        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- Pinned:\n" +
                "    - Value must be exactly equal to \"fast\" (1:8)\n" +
                "- Other:\n" +
                "    - Property 'mode': Expected one of: number, but got: string (1:8)",
            fullErrors[1].message.toString()
        )

        val partialErrors = assertKsonSchemaErrors(
            document,
            union,
            listOf(SCHEMA_VALUE_TYPE_MISMATCH),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
        assertEquals("Property 'mode': Expected one of: number, but got: string", partialErrors[0].message.toString())
        assertEquals(Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8), partialErrors[0].location)
    }

    @Test
    fun testOneOfLoneOptionalPinSelectsOnlyInPartialMode() {
        val union = """
            oneOf:
              - title: Pinned
                properties:
                  mode:
                    const: fast
                    .
                  size:
                    type: number

              - title: Other
                properties:
                  mode:
                    type: string
                    .
                  size:
                    type: boolean
        """.trimIndent()
        val document = """
            mode: "fast"
            size: "big"
        """.trimIndent()

        val fullErrors = assertKsonSchemaErrors(
            document,
            union,
            listOf(SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )
        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- Pinned:\n" +
                "    - Property 'size': Expected one of: number, but got: string (2:8)\n" +
                "- Other:\n" +
                "    - Property 'size': Expected one of: boolean, but got: string (2:8)",
            fullErrors[1].message.toString()
        )

        val partialErrors = assertKsonSchemaErrors(
            document,
            union,
            listOf(SCHEMA_VALUE_TYPE_MISMATCH),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
        assertEquals("Property 'size': Expected one of: number, but got: string", partialErrors[0].message.toString())
    }
}

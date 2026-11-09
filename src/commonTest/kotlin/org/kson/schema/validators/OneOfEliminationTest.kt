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
 * Elimination: a branch is dropped when the document's value falls outside its narrowing pin.  Pin
 * selection and presence only narrow among the survivors.
 */
class OneOfEliminationTest : JsonSchemaTest {
    /**
     * `kind` A repeats, so a value can match several branches' pins.
     */
    private val duplicateConstUnion = """
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
    """.trimIndent()

    private val unionWithEliminatedDuplicatePin = """
        oneOf:
          - properties:
              kind:
                const: A
                .
              version:
                const: 1
                .
              .
            required:
              - kind
              - version
              =

          - properties:
              kind:
                const: A
                .
              .
            required:
              - kind
              - alpha
              =

          - properties:
              kind:
                type: string
                .
              beta:
                type: string
                .
              .
            required:
              - kind
              - gamma
    """.trimIndent()

    @Test
    fun testOneOfLoneRequiredConstMismatchEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      extra_a:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - extra_a
                      =

                  - properties:
                      value:
                        type: boolean
                        .
                      .
                    required:
                      - value
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertEquals("Missing required properties: value", errors[0].message.toString())
    }

    @Test
    fun testOneOfNonDisjointPinsEliminateContradictedBranches() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
                params: {}
            """.trimIndent(),
            duplicateConstUnion,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "p3")
        assertFalse(errors[0].message.toString().contains("p1"))
        assertFalse(errors[0].message.toString().contains("p2"))
    }

    /**
     * `version: 2` eliminates the first branch, making `kind: "A"` unique among the surviving pins.
     */
    @Test
    fun testOneOfEliminatedDuplicatePinDoesNotPreventSelection() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                version: 2
            """.trimIndent(),
            unionWithEliminatedDuplicatePin,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING)
        )

        assertEquals("Missing required properties: alpha", errors[0].message.toString())
    }

    /**
     * The surviving unique pin selects before presence of `beta` can favor the unpinned branch.
     */
    @Test
    fun testOneOfSelectionAfterEliminationTakesPrecedenceOverPresence() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                version: 2
                beta: "present"
            """.trimIndent(),
            unionWithEliminatedDuplicatePin,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING)
        )

        assertEquals("Missing required properties: alpha", errors[0].message.toString())
    }

    /**
     * Each pinned value is listed once, though `A` is pinned twice.
     */
    @Test
    fun testOneOfValueNoBranchAdmitsReportsAllowedValues() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            duplicateConstUnion,
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            // the enum error hangs off the `kind` value `Z`
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8)
            )
        )

        assertEquals("Value must be one of: \"A\", \"B\"", errors[0].message.toString())
    }

    @Test
    fun testOneOfEachValueNoBranchAdmitsIsReported() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                mode: "sync"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      mode:
                        const: read
                        .
                      .
                    required:
                      - kind
                      - mode
                      =

                  - properties:
                      kind:
                        const: B
                        .
                      mode:
                        const: write
                        .
                      .
                    required:
                      - kind
                      - mode
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED,
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8),
                Location(Coordinates(1, 7), Coordinates(1, 11), 17, 21)
            )
        )

        assertEquals("Value must be one of: \"A\", \"B\"", errors[0].message.toString())
        assertEquals("Value must be one of: \"read\", \"write\"", errors[1].message.toString())
    }

    /**
     * `version: 2` rules out every branch, so only it is reported, not branch A's missing `alpha`.
     */
    @Test
    fun testOneOfSharedPinRejectionIsReportedAlone() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                version: 2
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      version:
                        const: 1
                        .
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
                      - version
                      - kind
                      =

                  - properties:
                      version:
                        const: 1
                        .
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
                      - version
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            // at the `version` value `2`
            listOf(
                Location(Coordinates(0, 9), Coordinates(0, 10), 9, 10)
            )
        )

        assertEquals("Value must be one of: 1", errors[0].message.toString())
    }

    /**
     * A lone branch keeps its own errors, which say more than the enum error would.
     */
    @Test
    fun testOneOfSingleBranchKeepsItsOwnErrors() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      name:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - name
            """.trimIndent(),
            listOf(
                SCHEMA_VALUE_NOT_EQUAL_TO_CONST,
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[1].message.toString(), "name")
    }

    /**
     * In [ValidationMode.PARTIAL] even this optional pin eliminates, yet a lone branch keeps its own errors.
     */
    @Test
    fun testOneOfSingleBranchKeepsItsOwnErrorsInPartialMode() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
            """.trimIndent(),
            listOf(
                SCHEMA_VALUE_NOT_EQUAL_TO_CONST
            ),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )

        assertEquals("Value must be exactly equal to \"A\"", errors[0].message.toString())
    }

    /**
     * No survivor, yet no value every branch rejects: nothing narrows.
     */
    @Test
    fun testOneOfBranchesEliminatedOnDifferentPropertiesReportEveryBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
                mode: "N"
            """.trimIndent(),
            """
                oneOf:
                  - title: BranchA
                    properties:
                      kind:
                        const: A
                        .
                      need_a:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - need_a
                      =

                  - title: BranchB
                    properties:
                      mode:
                        const: M
                        .
                      need_b:
                        type: string
                        .
                      .
                    required:
                      - mode
                      - need_b
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- BranchA:\n" +
                "    - Value must be exactly equal to \"A\" (1:8)\n" +
                "    - Missing required properties: need_a (1:1)\n" +
                "- BranchB:\n" +
                "    - Value must be exactly equal to \"M\" (2:8)\n" +
                "    - Missing required properties: need_b (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * Every branch of `oneOf: []` vacuously rejects every value; that must not become enum errors
     * listing nothing.
     */
    @Test
    fun testOneOfWithNoBranchesBlamesNoValue() {
        assertKsonSchemaErrors(
            """
                kind: "A"
            """.trimIndent(),
            """
                oneOf: []
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }

    @Test
    fun testOneOfLoneRequiredMultiValueEnumPinEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "C"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        enum:
                          - A
                          - B
                        .
                      foo:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - foo
                      =

                  - properties:
                      baz:
                        type: boolean
                        .
                      .
                    required:
                      - baz
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertEquals("Missing required properties: baz", errors[0].message.toString())
    }

    /**
     * Survivors {B, C} are refined by presence of `kind`, which B knows and C doesn't.
     */
    @Test
    fun testOneOfEliminationRefinesPresenceMatch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
            """.trimIndent(),
            """
                oneOf:
                  - title: BranchA
                    properties:
                      kind:
                        const: A
                        .
                      need_a:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - need_a
                      =

                  - title: BranchB
                    properties:
                      kind:
                        type: string
                        .
                      need_b:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - need_b
                      =

                  - title: BranchC
                    properties:
                      other:
                        type: string
                        .
                      need_c:
                        type: string
                        .
                      .
                    required:
                      - other
                      - need_c
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertEquals("Missing required properties: need_b", errors[0].message.toString())
    }

    /**
     * Presence alone would pick branch A, which knows `kind`, but `kind: "X"` eliminates it.
     */
    @Test
    fun testOneOfPresenceCannotResurrectEliminatedBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
            """.trimIndent(),
            """
                oneOf:
                  - title: BranchA
                    properties:
                      kind:
                        const: A
                        .
                      need_a:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - need_a
                      =

                  - title: BranchB
                    properties:
                      sel_b:
                        type: string
                        .
                      need_b:
                        type: string
                        .
                      .
                    required:
                      - sel_b
                      - need_b
                      =

                  - title: BranchC
                    properties:
                      sel_c:
                        type: string
                        .
                      need_c:
                        type: string
                        .
                      .
                    required:
                      - sel_c
                      - need_c
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- BranchB:\n    - Missing required properties: sel_b, need_b (1:1)\n" +
                "- BranchC:\n    - Missing required properties: sel_c, need_c (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * `enum: []` admits nothing, so carrying the property at all eliminates the branch.
     */
    @Test
    fun testOneOfLoneRequiredEmptyEnumPinEliminatesWhenPropertyPresent() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "anything"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        enum: []
                        .
                      foo:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - foo
                      =

                  - properties:
                      bar:
                        type: boolean
                        .
                      .
                    required:
                      - bar
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertEquals("Missing required properties: bar", errors[0].message.toString())
    }

    /**
     * Not eliminating lets presence of `kind` narrow the report to this branch, though no document with
     * `kind` can satisfy it: the known cost of not special-casing `enum: []`.
     */
    @Test
    fun testOneOfLoneOptionalEmptyEnumPinDoesNotEliminate() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "anything"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        enum: []
                        .
                      foo:
                        type: string
                        .
                      .
                    required:
                      - foo
                      =

                  - properties:
                      bar:
                        type: boolean
                        .
                      .
                    required:
                      - bar
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED,
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertEquals("Value must be one of: ", errors[0].message.toString())
        assertEquals("Missing required properties: foo", errors[1].message.toString())
    }

    @Test
    fun testOneOfPartialModeEliminatesContradictedBranch() {
        assertKsonSchemaErrors(
            """
                kind: "A"
                params:
                  alpha: 5
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      params:
                        type: object
                        properties:
                          alpha:
                            type: string
                            .
                          .
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
                      .
                    required:
                      - kind
            """.trimIndent(),
            listOf(
                SCHEMA_VALUE_TYPE_MISMATCH
            ),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }
}

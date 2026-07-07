package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnyOfValidatorTest : JsonSchemaTest {
    /**
     * A discriminated union whose branches are written as `$ref`s into `$defs`, under `anyOf`.  The
     * discriminator-aware reporting is shared with `oneOf`, so this exercises that the ref-resolving
     * pin detection is wired into `anyOf` too.
     */
    private val refDiscriminatedUnion = """
        {
          "anyOf": [
            { "${'$'}ref": "#/${'$'}defs/A" },
            { "${'$'}ref": "#/${'$'}defs/B" }
          ],
          "${'$'}defs": {
            "A": {
              "properties": {
                "kind": { "const": "A" },
                "params": { "type": "object", "required": ["alpha"] }
              },
              "required": ["kind"]
            },
            "B": {
              "properties": {
                "kind": { "const": "B" },
                "params": { "type": "object", "required": ["beta"] }
              },
              "required": ["kind"]
            }
          }
        }
    """.trimIndent()

    /**
     * The document carries only the *shared* `description` property (known by both branches, so not
     * distinguishing), so presence-based narrowing declines and the full-dump machinery runs — and since
     * `description` is wrong for every branch, its error is extracted as the single precise message.
     */
    @Test
    fun testAnyOfCommonValidationErrors() {
        assertKsonSchemaErrors(
            """
                description: 99
            """.trimIndent(),
            """
                anyOf:
                  - properties:
                      description:
                        type: string
                        .
                      thing:
                        type: number

                  - properties:
                      description:
                        type: string
                        .
                      .
                    required:
                      - required_prop
            """.trimIndent(),
            listOf(
                // note that since `description` is wrong for ALL the sub-schemas, we get a precise error for it.
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )
    }

    @Test
    fun testAnyOfPresenceNarrowsOnOptionalDeclaredProperty() {
        val anyOfSchemaRequired = """
                anyOf:
                  - properties:
                      description:
                        type: string
                        .
                      think:
                        type: number
                        .
                      .
                    required:
                      - required_branch_1
                      =

                  - properties:
                      description:
                        type: string
                        .
                      .
                    required:
                      - required_branch_2
                """.trimIndent()
        assertKsonSchemaErrors(
            """
                description: "describer"
                think: false
            """.trimIndent(),
            anyOfSchemaRequired,
            listOf(
                SCHEMA_VALUE_TYPE_MISMATCH, SCHEMA_REQUIRED_PROPERTY_MISSING
            ))

        assertKsonSchemaErrors(
            """
                description: "describer"
            """.trimIndent(),
            anyOfSchemaRequired,
            listOf(
                SCHEMA_ANY_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS
            ))
    }

    @Test
    fun testSubSchemaErrorsIncludeLocationAndTitle() {
        val errors = assertKsonSchemaErrors(
            """
                name: test
                value: hello
            """.trimIndent(),
            """
                anyOf:
                  - title: NumberModel
                    properties:
                      name:
                        type: string
                        .
                      value:
                        type: number
                        .
                      .
                    additionalProperties: false

                  - title: BooleanModel
                    properties:
                      name:
                        type: string
                        .
                      value:
                        type: boolean
                        .
                      .
                    additionalProperties: false
            """.trimIndent(),
            listOf(
                SCHEMA_ANY_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        val subSchemaMessage = errors[1].message.toString()
        assertContains(subSchemaMessage, "- NumberModel:\n    - Property 'value': Expected one of: number, but got: string (2:8)")
        assertContains(subSchemaMessage, "- BooleanModel:\n    - Property 'value': Expected one of: boolean, but got: string (2:8)")
    }

    /**
     * The discriminated-union reporting is shared with `oneOf`: an `anyOf` whose branches are keyed by
     * distinct `const`s selects the branch the document's discriminator picks.  `kind: "A"` selects
     * branch A, surfacing only its deeper `params` failure (missing `alpha`) — proving the shared
     * [reportUnionMatchFailure] helper is wired into `anyOf`, not just `oneOf`.
     */
    @Test
    fun testAnyOfDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "anyOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * The shared helper also recognizes `enum` pins under `anyOf`: single-value enums key the branches,
     * so `kind: "A"` selects branch A and only its `alpha` requirement surfaces.
     */
    @Test
    fun testAnyOfEnumDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "anyOf": [
                    {
                      "properties": {
                        "kind": { "enum": ["A"] },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "enum": ["B"] },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * A *closed* `anyOf` union — every branch pins `kind` — whose document matches no branch collapses
     * to a single enum error at the discriminator value, just as it does for `oneOf`.
     */
    @Test
    fun testAnyOfClosedUnionNoMatchReportsEnum() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            """
                {
                  "anyOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            // the enum error hangs off the `kind` value `Z`, the discriminator the user must fix
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8)
            )
        )

        assertContains(errors[0].message.toString(), "Value must be one of: \"A\", \"B\"")
    }

    /**
     * An *open* `anyOf` union — a trailing wildcard branch (`kind: { type: string }`) pins nothing — can't
     * prove a non-matching `kind` invalid, so discriminator selection declines rather than collapsing to an enum error.  But
     * the two pinned branches each pin `kind` to a value the document contradicts (`Z` is outside {A}, {B}),
     * so elimination drops them and narrows to the lone surviving wildcard branch, surfacing its deeper
     * `gamma` requirement instead of dumping every branch.
     */
    @Test
    fun testAnyOfOpenUnionNoMatchNarrowsToUnpinnedBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            """
                {
                  "anyOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "type": "string" },
                        "params": { "type": "object", "required": ["gamma"] }
                      },
                      "required": ["kind"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // the two pinned branches are eliminated by `kind: Z`; only the wildcard branch's `gamma` remains
        assertContains(errors[0].message.toString(), "gamma")
    }

    /**
     * Elimination is shared with `oneOf`, so a single pinned branch the document contradicts is
     * dropped under `anyOf` too: only branch A pins `kind` (too few to discriminate), but `kind: "B"` is
     * outside its `["A"]`, so branch A is eliminated and the union narrows to the unpinned branch, whose
     * missing `value` surfaces alone instead of a full dump.
     */
    @Test
    fun testAnyOfSinglePinnedBranchMismatchEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                {
                  "anyOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "extra_a": { "type": "string" }
                      },
                      "required": ["kind", "extra_a"]
                    },
                    {
                      "properties": { "value": { "type": "boolean" } },
                      "required": ["value"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // branch A (pinned `kind: "A"`) is eliminated by `kind: "B"`; only the unpinned branch's `value` remains
        assertContains(errors[0].message.toString(), "value")
        assertFalse(errors[0].message.toString().contains("extra_a"))
    }

    /**
     * `$ref`-based discriminated unions get the same focused reporting under `anyOf` as under `oneOf`:
     * `kind: "A"` resolves through the ref to branch A and surfaces only its `alpha` requirement, not
     * branch B's `beta`.
     */
    @Test
    fun testAnyOfRefBranchDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            refDiscriminatedUnion,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * A closed `$ref` `anyOf` union whose discriminator matches no branch collapses to one enum error
     * at the discriminator value — detection reads the pins through the refs, just as for `oneOf`.
     */
    @Test
    fun testAnyOfRefBranchClosedUnionNoMatchReportsEnum() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            refDiscriminatedUnion,
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8)
            )
        )

        assertContains(errors[0].message.toString(), "Value must be one of: \"A\", \"B\"")
    }

    /**
     * The presence-based fallback is shared with `oneOf`, so it fires under `anyOf` too: with no value
     * discriminator, the present `selector_a` (required only by branch A) narrows to branch A and only
     * its missing `needs_a` surfaces — proving the shared narrowing is wired into `anyOf`.
     */
    @Test
    fun testAnyOfPresenceSelectsSingleMatchingBranch() {
        val errors = assertKsonSchemaErrors(
            """
                selector_a: "present"
            """.trimIndent(),
            """
                {
                  "anyOf": [
                    {
                      "properties": { "selector_a": { "type": "string" }, "needs_a": { "type": "string" } },
                      "required": ["selector_a", "needs_a"]
                    },
                    {
                      "properties": { "selector_b": { "type": "string" }, "needs_b": { "type": "string" } },
                      "required": ["selector_b", "needs_b"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "needs_a")
        assertFalse(errors[0].message.toString().contains("needs_b"))
    }

    /**
     * A multi-branch presence match under `anyOf` narrows the dump to the branches keyed by the present
     * `shared` property (A and B), dropping branch C, and anchors it to the `anyOf`-specific no-match
     * message — confirming the fallback threads the caller's own message through the shared helper.
     */
    @Test
    fun testAnyOfPresenceReportsAllMatchingBranches() {
        val errors = assertKsonSchemaErrors(
            """
                shared: "x"
            """.trimIndent(),
            """
                {
                  "anyOf": [
                    {
                      "title": "BranchA",
                      "properties": { "shared": { "type": "string" }, "only_a": { "type": "string" } },
                      "required": ["shared", "only_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "shared": { "type": "string" }, "only_b": { "type": "string" } },
                      "required": ["shared", "only_b"]
                    },
                    {
                      "title": "BranchC",
                      "properties": { "gamma": { "type": "string" } },
                      "required": ["gamma"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ANY_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        val dump = errors[1].message.toString()
        assertContains(dump, "BranchA")
        assertContains(dump, "BranchB")
        assertFalse(dump.contains("BranchC"))
    }

    /**
     * The PARTIAL-mode guard: a *closed* discriminated `anyOf` whose discriminator matches no branch
     * reports one hard [SCHEMA_ENUM_VALUE_NOT_ALLOWED] in FULL mode, but in [ValidationMode.PARTIAL] a
     * half-typed discriminator shouldn't get that hard narrowing error — the guard skips union narrowing and
     * falls back to the plain per-branch dump ([SCHEMA_ANY_OF_VALIDATION_FAILED] + sub-schema errors).
     */
    @Test
    fun testAnyOfPartialModeSkipsHardEnumNarrowing() {
        val closedUnion = """
            {
              "anyOf": [
                {
                  "properties": {
                    "kind": { "const": "A" },
                    "params": { "type": "object", "required": ["alpha"] }
                  },
                  "required": ["kind"]
                },
                {
                  "properties": {
                    "kind": { "const": "B" },
                    "params": { "type": "object", "required": ["beta"] }
                  },
                  "required": ["kind"]
                }
              ]
            }
        """.trimIndent()
        val document = """
            kind: "Z"
            params: {}
        """.trimIndent()

        // FULL mode: the closed union proves `kind: "Z"` out of range with one hard enum error
        assertKsonSchemaErrors(
            document,
            closedUnion,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED)
        )

        // PARTIAL mode: the guard skips union narrowing, so no hard enum error — just the plain per-branch dump
        assertKsonSchemaErrors(
            document,
            closedUnion,
            listOf(SCHEMA_ANY_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }
}

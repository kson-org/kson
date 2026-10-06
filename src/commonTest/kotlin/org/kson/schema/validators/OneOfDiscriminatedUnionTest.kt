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
 * Narrowing on pinned values, whether inline, through a lone `$ref`, or mixed.  A value admitted by
 * one pin alone selects that branch; a value no pin admits is an enum error; a value outside a
 * branch's pin eliminates that branch.  Pins needn't be disjoint.
 */
class OneOfDiscriminatedUnionTest : JsonSchemaTest {
    /**
     * A discriminated union shaped like real-world schemas: `kind` repeats consts across branches
     * (so it can't discriminate), `kind_job` carries the distinct consts (the real discriminator),
     * each branch's `params` requires a different property, and a trailing wildcard branch pins
     * neither `kind` nor `kind_job` to a const.
     */
    private val duplicateConstUnionWithWildcard = """
        {
          "oneOf": [
            {
              "properties": {
                "kind": { "const": "A" },
                "kind_job": { "const": "J1" },
                "params": { "type": "object", "required": ["p1"] }
              },
              "required": ["kind", "kind_job", "params"]
            },
            {
              "properties": {
                "kind": { "const": "A" },
                "kind_job": { "const": "J2" },
                "params": { "type": "object", "required": ["p2"] }
              },
              "required": ["kind", "kind_job", "params"]
            },
            {
              "properties": {
                "kind": { "const": "B" },
                "kind_job": { "const": "J3" },
                "params": { "type": "object", "required": ["p3"] }
              },
              "required": ["kind", "kind_job", "params"]
            },
            {
              "properties": {
                "kind": { "type": "string" },
                "kind_job": { "not": { "enum": ["J1", "J2", "J3"] } },
                "params": { "type": "object", "required": ["p4"] }
              },
              "required": ["kind", "kind_job", "params"]
            }
          ]
        }
    """.trimIndent()

    /**
     * Branches written as `$ref`s into `$defs`, the dominant real-world shape.
     */
    private val refDiscriminatedUnion = """
        {
          "oneOf": [
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
     * A discriminated union (branches keyed by distinct `kind` consts) whose discriminator matches
     * one branch reports only that branch's deeper failure — here `params` is missing a required
     * property — instead of dumping every branch's errors.  The branches require *different* `params`
     * properties (as real discriminated unions do), so without discriminator-awareness this dumps
     * every branch; with it, only branch A's `alpha` requirement surfaces.
     */
    @Test
    fun testOneOfDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
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
            // only branch A's error — no SCHEMA_ONE_OF_VALIDATION_FAILED, no SCHEMA_SUB_SCHEMA_ERRORS dump
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            // and it lands inside `params`, the value the user must actually fix
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        // branch A was selected: its `alpha` requirement is reported, not branch B's `beta`
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
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "type": "string", "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "type": "string", "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    }
                  ]
                }
            """.trimIndent(),
            // only branch A's error — no SCHEMA_ONE_OF_VALIDATION_FAILED, no SCHEMA_SUB_SCHEMA_ERRORS dump
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            // and it lands inside `params`, the value the user must actually fix
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        // branch A selected via the `{ type, const }` discriminator: its `alpha` requirement surfaces, not `beta`
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
                {
                  "allOf": [
                    { "type": "object" },
                    {
                      "oneOf": [
                        {
                          "properties": {
                            "kind": { "const": "A" },
                            "kind_job": { "const": "A_JOB" },
                            "params": { "type": "object", "required": ["alpha"] }
                          },
                          "required": ["kind", "kind_job", "params"]
                        },
                        {
                          "properties": {
                            "kind": { "const": "B" },
                            "kind_job": { "const": "B_JOB" },
                            "params": { "type": "object", "required": ["beta"] }
                          },
                          "required": ["kind", "kind_job", "params"]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            // branch A selected via `kind`; its `params` failure is reported inside `params`
            listOf(
                Location(Coordinates(2, 8), Coordinates(2, 10), 36, 38)
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
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "p1")
    }

    /**
     * No enum error: the wildcard pins no `kind_job`, so it might accept `J9`.  The three contradicted
     * branches drop out instead, leaving the wildcard alone.
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
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // the three pinned branches are eliminated by `kind_job: J9`; only the wildcard branch's `p4` remains
        assertContains(errors[0].message.toString(), "p4")
    }

    /**
     * Selection never resurrects a contradicted branch: `kind_job: "J1"` is admitted by the J1 branch
     * alone and `kind: "B"` by the J3 branch alone, but each contradicts the other's pin, so the
     * surviving wildcard is reported instead.
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
                SCHEMA_REQUIRED_PROPERTY_MISSING
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
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "needs_a": { "type": "string" } },
                      "required": ["kind", "needs_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "kind": { "const": "B" }, "needs_b": { "type": "string" } },
                      "required": ["kind", "needs_b"]
                    },
                    {
                      "title": "BranchW",
                      "properties": { "version": { "const": 2 }, "needs_w": { "type": "string" } },
                      "required": ["version", "needs_w"]
                    },
                    {
                      "title": "UnpinnedBranch",
                      "properties": { "kind": { "type": "string" }, "version": { "type": "number" } },
                      "required": ["needs_unpinned"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
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
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["p1"] }
                      },
                      "required": ["kind", "params"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["p2"] }
                      },
                      "required": ["kind", "params"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["p3"] }
                      },
                      "required": ["kind", "params"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        // `kind: "A"` eliminates the `B` branch; the dump keeps only the two `A` branches (p1, p2), not p3
        val report = errors[1].message.toString()
        assertContains(report, "p1")
        assertContains(report, "p2")
        assertFalse(report.contains("p3"))
    }

    /**
     * A single-value `enum` pins a property exactly like `const` (`kind: { enum: ["A"] }`), so the
     * generalized [org.kson.schema.JsonSchemaValidator.pinnedValues] abstraction discriminates on it:
     * `kind: "A"` selects branch A and we report only its deeper `params` failure (missing `alpha`),
     * not branch B's `beta`.
     */
    @Test
    fun testOneOfSingleValueEnumDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
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
            // the selected branch's failure lands inside `params`
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        // branch A selected via its single-value `enum`: its `alpha` requirement surfaces, not `beta`
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
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": ["A", "B"] },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "enum": ["C"] },
                        "params": { "type": "object", "required": ["gamma"] }
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

        // `kind: "B"` is the second element of branch A's `["A", "B"]`, so branch A's `alpha` surfaces
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
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": ["A", "B"] },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "enum": ["B", "C"] },
                        "params": { "type": "object", "required": ["beta"] }
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

        // `kind: "A"` is outside branch B's `["B", "C"]`, eliminating it; only branch A's `alpha` remains
        assertContains(errors[0].message.toString(), "alpha")
        assertFalse(errors[0].message.toString().contains("beta"))
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
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": ["A"] },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "enum": [] },
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
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8)
            )
        )

        assertEquals("Value must be one of: \"A\"", errors[0].message.toString())
    }

    /**
     * The discriminator-aware reporting fires even when branches are written as `$ref`s (the common
     * real-world shape): `kind: "A"` resolves through the ref to branch A and surfaces only its deeper
     * `params` failure (missing `alpha`), not branch B's `beta`.
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
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            // the selected branch's failure lands inside `params`, exactly as the inline union's does
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        // branch A selected through its `$ref`: its `alpha` requirement surfaces, not branch B's `beta`
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
     * A discriminated union may mix an inline branch with a `$ref` branch: both pins are read (the
     * inline `const` and the ref target's), so `kind: "B"` resolves through the ref to branch B and
     * surfaces only its `beta` requirement — proving the ref branch's pin drove discrimination.
     */
    @Test
    fun testOneOfMixedInlineAndRefPinsSelect() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "B"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    { "${'$'}ref": "#/${'$'}defs/B" }
                  ],
                  "${'$'}defs": {
                    "B": {
                      "properties": {
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    }
                  }
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        // branch B selected through its `$ref`, alongside the inline branch A: its `beta` requirement surfaces
        assertContains(errors[0].message.toString(), "beta")
    }

    /**
     * PARTIAL-mode guard mirror for `oneOf`: the closed union that collapses to one hard
     * [SCHEMA_ENUM_VALUE_NOT_ALLOWED] in FULL mode (see [testOneOfDiscriminatorMatchesNoBranch]) falls
     * back to the plain per-branch dump in [ValidationMode.PARTIAL], where a half-typed discriminator
     * shouldn't receive a hard narrowing error.
     */
    @Test
    fun testOneOfPartialModeSkipsHardEnumNarrowing() {
        val closedUnion = """
            {
              "oneOf": [
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
            listOf(SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }
}

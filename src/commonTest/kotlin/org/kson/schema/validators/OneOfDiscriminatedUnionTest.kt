package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Value-based discriminated unions: a shared property pinned to pairwise-disjoint `const`/`enum`
 * values (inline, through a lone `$ref`, or a mix) selects the branch the document's discriminator
 * picks, so only that branch's deeper failure is reported.  Also covers the cases where no
 * discriminator forms (duplicate / overlapping / empty pins) — where discriminator detection declines and
 * elimination drops the branches the document's value contradicts — and the closed-union enum error.
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
     * A discriminated union whose branches are written as `$ref`s into `$defs` — the dominant
     * real-world shape.  Each branch pins `kind` to a distinct `const` and requires a different
     * `params` property, so it discriminates exactly like the inline unions above, but only once
     * detection resolves through the refs to the targets' properties.
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
     * The motivating real-world discriminator shape is `{ type: string, const: X }` — a `const` with a
     * sibling `type` — not the bare `{ const: X }` the other tests use.  The sibling `type` parses into
     * a separate type-validator, so the property's `schemaValidators` is still just `[ConstValidator]`
     * and discriminator detection selects the matching branch exactly as it does for a bare `const`.
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
     * When the discriminator value matches no branch's `const`, collapse to one enum error at the
     * discriminator value rather than dumping every branch.
     */
    @Test
    fun testOneOfDiscriminatorMatchesNoBranch() {
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
            // the enum error hangs off the `kind` value `Z` (the discriminator the user must fix)
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8)
            )
        )

        assertContains(errors[0].message.toString(), "Value must be one of: \"A\", \"B\"")
    }

    /**
     * Mirrors the real `allOf[ base, oneOf[ branches ] ]` shape: the discriminator-aware reporting
     * still applies through the `allOf` wrapper, and branches keyed by dual consts (`kind` +
     * `kind_job`) discriminate on the first const property in declaration order.
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

        // selected branch A via `kind` (the first const property), so its `alpha` requirement surfaces
        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * Real-schema reduction: `kind` consts repeat (A, A, B) so it can't discriminate, but `kind_job`
     * consts are distinct (J1, J2, J3) and a trailing wildcard branch pins neither.  Detection must
     * tolerate the duplicate-const property and the wildcard, choose `kind_job`, and report only the
     * branch the document's `kind_job` selects — here the J1 branch's `params` requirement.
     *
     * The document's `kind` is `A`, shared by the J1 and J2 branches, so `J1` and `kind: A` point at
     * different branches: selecting J1's branch (requires `p1`) — not the `kind`-ambiguous J2 branch
     * (requires `p2`) — proves `kind_job`, not `kind`, drove the choice.
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
     * With a wildcard branch present, a `kind_job` value that matches no pinned branch must NOT collapse
     * to a closed-union enum error — the wildcard might legitimately accept it — so discriminator selection declines.  But
     * each of the three pinned branches pins `kind_job` to a value the document contradicts (`J9` is
     * outside {J1}, {J2}, {J3}), so elimination drops all three and narrows to the lone surviving wildcard
     * branch, surfacing its deeper `p4` requirement instead of dumping every branch.
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
     * When the only shared const property repeats consts across branches (`kind`: A, A, B), it can't
     * *discriminate* — no single branch a value selects — so discriminator selection declines rather than arbitrarily
     * picking one of the `A` branches.  A mismatch still *eliminates*, though: `kind: "A"` is outside the
     * `B` branch's pin, dropping it, so the dump narrows to just the two `A` branches (each missing its
     * own `params` property) and omits the `B` branch's `p3` entirely.
     */
    @Test
    fun testOneOfDuplicateConstPropertyDoesNotDiscriminate() {
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
        val dump = errors[1].message.toString()
        assertContains(dump, "p1")
        assertContains(dump, "p2")
        assertFalse(dump.contains("p3"))
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
     * A multi-value `enum` pins a property to a *set* of values; as long as the branches' sets are
     * disjoint they still discriminate.  Branch A pins `kind` to `["A", "B"]` and branch B to `["C"]`,
     * so the document's `kind: "B"` selects branch A — surfacing its `alpha` requirement, not branch
     * B's `gamma` — proving the whole value set, not just the first element, maps to the branch.
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
     * Overlapping `enum` sets can't *discriminate*: branch A pins `kind` to `["A", "B"]` and branch B to
     * `["B", "C"]`, so `"B"` would select both — the disjointness rule disqualifies `kind` as a discriminator.
     * A mismatch still *eliminates*, though: `kind: "A"` is outside branch B's `["B", "C"]`, so branch B
     * is provably dead and drops out, narrowing to branch A alone and surfacing its `alpha` requirement
     * rather than dumping both branches.
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
     * An empty `enum: []` pins a property to no values.  It can't form a *discriminator*: with no
     * selectable value it's excluded from detection, leaving branch A's lone `kind` pin — too few — so
     * discriminator selection declines.  Elimination reads empty pins too, and `kind: "Z"` is outside branch A's `["A"]`
     * *and* outside branch B's `[]` (which admits nothing), so both branches are eliminated, the survivor
     * set is empty, and we keep the full per-branch dump rather than narrowing to the unsatisfiable
     * `enum: []` branch.
     */
    @Test
    fun testOneOfEmptyEnumSetDoesNotDiscriminate() {
        assertKsonSchemaErrors(
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
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
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

    /**
     * A closed `$ref` union whose discriminator matches no branch collapses to one enum error at the
     * discriminator value, just as the inline closed union does — detection reads the pins through the refs.
     */
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
    fun testOneOfMixedInlineAndRefBranchesDiscriminate() {
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
}

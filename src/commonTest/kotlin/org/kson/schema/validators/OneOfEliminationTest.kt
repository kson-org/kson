package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * *Elimination*: a branch that pins a property to a value set the document contradicts is
 * provably dead and drops out of the reported errors — with none of the ≥2-branch or disjointness
 * gating discriminator selection requires.  Elimination composes with presence narrowing as
 * `S ∩ M` (else the survivors `S`), so these cases also pin down that composition: a single
 * contradicted pin narrows the dump, all-eliminated unions still fall back to the full dump, and
 * presence can never resurrect a branch elimination has already ruled out.
 */
class OneOfEliminationTest : JsonSchemaTest {
    /**
     * A union whose branches share `kind` — pinned to a `const` by some, repeated across others — with
     * each branch requiring its own `params` property.  `kind` can't discriminate (`A` repeats) but a
     * mismatch still eliminates, so it exercises the composition without ever forming a discriminator.
     */
    private val duplicateConstUnion = """
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
    """.trimIndent()

    /**
     * A single pinned branch is too few to form a value discriminator (that needs ≥2), but a contradicted
     * pin still eliminates: only branch A pins `kind`, and `kind: "B"` is outside its `["A"]`, so branch A
     * is dropped and the union narrows to the lone unpinned branch — surfacing its missing `value` alone
     * rather than dumping both branches.
     */
    @Test
    fun testOneOfSinglePinnedBranchMismatchEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                {
                  "oneOf": [
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
     * Non-disjoint pins (`kind`: A, A, B) can't discriminate, but a mismatch eliminates every branch that
     * pins `kind` away from the document's value: `kind: "B"` drops both `A` branches, narrowing to the
     * lone `B` branch and surfacing its deeper `p3` requirement as a bare message.
     */
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

        // both `A` branches are eliminated by `kind: "B"`; only the surviving `B` branch's `p3` remains
        assertContains(errors[0].message.toString(), "p3")
        assertFalse(errors[0].message.toString().contains("p1"))
        assertFalse(errors[0].message.toString().contains("p2"))
    }

    /**
     * When the document's value contradicts *every* branch's pin (`kind: "Z"` is outside {A} and {B}), the
     * survivor set is empty — elimination can't narrow to nothing — so we fall back to the full per-branch
     * dump rather than inventing a "must be one of" error from the non-disjoint pins.
     */
    @Test
    fun testOneOfAllBranchesEliminatedKeepsFullDump() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            duplicateConstUnion,
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        // every branch is eliminated, so none is narrowed away: the dump still lists all three
        val dump = errors[1].message.toString()
        assertContains(dump, "p1")
        assertContains(dump, "p2")
        assertContains(dump, "p3")
    }

    /**
     * A multi-value `enum` pin eliminates just like a `const`: branch A pins `kind` to `["A", "B"]`, and
     * `kind: "C"` is outside that whole set, so branch A is dropped and the union narrows to the unpinned
     * branch — proving elimination tests membership in the full pinned set, not just its first value.
     */
    @Test
    fun testOneOfMultiValueEnumPinEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "C"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": ["A", "B"] },
                        "foo": { "type": "string" }
                      },
                      "required": ["kind", "foo"]
                    },
                    {
                      "properties": { "baz": { "type": "boolean" } },
                      "required": ["baz"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // `kind: "C"` is outside `["A", "B"]`, eliminating branch A; only the unpinned branch's `baz` remains
        assertContains(errors[0].message.toString(), "baz")
        assertFalse(errors[0].message.toString().contains("foo"))
    }

    /**
     * The composition prefers `S ∩ M`: elimination drops branch A (`kind: "X"` ∉ `["A"]`), leaving two
     * survivors, and presence then refines those to the single branch whose distinguishing property the
     * document carries.  With `kind: "X"` alone, `need_b`'s branch is the one both signals agree on, so its
     * missing `need_b` surfaces — neither the eliminated branch A's `need_a` nor the unmatched branch C.
     */
    @Test
    fun testOneOfEliminationRefinesPresenceMatch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "need_a": { "type": "string" } },
                      "required": ["kind", "need_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "kind": { "type": "string" }, "need_b": { "type": "string" } },
                      "required": ["kind", "need_b"]
                    },
                    {
                      "title": "BranchC",
                      "properties": { "other": { "type": "string" }, "need_c": { "type": "string" } },
                      "required": ["other", "need_c"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // survivors {B, C} refined by presence of `kind` to just B; A eliminated, C unmatched
        assertContains(errors[0].message.toString(), "need_b")
        assertFalse(errors[0].message.toString().contains("need_a"))
        assertFalse(errors[0].message.toString().contains("need_c"))
    }

    /**
     * Presence must not resurrect an eliminated branch.  `kind: "X"` eliminates branch A, yet `kind` is
     * also the only property branch A *knows*, so presence alone would match A — the exact branch just
     * proven dead.  Intersecting with the survivors drops A from the presence match, leaving `S ∩ M` empty,
     * so the report falls back to the survivors {B, C} and never mentions A's requirements.
     */
    @Test
    fun testOneOfPresenceCannotResurrectEliminatedBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "need_a": { "type": "string" } },
                      "required": ["kind", "need_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "sel_b": { "type": "string" }, "need_b": { "type": "string" } },
                      "required": ["sel_b", "need_b"]
                    },
                    {
                      "title": "BranchC",
                      "properties": { "sel_c": { "type": "string" }, "need_c": { "type": "string" } },
                      "required": ["sel_c", "need_c"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        // branch A is eliminated despite presence matching it, so the dump narrows to the survivors, not A
        val dump = errors[1].message.toString()
        assertContains(dump, "BranchB")
        assertContains(dump, "BranchC")
        assertFalse(dump.contains("BranchA"))
    }

    /**
     * An empty `enum: []` admits no value, so it eliminates any branch once the document carries the
     * pinned property: branch A pins `kind` to `[]`, and `kind: "anything"` is outside it, so branch A is
     * dropped and the union narrows to the unpinned branch — surfacing its missing `bar` alone.
     */
    @Test
    fun testOneOfEmptyEnumPinEliminatesWhenPropertyPresent() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "anything"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": [] },
                        "foo": { "type": "string" }
                      },
                      "required": ["kind", "foo"]
                    },
                    {
                      "properties": { "bar": { "type": "boolean" } },
                      "required": ["bar"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // the empty-pin branch is eliminated by the present `kind`; only the unpinned branch's `bar` remains
        assertContains(errors[0].message.toString(), "bar")
        assertFalse(errors[0].message.toString().contains("foo"))
    }
}

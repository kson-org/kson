package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * The presence-based fallback: when no value discriminator applies, narrow the union to the
 * branch(es) whose *known* (declared ∪ required) properties the document actually carries, and
 * report only those.  Value-based discrimination keeps priority when both could apply.
 */
class OneOfPresenceUnionTest : JsonSchemaTest {
    /**
     * A union with no value discriminator (no property pinned to a `const`/`enum`) but distinct
     * *required* properties per branch — the shape the presence-based fallback narrows.  Each branch
     * has its own selector property plus a deeper requirement that a matched-but-incomplete document trips.
     */
    private val presenceUnion = """
        {
          "oneOf": [
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
    """.trimIndent()

    /**
     * A single pinned branch is too few to form a value discriminator, so presence takes over.  `kind`
     * is a known property of only the `node` branch (declared there, absent from the other), so the
     * document's `kind` narrows to `node` and surfaces its missing `child`.  Both pin detection and the
     * known-property accessors resolve the `node` `$ref` exactly one hop and never follow its `child`
     * property — itself a `$ref` back to `node` — so the self-reference can't drive infinite recursion.
     */
    @Test
    fun testOneOfRefBranchSelfReferentialPropertyPresenceNarrowsToNodeBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "node"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    { "${'$'}ref": "#/${'$'}defs/node" },
                    {
                      "properties": { "value": { "type": "boolean" } },
                      "required": ["value"]
                    }
                  ],
                  "${'$'}defs": {
                    "node": {
                      "properties": {
                        "kind": { "const": "node" },
                        "child": { "${'$'}ref": "#/${'$'}defs/node" }
                      },
                      "required": ["child"]
                    }
                  }
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // narrowed to the `node` branch via its declared `kind`: its missing `child` surfaces, not the other branch's `value`
        assertContains(errors[0].message.toString(), "child")
        assertFalse(errors[0].message.toString().contains("value"))
    }

    /**
     * With no value discriminator, the presence-based fallback picks the branch whose distinguishing
     * required property the document actually carries: `selector_a` is present (and required only by
     * branch A), so only branch A's deeper failure — its missing `needs_a` — surfaces, not branch B's
     * requirements.  A single narrowed branch collapses to that branch's error directly, with no dump.
     */
    @Test
    fun testOneOfPresenceSelectsSingleMatchingBranch() {
        val errors = assertKsonSchemaErrors(
            """
                selector_a: "present"
            """.trimIndent(),
            presenceUnion,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // branch A selected by presence of `selector_a`: its `needs_a` requirement surfaces, not branch B's
        assertContains(errors[0].message.toString(), "needs_a")
        assertFalse(errors[0].message.toString().contains("needs_b"))
    }

    /**
     * When a distinguishing required property is shared by two branches (`shared`, required by A and B
     * but not by C), presence narrows to *both* — and only those — branches: the dump lists branch A
     * and branch B (each missing its own `only_*` property) but omits branch C entirely.
     */
    @Test
    fun testOneOfPresenceReportsAllMatchingBranches() {
        val errors = assertKsonSchemaErrors(
            """
                shared: "x"
            """.trimIndent(),
            """
                {
                  "oneOf": [
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
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        // the dump narrows to the two branches keyed by the present `shared` property, dropping BranchC
        val dump = errors[1].message.toString()
        assertContains(dump, "BranchA")
        assertContains(dump, "BranchB")
        assertFalse(dump.contains("BranchC"))
    }

    /**
     * When the document carries none of the branches' distinguishing required properties, presence
     * can't narrow anything, so we keep the full per-branch dump exactly as before — every branch's
     * requirements are reported.
     */
    @Test
    fun testOneOfPresenceNoMatchDumpsAllBranches() {
        val errors = assertKsonSchemaErrors(
            """
                unrelated: "x"
            """.trimIndent(),
            presenceUnion,
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        // no narrowing: both branches' requirements appear in the dump
        val dump = errors[1].message.toString()
        assertContains(dump, "selector_a")
        assertContains(dump, "selector_b")
    }

    /**
     * When both strategies could apply — the branches share a value discriminator (`kind` pinned to distinct
     * consts) *and* have distinct required properties — the value discriminator wins.  The document's
     * `kind: "A"` selects branch A (surfacing its missing `alpha`), even though the present `beta` would
     * have made presence narrow to branch B instead.
     */
    @Test
    fun testOneOfValueDiscriminatorTakesPrecedenceOverPresence() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                beta: "present"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": { "kind": { "const": "A" }, "alpha": { "type": "string" } },
                      "required": ["kind", "alpha"]
                    },
                    {
                      "properties": { "kind": { "const": "B" }, "beta": { "type": "string" } },
                      "required": ["kind", "beta"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // value discriminator selected branch A via `kind`; presence would have picked branch B via `beta`
        assertContains(errors[0].message.toString(), "alpha")
        assertFalse(errors[0].message.toString().contains("beta"))
    }

    /**
     * A `$ref`-based union with no value discriminator (the targets pin nothing) but distinct *required*
     * properties still narrows by presence: [org.kson.schema.JsonObjectSchema.requiredProperties] resolves
     * through each branch's `$ref` to read its target's requirements, so the present `needs_a` selects
     * branch A and only its deeper `detail_a` failure surfaces, not branch B's.
     */
    @Test
    fun testOneOfPresenceNarrowsThroughRefBranch() {
        val errors = assertKsonSchemaErrors(
            """
                needs_a: "present"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    { "${'$'}ref": "#/${'$'}defs/A" },
                    { "${'$'}ref": "#/${'$'}defs/B" }
                  ],
                  "${'$'}defs": {
                    "A": {
                      "properties": { "needs_a": { "type": "string" }, "detail_a": { "type": "string" } },
                      "required": ["needs_a", "detail_a"]
                    },
                    "B": {
                      "properties": { "needs_b": { "type": "string" }, "detail_b": { "type": "string" } },
                      "required": ["needs_b", "detail_b"]
                    }
                  }
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        // branch A selected by presence of `needs_a` (read through its `$ref`): its `detail_a` surfaces
        assertContains(errors[0].message.toString(), "detail_a")
        assertFalse(errors[0].message.toString().contains("detail_b"))
    }

    /**
     * Mirrors the real-world deployment-target case: three `kind`-keyed branches, but the document omits
     * `kind` so the value discriminator declines.  `region` is *required* by Lambda yet only *declared*
     * (optional) by Kubernetes — matching on known properties (declared ∪ required) narrows to both, since
     * both recognize `region`, while Static (which never mentions `region`) is dropped.  Matching on
     * `required` alone would wrongly pin to Lambda only.
     */
    @Test
    fun testOneOfPresenceMatchesBranchesDeclaringPropertyEvenWhenOptional() {
        val errors = assertKsonSchemaErrors(
            """
                region: "us-east"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "Kubernetes",
                      "properties": {
                        "kind": { "const": "kubernetes" },
                        "cluster": { "type": "string" },
                        "region": { "type": "string" }
                      },
                      "required": ["kind", "cluster"]
                    },
                    {
                      "title": "Lambda",
                      "properties": {
                        "kind": { "const": "lambda" },
                        "region": { "type": "string" }
                      },
                      "required": ["kind", "region"]
                    },
                    {
                      "title": "Static",
                      "properties": {
                        "kind": { "const": "static" },
                        "bucket": { "type": "string" }
                      },
                      "required": ["kind", "bucket"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        // `region` is known to Kubernetes (optional) and Lambda (required): both reported, Static excluded
        val dump = errors[1].message.toString()
        assertContains(dump, "Kubernetes")
        assertContains(dump, "Lambda")
        assertFalse(dump.contains("Static"))
    }
}

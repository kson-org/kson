package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Presence narrowing: among the branches no pin rules out, those that know (declare or require) a
 * property the document carries.  Pins keep priority: a contradicted one eliminates, and one only a
 * branch admits selects it.
 */
class OneOfPresenceUnionTest : JsonSchemaTest {
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
     * The `node` `$ref` is resolved one hop and its `child` property — a `$ref` back to `node` — is
     * never followed, so the self-reference can't recurse.
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
                        "kind": { "type": "string" },
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

        assertContains(errors[0].message.toString(), "child")
        assertFalse(errors[0].message.toString().contains("value"))
    }

    /**
     * A single narrowed branch reports its error directly, not grouped under the no-match message.
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

        assertContains(errors[0].message.toString(), "needs_a")
        assertFalse(errors[0].message.toString().contains("needs_b"))
    }

    /**
     * A property two branches know and the third doesn't narrows to both.
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

        val report = errors[1].message.toString()
        assertContains(report, "BranchA")
        assertContains(report, "BranchB")
        assertFalse(report.contains("BranchC"))
    }

    @Test
    fun testOneOfPresenceNoMatchReportsAllBranches() {
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

        val report = errors[1].message.toString()
        assertContains(report, "selector_a")
        assertContains(report, "selector_b")
    }

    /**
     * Branch B takes any `kind`, so nothing is eliminated and `kind` is no presence clue; presence
     * alone would pick B via `beta`, but `kind: "A"` is admitted only by branch A's pin, which selects it.
     */
    @Test
    fun testOneOfPinnedValueTakesPrecedenceOverPresence() {
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
                      "properties": {
                        "kind": { "type": "string" },
                        "beta": { "type": "string" },
                        "gamma": { "type": "string" }
                      },
                      "required": ["beta", "gamma"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "alpha")
        assertFalse(errors[0].message.toString().contains("gamma"))
    }

    /**
     * Known properties are read through each branch's `$ref`.
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

        assertContains(errors[0].message.toString(), "detail_a")
        assertFalse(errors[0].message.toString().contains("detail_b"))
    }

    /**
     * `region` is required by Lambda but only declared by Kubernetes; matching on `required` alone would
     * wrongly narrow to Lambda.
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

        val report = errors[1].message.toString()
        assertContains(report, "Kubernetes")
        assertContains(report, "Lambda")
        assertFalse(report.contains("Static"))
    }
}

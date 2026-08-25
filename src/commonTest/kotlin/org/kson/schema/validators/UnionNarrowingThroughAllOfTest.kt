package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Union narrowing reads a branch's pins and known properties across everything the branch composes,
 * so a branch shaped `allOf: [{ $ref: Base }, { oneOf: [ … ] }]` — what code generators emit for "a
 * base type refined by variants" — narrows exactly like one declaring those properties inline.
 * Reading only a branch's own `properties` made such a branch look empty: nothing to eliminate it,
 * nothing to presence-match it, so every union containing one dumped all its branches' errors.
 */
class UnionNarrowingThroughAllOfTest : JsonSchemaTest {
    /**
     * Presence narrowing picks `Item`: the document carries `mode`, declared by `Base` an `allOf` hop
     * down and unknown to `Group`, so the bad `mode` value surfaces alone rather than alongside
     * `Group`'s complaints about a shape the document never claimed.
     */
    @Test
    fun testAnyOfPresenceNarrowsThroughAllOfComposedBranch() {
        val errors = assertKsonSchemaErrors(
            """
                mode: "sync"
                action: "fetch"
            """.trimIndent(),
            """
                {
                  "anyOf": [
                    { "${'$'}ref": "#/${'$'}defs/Group" },
                    { "${'$'}ref": "#/${'$'}defs/Item" }
                  ],
                  "${'$'}defs": {
                    "Group": {
                      "additionalProperties": false,
                      "properties": { "name": {}, "members": {} },
                      "required": ["members"]
                    },
                    "Base": {
                      "properties": { "name": {}, "mode": {}, "action": {} }
                    },
                    "Item": {
                      "allOf": [
                        { "${'$'}ref": "#/${'$'}defs/Base" },
                        {
                          "oneOf": [
                            { "properties": { "mode": { "const": "read" }, "action": { "const": "fetch" } } },
                            { "properties": { "mode": { "const": "write" }, "action": { "const": "store" } } }
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED)
        )

        // the surviving error is about `mode`, not about Group's unmet shape
        assertContains(errors[0].message.toString(), "read")
        assertFalse(errors[0].message.toString().contains("members"))
    }

    /**
     * A discriminator pinned inside an `allOf` member discriminates: the document's `kind` selects its
     * branch, so only that branch's deeper failure is reported.
     */
    @Test
    fun testOneOfDiscriminatorDetectedThroughAllOfComposedBranch() {
        val errors = assertKsonSchemaErrors(
            """kind: "a"""",
            dualBranchAllOfUnion,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING)
        )

        assertContains(errors[0].message.toString(), "needs_a")
        assertFalse(errors[0].message.toString().contains("needs_b"))
    }

    /**
     * With every branch pinning `kind` through its `allOf`, the union is closed, so a `kind` no branch
     * claims is reported as one out-of-range value rather than as a dump of both branches.
     */
    @Test
    fun testOneOfClosedUnionThroughAllOfReportsAllowedDiscriminatorValues() {
        val errors = assertKsonSchemaErrors(
            """kind: "c"""",
            dualBranchAllOfUnion,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED)
        )

        assertContains(errors[0].message.toString(), "\"a\", \"b\"")
    }

    /** Two branches, each pinning `kind` to its own `const` one `allOf` hop down, over a shared base. */
    private val dualBranchAllOfUnion = """
        {
          "oneOf": [
            {
              "allOf": [
                { "${'$'}ref": "#/${'$'}defs/base" },
                { "properties": { "kind": { "const": "a" } } }
              ],
              "required": ["needs_a"]
            },
            {
              "allOf": [
                { "${'$'}ref": "#/${'$'}defs/base" },
                { "properties": { "kind": { "const": "b" } } }
              ],
              "required": ["needs_b"]
            }
          ],
          "${'$'}defs": {
            "base": { "properties": { "kind": { "type": "string" } } }
          }
        }
    """.trimIndent()

    /**
     * Composed sources all constrain the same document, so a property pinned by more than one is
     * pinned to the *intersection* of their sets: the base admits `"A"` or `"B"`, the refining member
     * only `"A"`, so `kind: "B"` contradicts the composed branch and eliminates it — where the wider
     * of the two pins alone would have kept it alive.  Both branches know `kind` and `other` and
     * neither `needs_*` is present, so this elimination is the only thing that can narrow here.
     */
    @Test
    fun testEliminationIntersectsPinsAcrossComposedSources() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
                other: "present"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": { "kind": {}, "other": {} },
                      "allOf": [
                        { "properties": { "kind": { "const": "A" } } },
                        { "properties": { "kind": { "enum": ["A", "B"] } } }
                      ],
                      "required": ["needs_a"]
                    },
                    {
                      "properties": { "kind": {}, "other": {} },
                      "required": ["needs_b"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING)
        )

        assertContains(errors[0].message.toString(), "needs_b")
        assertFalse(errors[0].message.toString().contains("needs_a"))
    }
}

package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Pins and known properties are read through `$ref` and `allOf`, so a branch shaped
 * `allOf: [{ $ref: Base }, { oneOf: [ … ] }]` — what code generators emit for a base type refined by
 * variants — narrows like one declaring those properties inline.
 */
class UnionNarrowingThroughAllOfTest : JsonSchemaTest {
    /**
     * `mode` is declared by `Base`, an `allOf` hop down, and unknown to `Group`, so presence narrows to `Item`.
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

        assertContains(errors[0].message.toString(), "read")
        assertFalse(errors[0].message.toString().contains("members"))
    }

    @Test
    fun testOneOfPinSelectsThroughAllOfComposedBranch() {
        val errors = assertKsonSchemaErrors(
            """kind: "a"""",
            dualBranchAllOfUnion,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING)
        )

        assertContains(errors[0].message.toString(), "needs_a")
        assertFalse(errors[0].message.toString().contains("needs_b"))
    }

    @Test
    fun testOneOfClosedUnionThroughAllOfReportsAllowedPinnedValues() {
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
     * A property several composed sources pin is pinned to the intersection: the base admits `"A"` or
     * `"B"`, the refining member only `"A"`, so `kind: "B"` eliminates the branch the wider pin alone
     * would keep.
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

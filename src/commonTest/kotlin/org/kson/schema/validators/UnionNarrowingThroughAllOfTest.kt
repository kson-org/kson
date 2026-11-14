package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins, required and known properties are read through `$ref` and `allOf`, so a branch shaped
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
            $$"""
                anyOf:
                  - '$ref': '#/$defs/Group'
                  - '$ref': '#/$defs/Item'
                    .
                '$defs':
                  Group:
                    additionalProperties: false
                    properties:
                      name: {}
                      members: {}
                      .
                    required:
                      - members
                    .
                  Base:
                    properties:
                      name: {}
                      mode: {}
                      action: {}
                      .
                    .
                  Item:
                    allOf:
                      - '$ref': '#/$defs/Base'
                      - oneOf:
                          - properties:
                              mode:
                                const: read
                                .
                              action:
                                const: fetch

                          - properties:
                              mode:
                                const: write
                                .
                              action:
                                const: store
            """.trimIndent(),
            // `Item`'s own `oneOf`, led by the enum error, lists its branches, then the outer `anyOf` lists `Group`
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED, SCHEMA_SUB_SCHEMA_ERRORS, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertContains(errors[0].message.toString(), "read")
        assertFalse(errors[0].message.toString().contains("members"))
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- Group:\n" +
                "    - Additional property 'mode' is not allowed (1:1)\n" +
                "    - Additional property 'action' is not allowed (2:1)\n" +
                "    - Missing required properties: members (1:1)",
            errors[2].message.toString()
        )
    }

    @Test
    fun testOneOfPinSelectsThroughAllOfComposedBranch() {
        val errors = assertKsonSchemaErrors(
            """kind: "a"""",
            dualBranchAllOfUnion,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertContains(errors[0].message.toString(), "needs_a")
        assertFalse(errors[0].message.toString().contains("needs_b"))
    }

    @Test
    fun testOneOfClosedUnionThroughAllOfReportsAllowedPinnedValues() {
        val errors = assertKsonSchemaErrors(
            """kind: "c"""",
            dualBranchAllOfUnion,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertContains(errors[0].message.toString(), "\"a\", \"b\"")
    }

    /** Two branches, each pinning `kind` to its own `const` one `allOf` hop down, over a shared base. */
    private val dualBranchAllOfUnion = $$"""
        oneOf:
          - allOf:
              - '$ref': '#/$defs/base'
              - properties:
                  kind:
                    const: a
                    .
                  .
                .
            required:
              - needs_a
              =

          - allOf:
              - '$ref': '#/$defs/base'
              - properties:
                  kind:
                    const: b
                    .
                  .
                .
            required:
              - needs_b
            .
        '$defs':
          base:
            properties:
              kind:
                type: string
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
                oneOf:
                  - properties:
                      kind: {}
                      other: {}
                      .
                    allOf:
                      - properties:
                          kind:
                            const: A
                      - properties:
                          kind:
                            enum:
                              - A
                              - B
                            .
                          .
                        .
                    required:
                      - needs_a
                      =

                  - properties:
                      kind:
                        const: B
                        .
                      other: {}
                      .
                    required:
                      - needs_b
            """.trimIndent(),
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Missing required properties: needs_b", errors[0].message.toString())
    }

    @Test
    fun testOneOfPinsComposedWithinOneBranchCountOnce() {
        val errors = assertKsonSchemaErrors(
            """kind: "B"""",
            $$"""
                oneOf:
                  - '$ref': '#/$defs/Composed'
                  - title: Plain
                    properties:
                      kind: {}
                      .
                    required:
                      - needs_plain
                    .
                '$defs':
                  Composed:
                    allOf:
                      - properties:
                          kind:
                            const: A
                      - properties:
                          kind:
                            enum:
                              - A
                              - B
                            .
                          .
                        .
                    required:
                      - needs_composed
            """.trimIndent(),
            listOf(SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- Composed:\n" +
                "    - Missing required properties: needs_composed (1:1)\n" +
                "    - Value must be exactly equal to \"A\" (1:8)\n" +
                "- Plain:\n" +
                "    - Missing required properties: needs_plain (1:1)",
            errors[1].message.toString()
        )
    }

    @Test
    fun testOneOfPinInheritedFromSharedBaseCountsForEachBranch() {
        val errors = assertKsonSchemaErrors(
            """kind: "c"""",
            $$"""
                oneOf:
                  - allOf:
                      - '$ref': '#/$defs/base'
                        .
                    required:
                      - needs_a
                      =

                  - allOf:
                      - '$ref': '#/$defs/base'
                        .
                    required:
                      - needs_b
                      =

                  - properties:
                      kind:
                        type: string
                        .
                      .
                    required:
                      - needs_c
                    .
                '$defs':
                  base:
                    properties:
                      kind:
                        enum:
                          - a
                          - b
            """.trimIndent(),
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Missing required properties: needs_c", errors[0].message.toString())
    }

    @Test
    fun testOneOfLonePinRequiredByAnotherAllOfMemberNarrows() {
        assertLoneRequiredPinNarrows(
            """
                oneOf:
                  - allOf:
                      - properties:
                          kind:
                            const: A
                      - required:
                          - kind
                        .
                    required:
                      - needs_pinned
                      =

                  - properties:
                      kind:
                        type: string
                        .
                      .
                    required:
                      - needs_plain
            """.trimIndent()
        )
    }

    @Test
    fun testOneOfLonePinRequiredThroughRefNarrows() {
        assertLoneRequiredPinNarrows(
            $$"""
                oneOf:
                  - '$ref': '#/$defs/Pinned'
                  - properties:
                      kind:
                        type: string
                        .
                      .
                    required:
                      - needs_plain
                    .
                '$defs':
                  Pinned:
                    properties:
                      kind:
                        const: A
                        .
                      .
                    required:
                      - kind
                      - needs_pinned
            """.trimIndent()
        )
    }

    @Test
    fun testOneOfLonePinRequiredThroughAllOfMemberRefNarrows() {
        assertLoneRequiredPinNarrows(
            $$"""
                oneOf:
                  - allOf:
                      - '$ref': '#/$defs/RequiresKind'
                        .
                    properties:
                      kind:
                        const: A
                        .
                      .
                    required:
                      - needs_pinned
                      =

                  - properties:
                      kind:
                        type: string
                        .
                      .
                    required:
                      - needs_plain
                    .
                '$defs':
                  RequiresKind:
                    required:
                      - kind
            """.trimIndent()
        )
    }

    private fun assertLoneRequiredPinNarrows(union: String) {
        val eliminated = assertKsonSchemaErrors(
            """kind: "B"""", union, listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )
        assertEquals("Missing required properties: needs_plain", eliminated[0].message.toString())

        val selected = assertKsonSchemaErrors(
            """kind: "A"""", union, listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )
        assertEquals("Missing required properties: needs_pinned", selected[0].message.toString())
    }
}

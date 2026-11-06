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

class AnyOfValidatorTest : JsonSchemaTest {
    /**
     * `description` is known by both branches, so presence can't narrow; its error is common to both,
     * so it is reported alone.
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
     * Pin selection narrows `anyOf` even when the unpinned branch also survives elimination.
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
                        "kind": { "type": "string" },
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

        assertEquals("Missing required properties: alpha", errors[0].message.toString())
    }

    /**
     * The narrowed report is anchored to the `anyOf` no-match message, not `oneOf`'s.
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

        val report = errors[1].message.toString()
        assertContains(report, "BranchA")
        assertContains(report, "BranchB")
        assertFalse(report.contains("BranchC"))
    }

    @Test
    fun testAnyOfPartialModeNarrowsOnDiscriminatorsLikeFull() {
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

        assertKsonSchemaErrors(
            document,
            closedUnion,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED)
        )

        assertKsonSchemaErrors(
            document,
            closedUnion,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }

    @Test
    fun testAnyOfLoneOptionalPinEliminatesOnlyInPartialMode() {
        val union = """
            {
              "anyOf": [
                { "title": "Pinned", "properties": { "mode": { "const": "fast" } } },
                { "title": "Other", "properties": { "mode": { "type": "number" } } }
              ]
            }
        """.trimIndent()
        val document = """mode: "f""""

        val fullErrors = assertKsonSchemaErrors(
            document,
            union,
            listOf(SCHEMA_ANY_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )
        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- Pinned:\n" +
                "    - Value must be exactly equal to \"fast\" (1:8)\n" +
                "- Other:\n" +
                "    - Property 'mode': Expected one of: number, but got: string (1:8)",
            fullErrors[1].message.toString()
        )

        val partialErrors = assertKsonSchemaErrors(
            document,
            union,
            listOf(SCHEMA_VALUE_TYPE_MISMATCH),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
        assertEquals("Property 'mode': Expected one of: number, but got: string", partialErrors[0].message.toString())
        assertEquals(Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8), partialErrors[0].location)
    }
}

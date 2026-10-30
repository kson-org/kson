package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import kotlin.test.Test
import kotlin.test.assertContains
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

        assertContains(errors[0].message.toString(), "alpha")
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

    /**
     * In [ValidationMode.PARTIAL] mode a half-typed `kind` must not get the enum error; every branch
     * is reported instead.
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

        assertKsonSchemaErrors(
            document,
            closedUnion,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED)
        )

        assertKsonSchemaErrors(
            document,
            closedUnion,
            listOf(SCHEMA_ANY_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }
}

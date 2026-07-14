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
     * The document carries only the *shared* `description` property (known by both branches, so not
     * distinguishing), so presence-based narrowing declines and the full-dump machinery runs — and since
     * `description` is wrong for every branch, its error is extracted as the single precise message.
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
                // note that since `description` is wrong for ALL the sub-schemas, we get a precise error for it.
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
     * The discriminated-union reporting is shared with `oneOf`: an `anyOf` whose branches are keyed by
     * distinct `const`s selects the branch the document's discriminator picks.  `kind: "A"` selects
     * branch A, surfacing only its deeper `params` failure (missing `alpha`) — proving the shared
     * [reportUnionMatchFailure] helper is wired into `anyOf`, not just `oneOf`.
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
                        "kind": { "const": "B" },
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
     * A multi-branch presence match under `anyOf` narrows the dump to the branches keyed by the present
     * `shared` property (A and B), dropping branch C, and anchors it to the `anyOf`-specific no-match
     * message — confirming the fallback threads the caller's own message through the shared helper.
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

        val dump = errors[1].message.toString()
        assertContains(dump, "BranchA")
        assertContains(dump, "BranchB")
        assertFalse(dump.contains("BranchC"))
    }

    /**
     * The PARTIAL-mode guard: a *closed* discriminated `anyOf` whose discriminator matches no branch
     * reports one hard [SCHEMA_ENUM_VALUE_NOT_ALLOWED] in FULL mode, but in [ValidationMode.PARTIAL] a
     * half-typed discriminator shouldn't get that hard narrowing error — the guard skips union narrowing and
     * falls back to the plain per-branch dump ([SCHEMA_ANY_OF_VALIDATION_FAILED] + sub-schema errors).
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
            listOf(SCHEMA_ANY_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }
}

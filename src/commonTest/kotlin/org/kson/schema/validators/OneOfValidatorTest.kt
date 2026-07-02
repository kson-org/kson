package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Generic `oneOf` behaviour with no discriminated union in play: shared-error extraction across
 * branches, the multiple-match case, the plain per-branch nested-bullet dump, and the presence
 * fallback's optional-declared basics.  The discriminated-union suites live in
 * [OneOfDiscriminatedUnionTest] and [OneOfPresenceUnionTest].
 */
class OneOfValidatorTest : JsonSchemaTest {
    /**
     * The document carries only the *shared* `description` property (known by both branches, so not
     * distinguishing), so presence-based narrowing declines and the full-dump machinery runs — and since
     * `description` is wrong for every branch, its error is extracted as the single precise message.
     */
    @Test
    fun testOneOfCommonValidationErrors() {
        assertKsonSchemaErrors(
            """
                description: 99
            """.trimIndent(),
            """
                oneOf:
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
                // since `description` is wrong for ALL the sub-schemas, we get a precise error for it
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )
    }

    /**
     * Presence matches on a branch's *known* properties — those it declares, even optionally — not only
     * those it requires.  Branch one declares `think` optionally (it isn't in any `required` list); the
     * document carries `think`, so it narrows to that branch alone and surfaces its `think` type error,
     * excluding the `required_prop` branch that never mentions `think`.
     */
    @Test
    fun testOneOfPresenceNarrowsOnOptionalDeclaredProperty() {
        val errors = assertKsonSchemaErrors(
            """
                description: "describer"
                think: false
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      description:
                        type: string
                        .
                      think:
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

        // narrowed to the branch that declares `think`; the other branch's `required_prop` is not reported
        assertFalse(errors[0].message.toString().contains("required_prop"))
    }

    @Test
    fun testOneOfMultipleMatches() {
        assertKsonSchemaErrors(
            """
                description: "hello"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      description:
                        type: string

                  - properties:
                      description:
                        type: string
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_MULTIPLE_MATCHES
            )
        )
    }

    /**
     * When branches don't share a single distinct-`const` property there's no discriminator, so we
     * keep the existing full dump of every branch's errors.
     */
    @Test
    fun testOneOfWithoutDiscriminatorDumpsSubSchemaErrors() {
        assertKsonSchemaErrors(
            """
                value: "hello"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    { "properties": { "value": { "type": "number" } } },
                    { "properties": { "value": { "type": "boolean" } } }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }
}

package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Generic `oneOf` behaviour; narrowing has its own suites, [OneOfDiscriminatedUnionTest],
 * [OneOfEliminationTest] and [OneOfPresenceUnionTest].
 */
class OneOfValidatorTest : JsonSchemaTest {
    /**
     * `description` is known by both branches, so presence can't narrow; its error is common to both,
     * so it is reported alone.
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
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )
    }

    /**
     * Presence matches on declared properties, not only required ones: `think` is optional in branch one.
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

        assertFalse(errors[0].message.toString().contains("required_prop"))
    }

    /**
     * [ValidationMode.PARTIAL] mode withholds the multiple-match error: a half-typed document may
     * still match several branches.
     */
    @Test
    fun testOneOfMultipleMatches() {
        val document = """
            description: "hello"
        """.trimIndent()
        val schema = """
            oneOf:
              - properties:
                  description:
                    type: string

              - properties:
                  description:
                    type: string
        """.trimIndent()

        assertKsonSchemaErrors(document, schema, listOf(SCHEMA_ONE_OF_MULTIPLE_MATCHES))
        assertKsonEnforcesSchema(
            document,
            schema,
            shouldAcceptAsValid = true,
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }

    @Test
    fun testOneOfWithoutDiscriminatorReportsSubSchemaErrors() {
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

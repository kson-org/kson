package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test

class OneOfValidatorTest : JsonSchemaTest {
    @Test
    fun testOneOfCommonValidationErrors() {
        assertKsonSchemaErrors(
            """
                description: 99
                thing: false
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

    @Test
    fun testOneOfDiverseValidationErrors() {
        assertKsonSchemaErrors(
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
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                // sub-schema errors are rolled up into this error
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
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
}

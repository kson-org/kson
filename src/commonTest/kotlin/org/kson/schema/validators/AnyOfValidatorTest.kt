package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test

class AnyOfValidatorTest : JsonSchemaTest {
    @Test
    fun testAnyOfValidationErrors() {
        assertKsonSchemaErrors(
            """
                description: 99
                think: false
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
                      thing:
                        type: string
            """.trimIndent(),
            listOf(
                SCHEMA_ANY_OF_VALIDATION_FAILED,
                // note that since `description` is wrong for ALL the sub-schemas, we get a precise error for it
                SCHEMA_VALUE_TYPE_MISMATCH,
                // the other sub-schema errors are rolled up into this error
                SCHEMA_SUB_SCHEMA_ERRORS)
        )
    }
}

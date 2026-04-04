package org.kson.schema.validators

import org.kson.parser.messages.MessageType.SCHEMA_VALUE_TYPE_MISMATCH
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeValidatorTest : JsonSchemaTest {
    @Test
    fun testErrorMessageIncludesPropertyName() {
        val errors =
            assertKsonSchemaErrors(
                """
                name: test
                value: hello
                """.trimIndent(),
                """
                properties:
                  name:
                    type: string
                    .
                  value:
                    type: number
                    .
                  .
                """.trimIndent(),
                listOf(SCHEMA_VALUE_TYPE_MISMATCH),
            )

        assertEquals("Property 'value': Expected one of: number, but got: string", errors[0].message.toString())
    }

    @Test
    fun testErrorMessageWithoutPropertyName() {
        val errors =
            assertKsonSchemaErrors(
                """
                hello
                """.trimIndent(),
                """
                type: number
                """.trimIndent(),
                listOf(SCHEMA_VALUE_TYPE_MISMATCH),
            )

        assertEquals("Expected one of: number, but got: string", errors[0].message.toString())
    }
}

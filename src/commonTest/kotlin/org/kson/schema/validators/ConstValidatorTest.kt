package org.kson.schema.validators

import org.kson.schema.JsonSchemaTest
import org.kson.parser.messages.MessageType.SCHEMA_VALUE_NOT_EQUAL_TO_CONST
import kotlin.test.Test
import kotlin.test.assertEquals

class ConstValidatorTest : JsonSchemaTest {
    @Test
    fun testErrorMessaging() {
        val errors = assertKsonSchemaErrors(
            """
               isConst: "this value is wrong"
            """.trimIndent(),
            """
                properties:
                    isConst:
                        const: "the const value"
            """.trimIndent(),
            listOf(SCHEMA_VALUE_NOT_EQUAL_TO_CONST)
        )

        assertEquals("Value must be exactly equal to \"the const value\"", errors[0].message.toString())
    }
}

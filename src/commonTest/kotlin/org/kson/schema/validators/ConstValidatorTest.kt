package org.kson.schema.validators

import org.kson.schema.JsonSchemaTest
import org.kson.parser.messages.MessageType.SCHEMA_VALUE_NOT_EQUAL_TO_CONST
import kotlin.test.Test
import kotlin.test.assertContains

class ConstValidatorTest : JsonSchemaTest {
    @Test
    fun testErrorMessaging() {
        val constValue = "the const value"
        val errors = assertKsonSchemaErrors(
            """
               isConst: "this value is wrong" 
            """.trimIndent(),
            """
                properties:
                    isConst:
                        const: "$constValue"
            """.trimIndent(),
            listOf(SCHEMA_VALUE_NOT_EQUAL_TO_CONST)
        )

        // ensure the error message refers to the required constant value
        assertContains(errors[0].message.toString(), constValue)
    }
}

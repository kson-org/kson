package org.kson.schema.validators

import org.kson.schema.JsonSchemaTest
import org.kson.parser.messages.MessageType.SCHEMA_ENUM_VALUE_NOT_ALLOWED
import kotlin.test.Test
import kotlin.test.assertEquals

class EnumValidatorTest : JsonSchemaTest {
    @Test
    fun testErrorMessageIncludesAllowedStringValues() {
        val errors = assertKsonSchemaErrors(
            """
                status: unknown
            """,
            """
                properties:
                  status:
                    enum:
                      - active
                      - inactive
                      - pending
                      .
                    .
                  .
            """,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED)
        )

        assertEquals("Value must be one of: \"active\", \"inactive\", \"pending\"", errors[0].message.toString())
    }

    @Test
    fun testErrorMessageIncludesMixedTypeValues() {
        val errors = assertKsonSchemaErrors(
            """
                {
                    "value": "other"
                }
            """,
            """
                {
                    "properties": {
                        "value": {
                            "enum": ["yes", "no", true, false, 0, null]
                        }
                    }
                }
            """,
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED)
        )

        assertEquals("Value must be one of: \"yes\", \"no\", true, false, 0, null", errors[0].message.toString())
    }

    @Test
    fun testValidEnumValueProducesNoError() {
        assertKsonEnforcesSchema(
            """
                status: active
            """,
            """
                properties:
                  status:
                    enum:
                      - active
                      - inactive
                      .
                    .
                  .
            """,
            true
        )
    }
}

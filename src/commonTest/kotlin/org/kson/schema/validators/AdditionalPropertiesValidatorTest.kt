package org.kson.schema.validators

import org.kson.schema.JsonSchemaTest
import org.kson.parser.messages.MessageType.SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED
import kotlin.test.Test
import kotlin.test.assertEquals

class AdditionalPropertiesValidatorTest : JsonSchemaTest {
    @Test
    fun testErrorMessageIncludesPropertyName() {
        val errors = assertKsonSchemaErrors(
            """
                {
                    "foo": 1,
                    "unknownProp": 2
                }
            """,
            """
                {
                    "properties": {
                        "foo": {}
                    },
                    "additionalProperties": false
                }
            """,
            listOf(SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED)
        )

        assertEquals("Additional property 'unknownProp' is not allowed", errors[0].message.toString())
    }

    @Test
    fun testErrorMessageIncludesSchemaTitle() {
        val errors = assertKsonSchemaErrors(
            """
                {
                    "foo": 1,
                    "unknownProp": 2
                }
            """,
            """
                {
                    "title": "TaskModel",
                    "properties": {
                        "foo": {}
                    },
                    "additionalProperties": false
                }
            """,
            listOf(SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED)
        )

        assertEquals("Additional property 'unknownProp' is not allowed in TaskModel", errors[0].message.toString())
    }
}

package org.kson.schema.validators

import org.kson.schema.JsonSchemaTest
import org.kson.parser.messages.MessageType.SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED
import org.kson.parser.messages.MessageType.SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH
import org.kson.parser.messages.MessageType.SCHEMA_VALUE_TYPE_MISMATCH
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

    @Test
    fun testSchemaValidatorEmitsContextualError() {
        val errors = assertKsonSchemaErrors(
            """
                metadata:
                  integration: SNOWFLAKE
            """,
            """
                properties:
                  metadata:
                    type: object
                    additionalProperties:
                      title: MetadataModel
                      type: object
                      .
                    .
                  .
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals("Property 'integration' must conform to 'MetadataModel'", errors[0].message.toString())
        assertEquals("Expected one of: object, but got: string", errors[1].message.toString())
    }

    @Test
    fun testSchemaValidatorWithoutTitleUsesDefault() {
        val errors = assertKsonSchemaErrors(
            """
                metadata:
                  integration: SNOWFLAKE
            """,
            """
                properties:
                  metadata:
                    type: object
                    additionalProperties:
                      type: object
                      .
                    .
                  .
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals("Property 'integration' must conform to 'JSON Object Schema'", errors[0].message.toString())
        assertEquals("Expected one of: object, but got: string", errors[1].message.toString())
    }

    @Test
    fun testSchemaValidatorPassesValidProperties() {
        assertKsonEnforcesSchema(
            """
                metadata:
                  integration:
                    name: snowflake
            """,
            """
                properties:
                  metadata:
                    type: object
                    additionalProperties:
                      type: object
                      .
                    .
                  .
            """,
            true
        )
    }
}

package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test

class AllOfValidatorTest : JsonSchemaTest {
    @Test
    fun testAllOfValidationErrors() {
        assertKsonSchemaErrors(
            """
                description: 99
                think: false
            """.trimIndent(),
            """
                allOf:
                  - properties:
                      description:
                        type: string
                        .
                      thing:
                        type: number
                
                  - required: [other_thing] 
                    properties:
                      description:
                        type: string
            """.trimIndent(),
            listOf(
                // description is wrong type according to both schemas
                SCHEMA_VALUE_TYPE_MISMATCH,
                // missing 'other_thing' required by second schema
                SCHEMA_REQUIRED_PROPERTY_MISSING)
        )
    }
}

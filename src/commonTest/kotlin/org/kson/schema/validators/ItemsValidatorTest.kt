package org.kson.schema.validators

import org.kson.schema.JsonSchemaTest
import org.kson.parser.messages.MessageType.SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemsValidatorTest : JsonSchemaTest {
    @Test
    fun testAdditionalItemsErrorIncludesCounts() {
        val errors = assertKsonSchemaErrors(
            """
                {
                    "tuple": [1, 2, 3, 4, 5]
                }
            """,
            """
                {
                    "properties": {
                        "tuple": {
                            "items": [
                                { "type": "number" },
                                { "type": "number" },
                                { "type": "number" }
                            ],
                            "additionalItems": false
                        }
                    }
                }
            """,
            listOf(SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED)
        )

        assertEquals("Expected at most 3 items, but found 5", errors[0].message.toString())
    }

    @Test
    fun testAdditionalItemsAllowedProducesNoError() {
        assertKsonEnforcesSchema(
            """
                {
                    "tuple": [1, 2, 3]
                }
            """,
            """
                {
                    "properties": {
                        "tuple": {
                            "items": [
                                { "type": "number" },
                                { "type": "number" },
                                { "type": "number" }
                            ],
                            "additionalItems": false
                        }
                    }
                }
            """,
            true
        )
    }
}

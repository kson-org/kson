package org.kson.schema

import kotlin.test.Test

class JsonSchemaTestType : JsonSchemaTest {
    @Test
    fun testEnforceConstraintsForMultipleTypes() {

        assertKsonEnforcesSchema(
            """
                "this string is longer than 10 characters"
            """,
            """
                {"type": ["string", "number"], "exclusiveMinimum": 20, "maxLength": 10 }
            """,
            false)

        assertKsonEnforcesSchema(
            """
                10
            """,
            """
                {"type": ["string", "number"], "exclusiveMinimum": 20, "maxLength": 10 }
            """,
            false)
    }
}

package org.kson.schema

import kotlin.test.Test

class JsonSchemaTestArray : JsonSchemaTest {
    @Test
    fun testEnforceConstraintsForMultipleTypes() {

        assertKsonEnforcesSchema(
            """
                [1, "string", true]
            """,
            """
                {"contains": { "type": "boolean" }}
            """,
            true)

        assertKsonEnforcesSchema(
            """
                [1, "string"]
            """,
            """
                {"contains": { "type": "boolean" }}
            """,
            false)
    }
}

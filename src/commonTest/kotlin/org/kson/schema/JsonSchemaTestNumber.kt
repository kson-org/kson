package org.kson.schema

import kotlin.test.Test

class JsonSchemaTestNumber : JsonSchemaTest {
    @Test
    fun testNumberSchemaWithMinExclusive() {

        assertKsonEnforcesSchema(
            """
                10
            """,
            """
                {"type": "number", "exclusiveMinimum": 20}
            """,
            false)
    }
}

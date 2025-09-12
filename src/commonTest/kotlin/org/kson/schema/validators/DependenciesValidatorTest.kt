package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.SCHEMA_MISSING_REQUIRED_DEPENDENCIES
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class DependenciesValidatorTest: JsonSchemaTest {
    @Test
    fun testDependencyErrorReporting() {
        val errorMessages = assertKsonSchemaErrorAtLocation(
            """
                postOfficeBox: '123'
                # streetAddress: '456 Main St'
                postalCode: '12345'
            """.trimIndent(),
            """
                {
                  "type": "object",
                  "properties": {
                    "postOfficeBox": {
                      "type": "string"
                    },
                    "extendedAddress": {
                      "type": "string"
                    }
                  },
                  "dependencies": {
                    "postOfficeBox": ["streetAddress", "postalCode"]
                  }
                }
            """.trimIndent(),
            listOf(SCHEMA_MISSING_REQUIRED_DEPENDENCIES),
            // ensure the error is hanging off the "postOfficeBox" key in the source
            listOf(Location(
                Coordinates(0, 0),
                Coordinates(0, 13),
                0, 13
            ))
        )

        // the error message should mention the properties involved with this dependency error
        assertContains(errorMessages[0].message.toString(), "postOfficeBox")
        assertContains(errorMessages[0].message.toString(), "streetAddress")
    }
}

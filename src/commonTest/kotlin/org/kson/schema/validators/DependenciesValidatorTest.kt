package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains

class DependenciesValidatorTest: JsonSchemaTest {
    @Test
    fun testDependencyArrayErrorReporting() {
        val errorMessages = assertKsonSchemaErrorAtLocation(
            """
                # streetAddress: '456 Main St'
                postalCode: '12345'
                postOfficeBox: '123'
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
                Coordinates(2, 0),
                Coordinates(2, 13),
                51, 64
            ))
        )

        // the error message should mention the properties involved with this dependency error
        assertContains(errorMessages[0].message.toString(), "postOfficeBox")
        assertContains(errorMessages[0].message.toString(), "streetAddress")
    }

    @Test
    fun testDependencySchemaErrorReporting() {
        val errorMessages = assertKsonSchemaErrorAtLocation(
            """
                {
                  # streetAddress: '456 Main St'
                  postalCode: '12345'
                  postOfficeBox: '123'
                }
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
                    "postOfficeBox": {
                      required: ["streetAddress", "postalCode"]
                    }
                  }
                }
            """.trimIndent(),
            listOf(SCHEMA_DEPENDENCIES_SCHEMA_ERROR),
            // ensure the error is hanging off the object which is violating the dependent schema
            listOf(Location(
                Coordinates(0, 0),
                Coordinates(0, 5),
                0, 5
            ))
        )

        // the error message should mention the properties involved with this dependency error
        assertContains(errorMessages[0].message.toString(), "postOfficeBox")
        assertContains(errorMessages[0].message.toString(), "streetAddress")
    }
}

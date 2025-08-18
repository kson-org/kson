package org.kson.schema

import org.kson.CoreCompileConfig
import org.kson.KsonCore
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.messages.MessageType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interface to tie together our Json Schema tests and give a home to our custom assertions for these tests
 */
interface JsonSchemaTest {
    fun assertKsonEnforcesSchema(ksonSource: String,
                                 schemaJson: String,
                                 shouldAcceptAsValid: Boolean,
                                 message: String? = null) {
        val jsonSchema = assertValidSchema(schemaJson)
        val parseResult = KsonCore.parseToAst(
            ksonSource.trimIndent(),
            coreCompileConfig = CoreCompileConfig(schemaJson = jsonSchema)
        )
        // accepted as valid if and only if we parsed without error
        val acceptedAsValid = parseResult.messages.isEmpty()

        assertEquals(
            shouldAcceptAsValid,
            acceptedAsValid,
            message)
    }

    fun assertKsonSchemaErrorAtLocation(
        ksonSource: String,
        schemaJson: String,
        expectedParseMessageTypes: List<MessageType>,
        expectedParseMessageLocation: List<Location>,
        message: String? = null
    ) {
        val jsonSchema = assertValidSchema(schemaJson)
        val parseResult = KsonCore.parseToAst(
            ksonSource.trimIndent(),
            coreCompileConfig = CoreCompileConfig(schemaJson = jsonSchema)
        )
        
        assertTrue(parseResult.messages.isNotEmpty(), "Expected schema validation errors but got none")
        assertEquals(expectedParseMessageLocation, parseResult.messages.map { it.location })
        assertEquals(expectedParseMessageTypes, parseResult.messages.map { it.message.type })
    }

    /**
     * Assertion helper for testing that [source] is successfully parsed by the schema parser
     * (produces non-null jsonSchema) with no error messages
     */
    fun assertValidSchema(source: String): JsonSchema {
        val result = KsonCore.parseSchema(source)

        val jsonSchema = result.jsonSchema
        assertNotNull(jsonSchema, "Should produce a non-null schema when parsing succeeds")
        assertTrue(
            result.messages.isEmpty(), "Should have no error messages when parsing succeeds, got: " +
                    result.messages.joinToString("\n")
        )

        return jsonSchema
    }
}

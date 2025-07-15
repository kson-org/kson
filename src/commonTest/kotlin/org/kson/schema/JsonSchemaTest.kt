package org.kson.schema

import org.kson.CoreCompileConfig
import org.kson.KsonCore
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
        val acceptedAsValid = !parseResult.hasErrors()

        assertEquals(
            shouldAcceptAsValid,
            acceptedAsValid,
            message)
    }

    /**
     * Assertion helper for testing that [source] is successfully parsed by the schema parser
     * (produces non-null jsonSchema) with no error messages
     */
    fun assertValidSchema(source: String): JsonSchema {
        val result = KsonCore.parseSchema(source)

        val jsonSchema = result.jsonSchema
        assertNotNull(jsonSchema, "Should produce a non-null schema when parsing succeeds")
        assertTrue(result.messages.isEmpty(), "Should have no error messages when parsing succeeds")

        return jsonSchema
    }
}

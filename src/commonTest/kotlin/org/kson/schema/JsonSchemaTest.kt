package org.kson.schema

import org.kson.CoreCompileConfig
import org.kson.Kson
import kotlin.test.assertEquals

/**
 * Interface to tie together our Json Schema tests and give a home to our custom assertions for these tests
 */
interface JsonSchemaTest {
    fun assertKsonEnforcesSchema(ksonSource: String,
                                 schemaJson: String,
                                 shouldAcceptAsValid: Boolean,
                                 message: String? = null) {
        val parseResult = Kson.parseToAst(
            ksonSource.trimIndent(),
            coreCompileConfig = CoreCompileConfig(schemaJson = schemaJson.trimIndent())
        )
        // accepted as valid if and only if we parsed without error
        val acceptedAsValid = !parseResult.hasErrors()

        assertEquals(
            shouldAcceptAsValid,
            acceptedAsValid,
            message)
    }
}

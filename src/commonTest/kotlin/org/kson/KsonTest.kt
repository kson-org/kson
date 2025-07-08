package org.kson

import org.kson.CompileTarget.*
import org.kson.ast.KsonRoot
import org.kson.parser.LoggedMessage
import org.kson.testSupport.validateJson
import org.kson.testSupport.validateYaml
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Interface to tie together our tests that exercise and verify [Kson] behavior on valid Kson and give a home to our
 * custom assertions for these tests.  For tests parsing invalid Kson, see [KsonTestError].
 *
 * The tests of type [KsonTest] are split out basically along the lines of the grammar.  Tests that cross-cut concerns
 * may live in [KsonTestGeneralValue].
 */
interface KsonTest {
    /**
     * Holder for compilation settings used in test.  For most tests, these should be good defaults,
     * and custom [CompileSettings] may be constructed as needed
     */
    data class CompileSettings(
        val ksonSettings: CompileTarget.Kson = Kson(),
        val yamlSettings: Yaml = Yaml(),
        val jsonSettings: Json = Json()
    )

    /**
     * Assertion helper for testing that [source] parses without error and produces the AST described by
     * [expectedKsonFromAst] (this often looks like a truism, i.e. `key: val` parses to `key: val`, but it's
     * an easy/quick/clear way to quickly produce platform- and implementation-agnostic tests that ensure
     * AST parsing is correct)
     *
     * @param source is the kson source to parse into a [KsonRoot]
     * @param expectedKsonFromAst the expected [CompileTarget.Kson] compiler output for the parsed [source]
     * @param expectedYaml the expected [CompileTarget.Yaml] compiler output for the parsed [source]
     * @param expectedJson the expected [CompileTarget.Json] compiler output for the parsed [source]
     * @param message optionally pass a custom failure message for this assertion
     * @param compileSettings optionally customize the [CompileSettings] for this test
     */
    fun assertParsesTo(
        source: String,
        expectedKsonFromAst: String,
        expectedYaml: String,
        expectedJson: String,
        message: String? = null,
        compileSettings: CompileSettings = CompileSettings(),
    ) {
        try {
            validateYaml(expectedYaml)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "ERROR: The expected YAML in this test is invalid. Please fix the test's expectations.\n" +
                "YAML parsing error:\n${e.message}", e
            )
        }
        try {
            validateJson(expectedJson)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "ERROR: The expected JSON in this test is invalid. Please fix the test's expectations.\n" +
                        "JSON parsing error:\n${e.message}", e
            )
        }

        val ksonParseResult = Kson.parseToKson(source, compileSettings.ksonSettings)

        assertFalse(
            ksonParseResult.hasErrors(),
            "Should not have parsing errors, got:\n\n" + LoggedMessage.print(ksonParseResult.messages)
        )

        assertEquals(
            expectedKsonFromAst,
            ksonParseResult.kson,
            message
        )

        assertEquals(
            ksonParseResult.kson,
            ksonParseResult.kson,
            "Re-parsing our transpiled Kson must be idempotent"
        )

        // now validate the Yaml produced for this source
        val yamlResult = Kson.parseToYaml(source, compileSettings.yamlSettings)
        assertEquals(
            expectedYaml,
            yamlResult.yaml,
            message
        )

        // now validate the Json produced for this source
        val jsonResult = Kson.parseToJson(source, compileSettings.jsonSettings)
        assertEquals(
            expectedJson,
            jsonResult.json,
            message
        )

    }
}

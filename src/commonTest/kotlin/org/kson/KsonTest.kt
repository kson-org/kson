package org.kson

import org.kson.CompileTarget.*
import org.kson.ast.KsonRoot
import org.kson.parser.LoggedMessage
import org.kson.testSupport.validateJson
import org.kson.testSupport.validateYaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Base class for tests that exercise and verify [Kson] behavior on valid Kson.  For tests parsing invalid Kson,
 * see [KsonTestError] and its subclasses.
 *
 * Subclasses of this test are split out basically along the lines of the grammar.  Tests that cross-cut concerns
 * may live in this root test class.
 */
open class KsonTest {
    /**
     * Holder for compilation settings used in test.  For most tests, these should be good defaults,
     * and custom [CompileSettings] may be constructed as needed
     */
    data class CompileSettings(
        val ksonSettings: CompileTarget.Kson = CompileTarget.Kson(),
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
    protected fun assertParsesTo(
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

    /**
     * A regression test that demonstrates as directly as possible the grammar ambiguity inherent to
     * trying to use one syntax element (the end-dot `.`) for closing both lists and objects.  In
     * a multi-level mixed nest situation, the parser cannot distinguish which order to unnest things
     * in, and so in specific cases like the one demonstrated here, would collapse two different
     * data structures into one Kson representation (which is of course incorrect on some of the input)
     */
    @Test
    fun testParseAmbiguityRegression(){
        val compileSettings = CompileSettings().ksonSettings

        // Note that "test_key" is a sibling of "outer_key"
        val parseTwoOuterKeys = Kson.parseToKson("""
            "outer_key": {
                "inner_key": [
                  1,
                  2,
                  3
                ]
              },
            "test_key": "value"
            """.trimIndent(), compileSettings)

        val doubleParseTwoOuterKeys = Kson.parseToKson(parseTwoOuterKeys.kson!!, compileSettings)

        // Note that "test_key" is a sibling of "inner_key"
        val parseTwoInnerKeys = Kson.parseToKson(
            """
            "outer_key": {
                "inner_key": [
                  1,
                  2,
                  3
                ],
                "test_key": "value"
              }
            """.trimIndent(), compileSettings)

        // under no circumstances should these parse results be the same
        assertNotEquals(doubleParseTwoOuterKeys, parseTwoInnerKeys,
            "should never format two different data structures into identical Kson")
    }

    @Test
    fun testNestedListAndObjectFormatting() {
        assertParsesTo("""
            {
              nested_obj: {
                key: value
              }
              nested_list: [
                1.1,
                2.1
              ]
            }
        """,
            """
           nested_obj:
             key: value
             .
           nested_list:
             - 1.1
             - 2.1
        """.trimIndent(),
            """
           nested_obj:
             key: value
           nested_list:
             - 1.1
             - 2.1
        """.trimIndent(),
            """
            {
              "nested_obj": {
                "key": "value"
              },
              "nested_list": [
                1.1,
                2.1
              ]
            }
        """.trimIndent()
        )
    }

    /**
     * Regression test demonstrating why using the end-dot for both objects and lists is too ambitious:
     * no way around the ambiguity involved in trying to unnest multiple mixed objects and lists
     */
    @Test
    fun testParsingMultiLevelMixedObjectsAndLists(){
        assertParsesTo(
            // the object containing `inner_key` should be unambiguously terminated
            """
            outer_key1:
              inner_key:
                - 1
                - 2
                - 3
              .
            outer_key2: value
            """.trimIndent(),
            """
            outer_key1:
              inner_key:
                - 1
                - 2
                - 3
              .
            outer_key2: value
            """.trimIndent(),
            """
            outer_key1:
              inner_key:
                - 1
                - 2
                - 3
            outer_key2: value
            """.trimIndent(),
            """
            {
              "outer_key1": {
                "inner_key": [
                  1,
                  2,
                  3
                ]
              },
              "outer_key2": "value"
            }
            """.trimIndent(),
        )

        assertParsesTo(
            // the list containing `inner_key: x` should be unambiguously terminated
            """
            - 
              - inner_key: x
              =
            - outer_list_elem
            """.trimIndent(),
            """
            - 
              - inner_key: x
              =
            - outer_list_elem
            """.trimIndent(),
            """
            - 
              - inner_key: x
            - outer_list_elem
            """.trimIndent(),
            """
            [
              [
                {
                  "inner_key": "x"
                }
              ],
              "outer_list_elem"
            ]
            """.trimIndent(),
        )
    }
}

package org.kson

import org.kson.KsonCoreTest.*
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Tests for general/mixed Kson values that don't fit neatly into the other [KsonCoreTest] tests
 */
class KsonCoreTestGeneralValue : KsonCoreTest {
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
        val parseTwoOuterKeys = KsonCore.parseToKson("""
            "outer_key": {
                "inner_key": [
                  1,
                  2,
                  3
                ]
              }
            "test_key": "value"
            """.trimIndent(), compileSettings)

        val doubleParseTwoOuterKeys = KsonCore.parseToKson(parseTwoOuterKeys.kson!!, compileSettings)

        // Note that "test_key" is a sibling of "inner_key"
        val parseTwoInnerKeys = KsonCore.parseToKson(
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

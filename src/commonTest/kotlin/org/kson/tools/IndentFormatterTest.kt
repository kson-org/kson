package org.kson.tools

import kotlin.test.Test
import kotlin.test.assertEquals

class IndentFormatterTest {
    private fun assertFormatting(
        source: String, 
        expected: String, 
        indentType: IndentType = IndentType.Space(2)
    ) {
        assertEquals(
            expected,
            IndentFormatter(indentType).indent(source)
        )
    }

    @Test
    fun testEmptySource() {
        assertFormatting("", "")
        assertFormatting("  ", "")
        assertFormatting("\n\n", "")
        assertFormatting("    \n   \n\n       \n", "")
    }

    @Test
    fun testSimpleObject() {
        assertFormatting(
            """
            {
            key: value
            }
            """.trimIndent(),
            """
            {
              key: value
            }
            """.trimIndent()
        )

        assertFormatting(
            """
                    {
                 key: value
                           }
            """.trimIndent(),
            """
            {
              key: value
            }
            """.trimIndent()
        )
    }

    @Test
    fun testNestedObject() {
        assertFormatting(
            """
            {
            outer: {
            inner: value
            }
            }
            """.trimIndent(),
            """
            {
              outer: {
                inner: value
              }
            }
            """.trimIndent()
        )

        // Test with tabs
        assertFormatting(
            """
            {
            outer: {
            inner: value
            }
            }
            """.trimIndent(),
            """
            {
            ${"\t"}outer: {
            ${"\t"}${"\t"}inner: value
            ${"\t"}}
            }
            """.trimIndent(),
            IndentType.Tab()
        )
    }

    @Test
    fun testDifferentSpaceIndents() {
        assertFormatting(
            """
            {
            key1: value1,
            nested: {
            inner: value2
            }
            }
            """.trimIndent(),
            """
            {
                key1: value1,
                nested: {
                    inner: value2
                }
            }
            """.trimIndent(),
            IndentType.Space(4)
        )
    }

    @Test
    fun testMultipleNestsOnALine() {
        assertFormatting(
            """
            object: { child1: { child2: {
                                        child2Property: 2
                                }
            child1Property: 1
            }
            }
            """.trimIndent(),
            """
            object: { child1: { child2: {
                  child2Property: 2
                }
                child1Property: 1
              }
            }
            """.trimIndent()
        )

        assertFormatting(
            """
            [
                    [
            [
                            3
                  ]]]
            """.trimIndent(),
            """
            [
              [
                [
                  3
            ]]]
            """.trimIndent()
        )

        assertFormatting(
            """
            <<<
            - 3
            >
                    - 2
                        >
                        - 1
                     >
            """.trimIndent(),
            """
            <<<
                  - 3
                >
                - 2
              >
              - 1
            >
            """.trimIndent()
        )
    }

    @Test
    fun testPreservesExistingFormatting() {
        assertFormatting(
            """
            {key:    value,
            nested:   {  a:   b,
            c:    d
            }
            }
            """.trimIndent(),
            """
            {key:    value,
              nested:   {  a:   b,
                c:    d
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun testPreservesLineBreaks() {
        assertFormatting(
            """
            [
            1,2,
            3,
            4
            ]
            """.trimIndent(),
            """
            [
              1,2,
              3,
              4
            ]
            """.trimIndent()
        )
    }

    @Test
    fun testDashList() {
        assertFormatting(
            """
                      - one
              -   two
                     -    three
            """.trimIndent(),
            """
            |  - one
            |  -   two
            |  -    three
            """.trimMargin()
        )
    }

    @Test
    fun testDashListNesting() {
        assertFormatting(
            """
              key:
                - {
            a: b
                            }
            """.trimIndent(),
            """
            key:
              - {
                a: b
              }
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlock() {
        // Embed blocks should preserve their exact internal formatting
        assertFormatting(
            """
            {
            code: %%sql
            SELECT * 
              FROM table
                WHERE x = 1
            %%
            }
            """.trimIndent(),
            """
            {
              code: %%sql
                SELECT * 
                  FROM table
                    WHERE x = 1
                %%
            }
            """.trimIndent()
        )
    }

    @Test
    fun testAlreadyIndentedEmbedBlock() {
        assertFormatting(
            """
            code: %%sql
              SELECT * 
                FROM table
                  WHERE x = 1
              %%
            """.trimIndent(),
            """
              code: %%sql
                SELECT * 
                  FROM table
                    WHERE x = 1
                %%
            """.trimIndent()
        )
    }

    @Test
    fun testPreservesCommentFormatting() {
        assertFormatting(
            """
            # Header comment
            {
            # Property comment
            key: value     # Trailing comment with lots of space
            }
            """.trimIndent(),
            """
            # Header comment
            {
              # Property comment
              key: value     # Trailing comment with lots of space
            }
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlocksAtDifferentNestingLevels() {
        assertFormatting(
            """
            {
            code1: %%sql
            SELECT *
              FROM table
            %%
            nested: {
            code2internalIndent: %%sql
                SELECT *
                  FROM table
            %%
            deeper: {
            code3: %%sql
            SELECT *
                FROM table
            %%
            }
            }
            }
            """.trimIndent(),
            """
            {
              code1: %%sql
                SELECT *
                  FROM table
                %%
              nested: {
                code2internalIndent: %%sql
                      SELECT *
                        FROM table
                  %%
                deeper: {
                  code3: %%sql
                    SELECT *
                        FROM table
                    %%
                }
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithNoIndentation() {
        assertFormatting(
            """
            {
            code: %%sql
            raw1
            raw2
            raw3
            %%
            }
            """.trimIndent(),
            """
            {
              code: %%sql
                raw1
                raw2
                raw3
                %%
            }
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithInconsistentIndentation() {
        assertFormatting(
            """
            {
            code: %%sql
                indented
            not_indented
                  more_indented
            %%
            }
            """.trimIndent(),
            """
            {
              code: %%sql
                    indented
                not_indented
                      more_indented
                %%
            }
            """.trimIndent()
        )
    }

    @Test
    fun testMultipleEmbedBlocksWithDifferentTags() {
        assertFormatting(
            """
            {
            sql: %%sql
            SELECT 1
            %%
            
            pythonWithInternalIndent: %%python
                def foo():
                    return 1
            %%
            
            text: %%
            plain text
              with some
                indentation
            %%
            }
            """.trimIndent(),
            """
            {
              sql: %%sql
                SELECT 1
                %%
              
              pythonWithInternalIndent: %%python
                    def foo():
                        return 1
                %%
              
              text: %%
                plain text
                  with some
                    indentation
                %%
            }
            """.trimIndent()
        )
    }

    @Test
    fun testAngleBracketList() {
        assertFormatting(
            """
            {
            items: <
            - first
            - second
            - third
            >
            }
            """.trimIndent(),
            """
            {
              items: <
                - first
                - second
                - third
              >
            }
            """.trimIndent()
        )
    }

    @Test
    fun testNestedAngleBrackets() {
        assertFormatting(
            """
            <
            - outer1
            <
            - inner1
            - inner2
            >
            - outer2
            <
            - inner3
            - inner4
            >
            >
            """.trimIndent(),
            """
            <
              - outer1
              <
                - inner1
                - inner2
              >
              - outer2
              <
                - inner3
                - inner4
              >
            >
            """.trimIndent()
        )

        assertFormatting(
            """
            - <
            - {
            key:
            - 1
            - 2
            - <
            - [
            - x
            ]
            >
            }
            >
            """.trimIndent(),
            """
            |  - <
            |    - {
            |      key:
            |        - 1
            |        - 2
            |        - <
            |          - [
            |              - x
            |          ]
            |        >
            |    }
            |  >
            """.trimMargin()
        )
    }

    @Test
    fun testMixedBracketTypes() {
        assertFormatting(
            """
              mixed: <
              - [1,2]
              - {x:y}
              - <
              - nested
              >
              >
            """.trimIndent(),
            """
              mixed: <
                - [1,2]
                - {x:y}
                - <
                  - nested
                >
              >
            """.trimIndent()
        )

        assertFormatting(
            """
            {
            arrays: [
            - first
            - second
            ],
            angles: <
            - third
            - fourth
            >,
            mixed: <
            - [1,2]
            - {x:y}
            - <
              - nested
              >
            >
            }
            """.trimIndent(),
            """
            {
              arrays: [
                  - first
                  - second
              ],
              angles: <
                - third
                - fourth
              >,
              mixed: <
                - [1,2]
                - {x:y}
                - <
                  - nested
                >
              >
            }
            """.trimIndent()
        )
    }

    @Test
    fun testClosingDelimiterAfterContent() {
        assertFormatting(
            """
            [{
            key: value },
            {nested: {
            inner: value }}]
            """.trimIndent(),
            """
            [{
                key: value },
              {nested: {
                  inner: value }}]
            """.trimIndent()
        )
    }

    @Test
    fun testEmptyNextLine() {
        assertFormatting(
            "key: {\n" +
                    "",
            "key: {\n" +
                    "  "
        )
    }

    @Test
    fun testNonDefaultIndentSize() {
        val indentSize = 4
        assertFormatting(
            """
            {
            key1: value1,
            key2: {
            key2_1:
            - x
            - y
            key2_2: {
            key2_2_1: value2_2_1
            }
            },
            key3: [
            value3_1,
            value3_2
            ]
            }
            """.trimIndent(),
            """
            {
                key1: value1,
                key2: {
                    key2_1:
                        - x
                        - y
                    key2_2: {
                        key2_2_1: value2_2_1
                    }
                },
                key3: [
                    value3_1,
                    value3_2
                ]
            }
            """.trimIndent(),
            IndentType.Space(indentSize)
        )
    }

    /**
     * Sanity check tab indents: the code paths are all shared with space indents, so we don't need to over-test
     * the tab case
     */
    @Test
    fun testTabIndentation() {
        assertFormatting(
            """
            {
            key: value
            }
            """.trimIndent(),
            """
            {
            ${"\t"}key: value
            }
            """.trimIndent(),
            IndentType.Tab()
        )

        assertFormatting(
            """
            {
            outer: {
            inner: value
            }
            }
            """.trimIndent(),
            """
            {
            ${"\t"}outer: {
            ${"\t"}${"\t"}inner: value
            ${"\t"}}
            }
            """.trimIndent(),
            IndentType.Tab()
        )
    }

    @Test
    fun testTabIndentWithEmbedBlock() {
        assertFormatting(
            """
            {
            code: %%sql
            SELECT * 
              FROM table
                WHERE x = 1
            %%
            }
            """.trimIndent(),
            """
            {
            ${"\t"}code: %%sql
            ${"\t"}${"\t"}SELECT * 
            ${"\t"}${"\t"}  FROM table
            ${"\t"}${"\t"}    WHERE x = 1
            ${"\t"}${"\t"}%%
            }
            """.trimIndent(),
            IndentType.Tab()
        )
    }

    @Test
    fun testTabIndentWithMixedStructures() {
        assertFormatting(
            """
            {
            list: <
            - item1
            - {
            nested: value
            }
            - [
            1,
            2
            ]
            >
            }
            """.trimIndent(),
            """
            {
            ${"\t"}list: <
            ${"\t"}${"\t"}- item1
            ${"\t"}${"\t"}- {
            ${"\t"}${"\t"}${"\t"}nested: value
            ${"\t"}${"\t"}}
            ${"\t"}${"\t"}- [
            ${"\t"}${"\t"}${"\t"}1,
            ${"\t"}${"\t"}${"\t"}2
            ${"\t"}${"\t"}]
            ${"\t"}>
            }
            """.trimIndent(),
            IndentType.Tab()
        )
    }
}

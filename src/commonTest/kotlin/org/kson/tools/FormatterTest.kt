package org.kson.tools

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatterTest {
    private fun assertFormatting(
        source: String,
        expected: String,
        indentType: IndentType = IndentType.Space(2),
        formattingStyle: FormattingStyle = FormattingStyle.PLAIN,
        roundTrip: Boolean = true
    ) {
        val formattedKson = format(source, KsonFormatterConfig(indentType, formattingStyle))
        assertEquals(
            expected,
            formattedKson
        )

        // Roundtrip: each format should preserve the semantics of a kson document
        /**
         * TODO There are two test cases that currently can't roundtrip. After we handled these corner cases we should
         * roundtrip for every test.
        **/
        var roundtripKson = formattedKson
        if(roundTrip){
            FormattingStyle.entries.filter { it != formattingStyle }.forEach { intermediateStyle ->
                roundtripKson = format(roundtripKson, KsonFormatterConfig(indentType, intermediateStyle))
            }
        }

        assertEquals(
            expected,
            format(roundtripKson, KsonFormatterConfig(indentType, formattingStyle)),
            "Formatting with same style should be idempotent"
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
            key: value
            """.trimIndent()
        )

        assertFormatting(
            """
                    {
                 key: value
                           }
            """.trimIndent(),
            """
            key: value
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
            outer:
              inner: value
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
            outer:
            ${"\t"}inner: value
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
            key1: value1
            nested:
                inner: value2
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
            object:
              child1:
                child2:
                  child2Property: 2
                  .
                child1Property: 1
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
            - 
              - 
                - 3
            """.trimIndent()
        )

        assertFormatting(
            """
            < - < - <
            - 3
            >
                    - 2
                        >
                        - 1
                     >
            """.trimIndent(),
            """
            - 
              - 
                - 3
                =
              - 2
              =
            - 1
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
            - one
            - two
            - three
            """.trimIndent()
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
              - a: b
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlock() {
        // Embed blocks should preserve their exact internal formatting
        assertFormatting(
            """
            {
            code: %sql
            SELECT * 
              FROM table
                WHERE x = 1
            %%
            }
            """.trimIndent(),
            """
            code: %sql
              SELECT * 
                FROM table
                  WHERE x = 1
              %%
            """.trimIndent()
        )
    }

    /**
     * Regression test for an issue where our formatter was re-inserted embed blocks with escapes
     * processed, resulting in illegal Kson
     */
    @Test
    fun testEmbedBlockWithEscapes() {
        assertFormatting(
            """
                %
                This embed block %\% has escapes that should be respected when formatting %\\\%
                %%
            """.trimIndent(),
            """
                $
                This embed block %% has escapes that should be respected when formatting %\\%
                $$
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithPartialEmbedDelim() {
        assertFormatting(
            """
            code: %sql
              SELECT * 
                FROM table
                  WHERE x = 1
              %%
            """.trimIndent(),
            """
            code: %sql
              SELECT * 
                FROM table
                  WHERE x = 1
              %%
            """.trimIndent()
        )

        assertFormatting(
            """
            code: ${'$'}sql
              SELECT * 
                FROM table
                  WHERE x = 1
              ${'$'}${'$'}
            """.trimIndent(),
            """
            code: %sql
              SELECT * 
                FROM table
                  WHERE x = 1
              %%
            """.trimIndent()
        )
    }

    @Test
    fun testAlreadyIndentedEmbedBlock() {
        assertFormatting(
            """
            code: %sql
              SELECT * 
                FROM table
                  WHERE x = 1
              %%
            """.trimIndent(),
            """
            code: %sql
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
            # Property comment
            # Trailing comment with lots of space
            key: value
            """.trimIndent()
        )
    }

    @Test
    fun testCommentAfterKeywordWithHangingValue() {
        assertFormatting(
            """
            key:
            # a value
            value

              # a property
              key2: value
            """.trimIndent(),
            """
            # a value
            key: value
            # a property
            key2: value
            """.trimIndent()
        )

        assertFormatting(
            """
            key:
              - 1 # a value
              - 2
            """.trimIndent(),
            """
            key:
              # a value
              - 1
              - 2
            """.trimIndent()
        )

        assertFormatting(
            """
        key:
                    - 1 
      # a value
                  - 2
            """.trimIndent(),
            """
            key:
              - 1
              # a value
              - 2
            """.trimIndent()
        )

        assertFormatting(
            """
            key:
                 # a value
                 # a value
                 # a value
                 value
              
                      # a property
              # a property
                   # a property
                 key2: value
            """.trimIndent(),
            """
            # a value
            # a value
            # a value
            key: value
            # a property
            # a property
            # a property
            key2: value
            """.trimIndent()
        )
    }

    @Test
    fun testCommentsWithNewlines() {
        assertFormatting(
            """
            x: {
            # comment
            
            # comment
            y: 12
            }
            """.trimIndent(),
            """
            x:
              # comment
              # comment
              y: 12
            """.trimIndent())
    }

    @Test
    fun testCommentsOnNestedObjects() {
        assertFormatting(
            """
            key:
            # a value
            # a value
            value
              
            key2: {
            nested:
            # a value
            value
                
            # a property
            # a property
            nested2:
            value
            }
            """.trimIndent(),
            """
            # a value
            # a value
            key: value
            key2:
              # a value
              nested: value
              # a property
              # a property
              nested2: value
            """.trimIndent()
        )

    }
    
    @Test
    fun testEmbedBlocksAtDifferentNestingLevels() {
        assertFormatting(
            """
            {
            code1: %sql
            SELECT *
              FROM table
            %%
            nested: {
            code2internalIndent: %sql
                SELECT *
                  FROM table
            %%
            deeper: {
            code3: %sql
            SELECT *
                FROM table
            %%
            }
            }
            }
            """.trimIndent(),
            """
            code1: %sql
              SELECT *
                FROM table
              %%
            nested:
              code2internalIndent: %sql
                    SELECT *
                      FROM table
                %%
              deeper:
                code3: %sql
                  SELECT *
                      FROM table
                  %%
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithNoIndentation() {
        assertFormatting(
            """
            {
            code: %sql
            raw1
            raw2
            raw3
            %%
            }
            """.trimIndent(),
            """
            code: %sql
              raw1
              raw2
              raw3
              %%
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithInconsistentIndentation() {
        assertFormatting(
            """
            {
            code: %sql
                indented
            not_indented
                  more_indented
            %%
            }
            """.trimIndent(),
            """
            code: %sql
                  indented
              not_indented
                    more_indented
              %%
            """.trimIndent()
        )
    }

    @Test
    fun testMultipleEmbedBlocksWithDifferentTags() {
        assertFormatting(
            """
            {
            sql: %sql
            SELECT 1
            %%
            
            pythonWithInternalIndent: %%python
                def foo():
                    return 1
            %%
            
            text: %
            plain text
              with some
                indentation
            %%
            }
            """.trimIndent(),
            """
            sql: %sql
              SELECT 1
              %%
            pythonWithInternalIndent: %%python
                  def foo():
                      return 1
              %%
            text: %
              plain text
                with some
                  indentation
              %%
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
            items:
              - first
              - second
              - third
            """.trimIndent()
        )
    }

    @Test
    fun testNestedAngleBrackets() {
        assertFormatting(
            """
            <
            - outer1
            - <
            - inner1
            - inner2
            >
            - outer2
            - <
            - inner3
            - inner4
            >
            >
            """.trimIndent(),
            """
            - outer1
            - 
              - inner1
              - inner2
              =
            - outer2
            - 
              - inner3
              - inner4
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
            - y
            }
            >
            """.trimIndent(),
            """
            - 
              - key:
                  - 1
                  - 2
                  - 
                    - 
                      - 
                        - x
                        =
                      =
                    =
                  - y
            """.trimIndent()
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
              mixed:
                - 
                  - 1
                  - 2
                  =
                - x: y
                - 
                  - nested
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
            arrays:
              - 
                - first
                - second
            angles:
              - third
              - fourth
            mixed:
              - 
                - 1
                - 2
                =
              - x: y
              - 
                - nested
            """.trimIndent()
        )
    }

    @Test
    fun testFormattingMixedUnnesting(){
        assertFormatting(
            """
            outer_key1: {
              inner_key: [1, 2, 3]
            }
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
            """.trimIndent()
        )

        assertFormatting(
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
            """
            - 
              - inner_key: x
              =
            - outer_list_elem
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
            - key: value
            - nested:
                inner: value
            """.trimIndent()
        )
    }

    @Test
    fun testEmptyNextLine() {
        assertFormatting(
            "key: {\n",
            """
            key: {
            """.trimIndent(),
            roundTrip = false
        )
    }

    @Test
    fun testNonDefaultIndentSize() {
        assertFormatting(
            """
            {
            key1: value1,
            nested: {
            key2_1: [
            x,
            y
            ]
            key2_2: {
            key2_2_1: value2_2_1
            }
            }
            key3: [
            value3_1,
            value3_2
            ]
            }
            """.trimIndent(),
            """
            key1: value1
            nested:
                key2_1:
                    - x
                    - y
                key2_2:
                    key2_2_1: value2_2_1
                    .
                .
            key3:
                - value3_1
                - value3_2
            """.trimIndent(),
            IndentType.Space(4)
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
            key: 
                subkey: value
            """.trimIndent(),
            """
            key:
            ${"\t"}subkey: value
            """.trimIndent(),
            IndentType.Tab()
        )

        assertFormatting(
            """
            {
            outer: {
            inner: { very_inner: value }
            }
            }
            """.trimIndent(),
            """
            outer:
            ${"\t"}inner:
            ${"\t"}${"\t"}very_inner: value
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
            code: %%sql
            ${"\t"}SELECT * 
            ${"\t"}  FROM table
            ${"\t"}    WHERE x = 1
            ${"\t"}%%
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
            list:
            ${"\t"}- item1
            ${"\t"}- nested: value
            ${"\t"}- 
            ${"\t"}${"\t"}- 1
            ${"\t"}${"\t"}- 2
            """.trimIndent(),
            IndentType.Tab()
        )
    }

    @Test
    fun testKeyNewlineIndentation() {
        assertFormatting(
            """
            {
            key1:
            {
            key2:
            {
            key3: 
            value
            }
            }
            }
            """.trimIndent(),
            """
            key1:
              key2:
                key3: value
            """.trimIndent()
        )
    }

    @Test
    fun testNestedKeyNewlineIndentation() {
        assertFormatting(
            """
            {
            outer: {
            inner1:
            [1, 2]
            inner2:
            {
            deepKey:
            "deep value"
            }
            }
            }
            """.trimIndent(),
            """
            outer:
              inner1:
                - 1
                - 2
              inner2:
                deepKey: 'deep value'
            """.trimIndent()
        )
    }

    @Test
    fun testKeyNewlineWithEmbedBlock() {
        assertFormatting(
            """
            {
            code:
            %%sql
            SELECT *
              FROM table
            %%
            }
            """.trimIndent(),
            """
            code: %%sql
              SELECT *
                FROM table
              %%
            """.trimIndent()
        )
    }

    @Test
    fun testGetCurrentLineIndentLevel() {
        val formatter = IndentFormatter(IndentType.Space(2))

        assertEquals(1, formatter.getCurrentLineIndentLevel("{"),
            "should indent one level after opening brace")
        assertEquals(2, formatter.getCurrentLineIndentLevel("  {"),
            "should indent one level after opening brace with existing indent")

        assertEquals(1, formatter.getCurrentLineIndentLevel("key:"),
            "should indent one level after colon")
        assertEquals(2, formatter.getCurrentLineIndentLevel("  key:"),
            "should indent one level after colon with existing indent")

        assertEquals(1, formatter.getCurrentLineIndentLevel("<"),
            "should indent one level after opening angle bracket")
        assertEquals(2, formatter.getCurrentLineIndentLevel("  <"),
            "should indent one level after opening angle bracket with existing indent")

        assertEquals(1, formatter.getCurrentLineIndentLevel("  - item"),
            "should maintain indent after dash in list")

        assertEquals(1, formatter.getCurrentLineIndentLevel("  }"),
            "should match closing brace indent")
        assertEquals(2, formatter.getCurrentLineIndentLevel("    }"),
            "should match closing brace indent with higher existing indent")

        assertEquals(1, formatter.getCurrentLineIndentLevel("  >"),
            "should match closing angle bracket indent")
        assertEquals(2, formatter.getCurrentLineIndentLevel("    >"),
            "should match closing angle bracket indent with higher existing indent")

        assertEquals(1, formatter.getCurrentLineIndentLevel("  some text"),
            "should maintain indent for normal lines")
        assertEquals(2, formatter.getCurrentLineIndentLevel("    other text"),
            "should maintain indent for normal lines with higher existing indent")
    }

    @Test
    fun testEmptyLineIndentLevel() {
        val formatter = IndentFormatter(IndentType.Space(2))

        assertEquals(1, formatter.getCurrentLineIndentLevel("  "),
            "should maintain previous indent for empty lines")
        assertEquals(2, formatter.getCurrentLineIndentLevel("    "),
            "should maintain previous indent for empty lines with higher existing indent")
    }

    @Test
    fun testGetCurrentLineIndentLevelWithTabs() {
        val formatter = IndentFormatter(IndentType.Tab())

        assertEquals(1, formatter.getCurrentLineIndentLevel("{"),
            "should indent one level after opening brace with tab indentation")
        assertEquals(2, formatter.getCurrentLineIndentLevel("\t{"),
            "should indent one level after opening brace with existing tab indent")

        assertEquals(1, formatter.getCurrentLineIndentLevel("key:"),
            "should indent one level after colon with tab indentation")
        assertEquals(2, formatter.getCurrentLineIndentLevel("\tkey:"),
            "should indent one level after colon with existing tab indent")

        assertEquals(1, formatter.getCurrentLineIndentLevel("\tsome text"),
            "should maintain indent for normal lines with tab indentation")
        assertEquals(2, formatter.getCurrentLineIndentLevel("\t\tother text"),
            "should maintain indent for normal lines with higher existing tab indent")
    }

    @Test
    fun testGetCurrentLineIndentLevelWithEmbedBlocks() {
        val formatter = IndentFormatter(IndentType.Space(2))

        assertEquals(0, formatter.getCurrentLineIndentLevel("%%sql"),
            "should not change indent for embed block start")
        assertEquals(1, formatter.getCurrentLineIndentLevel("  %%sql"),
            "should not change indent for embed block start with existing indent")

        assertEquals(1, formatter.getCurrentLineIndentLevel("  SELECT *"),
            "should maintain indent inside embed block")

        assertEquals(1, formatter.getCurrentLineIndentLevel("  %%"),
            "should decrease indent after embed block end")
        assertEquals(2, formatter.getCurrentLineIndentLevel("    %%"),
            "should decrease indent after embed block end with higher existing indent")
    }

    @Test
    fun testGetCurrentLineIndentLevelWithMixedStructures() {
        val formatter = IndentFormatter(IndentType.Space(2))

        assertEquals(1, formatter.getCurrentLineIndentLevel("{"),
            "should properly indent for nested structures")
        assertEquals(2, formatter.getCurrentLineIndentLevel("  nested: {"),
            "should properly indent for nested object properties")
        assertEquals(3, formatter.getCurrentLineIndentLevel("    items: <"),
            "should properly indent for nested angle brackets")
        assertEquals(3, formatter.getCurrentLineIndentLevel("    - {"),
            "should properly indent for nested list items")

        assertEquals(3, formatter.getCurrentLineIndentLevel("      }"),
            "should properly indent after closing nested delimiters")
        assertEquals(2, formatter.getCurrentLineIndentLevel("    >"),
            "should properly indent after closing angle brackets")
        assertEquals(1, formatter.getCurrentLineIndentLevel("  }"),
            "should properly indent after closing outer brace")
    }

    @Test
    fun testGetCurrentLineIndentLevelWithMultipleChanges() {
        val formatter = IndentFormatter(IndentType.Space(2))

        assertEquals(2, formatter.getCurrentLineIndentLevel("{ {"),
            "should increase indent by more than 1 for multiple opening delimiters on one line")
        assertEquals(3, formatter.getCurrentLineIndentLevel("{ { {"),
            "should increase indent by multiple levels for three opening delimiters")
        assertEquals(2, formatter.getCurrentLineIndentLevel("key: {nested: {"),
            "should increase indent by more than 1 for nested properties")

        assertEquals(1, formatter.getCurrentLineIndentLevel("  } }"),
            "should have no effect on the next line for multiple closing delimiters as they unindent their own line")
        assertEquals(2, formatter.getCurrentLineIndentLevel("    } } }"),
            "should maintain existing indent levels for multiple closing delimiters")
    }

    @Test
    fun testGetCurrentLineIndentLevelWithDeferredClosures() {
        val formatter = IndentFormatter(IndentType.Space(2))

        assertEquals(0, formatter.getCurrentLineIndentLevel("key:val }}}"),
            "should trigger an unindent on the next line, not their own line, for non-leading close delimiters")
        assertEquals(4, formatter.getCurrentLineIndentLevel("              key:val }}}"),
            "should maintain existing indent for trailing delimiters with leading spaces")
    }

    @Test
    fun testGetCurrentLineIndentLevelWithNestedClosures() {
        val formatter = IndentFormatter(IndentType.Space(2))

        assertEquals(2, formatter.getCurrentLineIndentLevel("    }}"),
            "should handle multiple nested structures closing at once")
        assertEquals(3, formatter.getCurrentLineIndentLevel("      }}}"),
            "should handle three nested structures closing at once")

        assertEquals(2, formatter.getCurrentLineIndentLevel("    }>"),
            "should work with mixed bracket types")
        assertEquals(3, formatter.getCurrentLineIndentLevel("      ]}>"),
            "should work with multiple mixed bracket types")
    }

    @Test
    fun testNestedPlainObjects() {
        assertFormatting(
            """
                key:
                nested_key: 10
                another_nest_key: 3 .
                unnested_key: 44
            """.trimIndent(),
            """
              key:
                nested_key: 10
                another_nest_key: 3
                .
              unnested_key: 44
            """.trimIndent()
        )
    }

    @Test
    fun testNestedDashLists() {
        assertFormatting(
            """
                - 
                - "sub-list elem 1"
                - "sub-list elem 2" =
                - "outer list elem 1"
            """.trimIndent(),
            """
              - 
                - 'sub-list elem 1'
                - 'sub-list elem 2'
                =
              - 'outer list elem 1'
            """.trimIndent()
        )
    }

    @Test
    fun testPlainObjectsInPlainLists() {
        assertFormatting(
            """
                - key11: 11
                key12: 12
                - key21: 22
                key22: 22
                - key31: 31
                key32: 32
            """.trimIndent(),
            """
            - key11: 11
              key12: 12
            
            - key21: 22
              key22: 22
            
            - key31: 31
              key32: 32
            """.trimIndent()
        )
    }

    @Test
    fun testInvalidKsonWithTrailingComments() {
        assertFormatting(
            """
            # error: list with no value
            -

            # trailing comment
        """.trimIndent(),
            """
            # error: list with no value
            -

            # trailing comment
        """.trimIndent(),
            roundTrip = false
        )
    }

    /**
     * If a Kson document is fairly well-formed but has some unexpected trialing content, we should still
     * be able to format the valid portion of the document
     */
    @Test
    fun testFormattingSucceedsWithInvalidTrailingContent() {
        assertFormatting(
            """
                {outer:{inner: value}}
                "Illegal trailing content"
            """.trimIndent(),
            """
                outer:
                  inner: value

                "Illegal trailing content"
            """.trimIndent()
        )

        assertFormatting(
            """
                key: value trailingContent
                list: [1,2,3] extraTrailingContent
            """.trimIndent(),
            """
                key: value
                
                trailingContent
          
                
                list:
                  - 1
                  - 2
                  - 3
                
                extraTrailingContent
            """.trimIndent()
        )

        assertFormatting(
            """
                key: value extraValue list:[1,2,3]
            """.trimIndent(),
            """
                key: value
                
                extraValue 
          
                list:
                  - 1
                  - 2
                  - 3
            """.trimIndent()
        )
    }

    @Test
    fun testDelimitedFormattingStyleObjects() {
        assertFormatting(
            """
                key: value
            """.trimIndent(),
            """
                {
                  key: value
                }
            """.trimIndent(),
            formattingStyle = FormattingStyle.DELIMITED
        )

        assertFormatting(
            """
                key: 
                  nested: value
                  .
                unnested: value
            """.trimIndent(),
            """
                {
                  key: {
                    nested: value
                  }
                  unnested: value
                }
            """.trimIndent(),
            formattingStyle = FormattingStyle.DELIMITED
        )
    }

    @Test
    fun testDelimitedFormattingStyleLists(){
        assertFormatting(
            """
                list: [1,2,3]
            """.trimIndent(),
            """
                {
                  list: <
                    - 1
                    - 2
                    - 3
                  >
                }
            """.trimIndent(),
            formattingStyle = FormattingStyle.DELIMITED
        )

        assertFormatting(
            """
                [1,2,3]
            """.trimIndent(),
            """
               <
                 - 1
                 - 2
                 - 3
               >
            """.trimIndent(),
            formattingStyle = FormattingStyle.DELIMITED
        )

        assertFormatting(
            """
                list: 
                  - 1
                  - 2
                  - key: value
                  - 
                     - "new list"
                     =
                  - test
            """.trimIndent(),
            """
                {
                  list: <
                    - 1
                    - 2
                    - {
                        key: value
                      }
                    - <
                        - 'new list'
                      >
                    - test
                  >
                }
            """.trimIndent(),
            formattingStyle = FormattingStyle.DELIMITED
        )
    }

    @Test
    fun testDelimitedFormattingStyleEmbed() {
        assertFormatting(
            """
                $
                content
                $$
            """.trimIndent(),
            """
                %
                content
                %%
                """.trimIndent(),
            formattingStyle = FormattingStyle.DELIMITED
        )

        assertFormatting(
            """
                embedBlock: $
                content
                $$
            """.trimIndent(),
            """
                {
                  embedBlock: %
                    content
                    %%
                }
            """.trimIndent(),
            formattingStyle = FormattingStyle.DELIMITED
        )
    }

    @Test
    fun testCompactFormattingStyleSimpleObject() {
        assertFormatting(
            """
                "type": "object",
                "properties": {
                x: w
                }
            """.trimIndent(),
            """
                type:object properties:x:w
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                {
                  key: value
                  key: value
                }
            """.trimIndent(),
            """
                key:value key:value
                """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                {
                  key: "quoted value"
                  key: value
                }
            """.trimIndent(),
            """
                key:'quoted value'key:value
                """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                key: 1
                another: key
            """.trimIndent(),
            """
                key:1 another:key
                """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )
    }

    @Test
    fun testCompactFormattingStyleList() {
        assertFormatting(
            """
                list: <
                 - 1
                 - 2
                 - 3
                 >
            """.trimIndent(),
            """
                list:[1 2 3]
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )
    }

    @Test
    fun testCompactFormattingStyleNestedStructures() {
        assertFormatting(
            """
                list:
                 - 1
                 - 2
                 -
                   - 3
                   - 4
                   =
                 key: value

            """.trimIndent(),
            """
                list:[1 2 [3 4]]key:value
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                list:
                 - 1
                 - 2
                 -
                   - 3
                   - 4
                   =
                 key:
                   nestedkey: value
                   .
                 anotherkey: value

            """.trimIndent(),
            """
                list:[1 2 [3 4]]key:nestedkey:value.anotherkey:value
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                - key: value
                - key: value
            """.trimIndent(),
            """
                [{key:value}key:value]
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                - key: true
                - key: value
            """.trimIndent(),
            """
                [{key:true}key:value]
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                - key: 3
                - key: value
            """.trimIndent(),
            """
                [{key:3}key:value]
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
            nested:
                - [key:value]
                - key: value
            """.trimIndent(),
            """
                nested:[[key:value]key:value]
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                - key11:
                   key12: value1
                - key21: value2
            """.trimIndent(),
            """
                [{key11:key12:value1}key21:value2]
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                - key11:
                   key12: 
                    key13: value1
                - key21: value2
            """.trimIndent(),
            """
                [{key11:key12:key13:value1}key21:value2]
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )
    }

    @Test
    fun testCompactFormattingStyleWithComments() {
        assertFormatting(
            """
                # comment
                key: value
                key: value
            """.trimIndent(),
            """
                # comment
                key:value key:value
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                key: value
                # comment
                key: value
            """.trimIndent(),
            """
                key:value
                # comment
                key:value
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )

        assertFormatting(
            """
                list: [1,2,3]
                # comment
            """.trimIndent(),
            """
                list:[1 2 3]
                # comment
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )
    }

    @Test
    fun testCompactFormattingStyleEmbedBlock() {
        assertFormatting(
            """
                embed: ${'$'}tag
                  content
                  $$
                key: value  
            """.trimIndent(),
            """
                embed:%tag
                content
                %%key:value
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )
    }

    @Test
    fun testCompactFormattingStyleReservedKeywords() {
        assertFormatting(
            """
                key: true
                key: false
                key: null
            """.trimIndent(),
            """
                key:true key:false key:null
            """.trimIndent(),
            formattingStyle = FormattingStyle.COMPACT
        )
    }
}

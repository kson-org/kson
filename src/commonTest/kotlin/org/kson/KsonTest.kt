package org.kson

import org.kson.ast.AstNode
import org.kson.ast.KsonRoot
import org.kson.collections.ImmutableList
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KsonTest {
    /**
     * Assertion helper for testing that [source] parses without error and produces the AST described by
     * [expectedSourceFromAst] (this often looks like a truism, ie. `key: val` parses to `key: val`, but it's
     * an easy/quick/clear way to quickly produce platform- and implementation-agnostic tests that ensure
     * AST parsing is correct)
     *
     * @param source is the kson source to parse into a [KsonRoot]
     * @param expectedSourceFromAst the expected [KsonRoot.toKsonSourceInternal] output for the parsed [source]
     * @param message optionally pass a custom failure message for this assertion
     */
    private fun assertParsesTo(
        source: String,
        expectedSourceFromAst: String,
        message: String? = null
    ) {
        val parseResult = Kson.parse(source)

        assertFalse(
            parseResult.hasErrors(),
            "Should not have parsing errors, got:\n\n" + LoggedMessage.print(parseResult.messages)
        )
        assertEquals(
            expectedSourceFromAst,
            parseResult.ast?.toKsonSource(AstNode.Indent()),
            message
        )
    }

    /**
     * Assertion helper for testing that [source] is rejected by the parser with the messages listed in
     * [expectedParseMessageTypes]
     *
     * @param source is the kson source to parse into a [KsonRoot]
     * @param expectedParseMessageTypes a list of [MessageType]s produced by parsing [source]
     * @param maxNestingLevel the maximum allowable nested lists and objects to configure the parser to accept
     * @return the produced messages for further validation
     */
    private fun assertParserRejectsSource(
        source: String,
        expectedParseMessageTypes: List<MessageType>,
        maxNestingLevel: Int? = null
    ): ImmutableList<LoggedMessage> {
        val parseResult = if (maxNestingLevel != null) {
            Kson.parse(source, maxNestingLevel)
        } else {
            Kson.parse(source)
        }

        assertEquals(
            expectedParseMessageTypes,
            parseResult.messages.map { it.message.type },
            "Should have the expected parse errors."
        )

        assertTrue(
            parseResult.hasErrors(),
            "Should set the hasErrors flag appropriate when there are errors"
        )
        assertEquals(
            null,
            parseResult.ast,
            "Should produce a null AST when there are errors"
        )

        return parseResult.messages
    }

    @Test
    fun testBlankKsonSource() {
        assertParserRejectsSource("", listOf(BLANK_SOURCE))
        assertParserRejectsSource("  ", listOf(BLANK_SOURCE))
        assertParserRejectsSource("\t", listOf(BLANK_SOURCE))
    }

    @Test
    fun testStringLiteralSource() {
        assertParsesTo(
            """
                "This is a string"
            """,
            "\"This is a string\""
        )
    }

    @Test
    fun testEmptyString() {
        assertParsesTo("''", "\"\"")
    }

    @Test
    fun testBadStringEscape() {
        assertParserRejectsSource("'this has \\x which is an illegal escape'", listOf(STRING_BAD_ESCAPE))
    }

    @Test
    fun testBadUnicodeEscape() {
        assertParserRejectsSource("'\\u12'", listOf(STRING_BAD_UNICODE_ESCAPE))
        assertParserRejectsSource("'\\u12x9'", listOf(STRING_BAD_UNICODE_ESCAPE))
        assertParserRejectsSource("'\\u'", listOf(STRING_BAD_UNICODE_ESCAPE))
        assertParserRejectsSource("'\\u", listOf(STRING_NO_CLOSE, STRING_BAD_UNICODE_ESCAPE))
    }

    @Test
    fun testDanglingEscapes() {
        assertParserRejectsSource("'\\'", listOf(STRING_NO_CLOSE))
        assertParserRejectsSource("'\\", listOf(STRING_NO_CLOSE, STRING_BAD_ESCAPE))
    }

    /**
     * See also [org.kson.parser.NumberParserTest] for more targeted number parsing tests
     */
    @Test
    fun testNumberLiteralSource() {
        assertParsesTo("42.1", "42.1")
        assertParsesTo("42.1E0", "42.1")
        assertParsesTo("42.1e0", "42.1")
        assertParsesTo("4.21E1", "42.1")
        assertParsesTo("421E-1", "42.1")
        assertParsesTo("4210e-2", "42.1")
        assertParsesTo("0.421e2", "42.1")
        assertParsesTo("0.421e+2", "42.1")
        assertParsesTo("42.1E+0", "42.1")
        assertParsesTo("00042.1E0", "42.1")
        assertParsesTo("-42.1", "-42.1")
        assertParsesTo("-42.1E0", "-42.1")
        assertParsesTo("-42.1e0", "-42.1")
        assertParsesTo("-4.21E1", "-42.1")
        assertParsesTo("-421E-1", "-42.1")
        assertParsesTo("-4210e-2", "-42.1")
        assertParsesTo("-0.421e2", "-42.1")
        assertParsesTo("-0.421e+2", "-42.1")
        assertParsesTo("-42.1E+0", "-42.1")
        assertParsesTo("-00042.1E0", "-42.1")
    }

    @Test
    fun testDanglingExponentError() {
        assertParserRejectsSource(
            """
                420E
            """,
            listOf(DANGLING_EXP_INDICATOR)
        )

        assertParserRejectsSource(
            """
                420E-
            """,
            listOf(DANGLING_EXP_INDICATOR)
        )
    }

    @Test
    fun testIllegalCharacterError() {
        assertParserRejectsSource("key: \\value", listOf(ILLEGAL_CHARACTERS))
    }

    @Test
    fun testIllegalMinusSignError() {
        assertParserRejectsSource(
            """
                -nope
            """.trimIndent(),
            listOf(ILLEGAL_MINUS_SIGN)
        )
    }

    @Test
    fun testBooleanLiteralSource() {
        assertParsesTo(
            """
                true
            """,
            "true"
        )

        assertParsesTo(
            """
                false
            """,
            "false"
        )
    }

    @Test
    fun testNilLiteralSource() {
        assertParsesTo(
            """
                null
            """,
            "null"
        )
    }

    @Test
    fun testEmptyListSource() {
        assertParsesTo(
            """
                []
            """,
            "[]"
        )
    }

    @Test
    fun testBracketListSource() {
        assertParsesTo(
            """
                ["a string"]
            """,
            """
                [
                  "a string"
                ]
            """.trimIndent()
        )

        assertParsesTo(
            """
                [42.4, 43.1, 44.7]
            """,
            """
                [
                  42.4,
                  43.1,
                  44.7
                ]
            """.trimIndent()
        )

        assertParsesTo(
            """
                [true, false, null,]
            """,
            """
                [
                  true,
                  false,
                  null
                ]
            """.trimIndent(),
            "should support an optional trailing comma in lists"
        )
    }

    @Test
    fun testDashListSource() {
        assertParsesTo(
            """
                - "a string"
            """,
            """
                [
                  "a string"
                ]
            """.trimIndent()
        )

        assertParsesTo(
            """
                - 42.4
                - 43.1
                - 44.7
            """,
            """
                [
                  42.4,
                  43.1,
                  44.7
                ]
            """.trimIndent()
        )

        // note that the indentation isn't significant in kson dash-delimited lists (unlike yaml)
        assertParsesTo(
            """
                - true
                  - false
                    - null
            """,
            """
                [
                  true,
                  false,
                  null
                ]
            """.trimIndent(),
            "should support an optional trailing comma in lists"
        )
    }

    @Test
    fun testCommaFreeList() {
        assertParsesTo("""
            [
                null true [sublist] 
                - another 
                - sublist
            ]
        """,
        """
            [
              null,
              true,
              [
                sublist
              ],
              [
                another,
                sublist
              ]
            ]
        """.trimIndent())
    }

    @Test
    fun testEmptyCommas() {
        assertParserRejectsSource("[,]", listOf(EMPTY_COMMAS))
        assertParserRejectsSource("{,}", listOf(EMPTY_COMMAS))
        assertParserRejectsSource(",", listOf(EMPTY_COMMAS))

        assertParserRejectsSource("[,,]", listOf(EMPTY_COMMAS, EMPTY_COMMAS))
        assertParserRejectsSource("{,,}", listOf(EMPTY_COMMAS, EMPTY_COMMAS))
        assertParserRejectsSource(",,", listOf(EMPTY_COMMAS, EMPTY_COMMAS))

        assertParserRejectsSource("[1,,3]", listOf(EMPTY_COMMAS))
        assertParserRejectsSource("{one: 1 ,, three: 3}", listOf(EMPTY_COMMAS))
        assertParserRejectsSource("one: 1 ,, three: 3", listOf(EMPTY_COMMAS))

        assertParserRejectsSource("[1,2,3,,,,,,]", listOf(EMPTY_COMMAS))
        assertParserRejectsSource("{one: 1, two: 2, three: 3,,,,,,}", listOf(EMPTY_COMMAS))
        assertParserRejectsSource("one: 1 ,two: 2, three: 3,,,,,,", listOf(EMPTY_COMMAS))

        assertParserRejectsSource("[,,,, x ,, y ,,,,,,, z ,,,,]", listOf(EMPTY_COMMAS, EMPTY_COMMAS, EMPTY_COMMAS, EMPTY_COMMAS))
        assertParserRejectsSource(",,,, x:1 ,, y:2 ,,,,,,, z:3 ,,,,", listOf(EMPTY_COMMAS, EMPTY_COMMAS, EMPTY_COMMAS, EMPTY_COMMAS))
    }

    @Test
    fun testDanglingListDash() {
        assertParserRejectsSource("-", listOf(DANGLING_LIST_DASH))
        assertParserRejectsSource("- ", listOf(DANGLING_LIST_DASH))
        assertParserRejectsSource("""
            - 2
            - 4
            - 
            - 8
        """.trimIndent(), listOf(DANGLING_LIST_DASH))
    }

    @Test
    fun testEmptyObjectSource() {
        assertParsesTo(
            """
                {}
            """,
            "{}"
        )
    }

    @Test
    fun testObjectSource() {
        val expectRootObjectAst = """
            {
              key: val
              "string key": 66.3
              hello: "y'all"
            }
            """.trimIndent()

        assertParsesTo(
            """
                {
                    key: val
                    "string key": 66.3
                    hello: "y'all"
                }
            """,
            expectRootObjectAst,
            "should parse as a root object when optional root parens are provided"
        )

        assertParsesTo(
            """
                key: val
                "string key": 66.3
                hello: "y'all"
            """,
            expectRootObjectAst
        )
    }

    @Test
    fun testObjectSourceWithImmediateTrailingComment() {
        assertParsesTo(
            """
                {a:b}#comment
            """,
            """
                #comment
                {
                  a: b
                }
            """.trimIndent(),
            "should parse as a root object when optional root parens are provided"
        )
    }

    @Test
    fun testObjectSourceOptionalComma() {
        val expectRootObjectAst = """
            {
              key: val
              "string key": 66.3
              hello: "y'all"
            }
            """.trimIndent()

        assertParsesTo(
            """
                {
                    key: val
                    "string key": 66.3,
                    hello: "y'all",
                }
            """,
            expectRootObjectAst,
            "should parse object ignoring optional commas, even trailing"
        )

        assertParsesTo(
            """
                key: val
                "string key": 66.3,
                hello: "y'all"
            """,
            expectRootObjectAst,
            "should parse ignoring optional commas, even in brace-free root objects"
        )
    }

    @Test
    fun testEmbedBlockSource() {
        assertParsesTo(
            """
                %%
                    this is a raw embed
                %%
            """,
            """
                %%
                    this is a raw embed
                %%
            """.trimIndent()
        )

        assertParsesTo(
            """
                %%sql
                    select * from something
                %%
            """,
            """
                %%sql
                    select * from something
                %%
            """.trimIndent()
        )
    }

    @Test
    fun testUnclosedStringError() {
        assertParserRejectsSource("\"unclosed", listOf(STRING_NO_CLOSE))
    }

    @Test
    fun testUnclosedEmbedDelimiterError() {
        assertParserRejectsSource("%%\n", listOf(EMBED_BLOCK_NO_CLOSE))
    }

    @Test
    fun testUnclosedEmbedAlternateDelimiterError() {
        assertParserRejectsSource("$$\n", listOf(EMBED_BLOCK_NO_CLOSE))
    }

    @Test
    fun testUnclosedListError() {
        val errorMessages = assertParserRejectsSource("[", listOf(LIST_NO_CLOSE))
        assertEquals(Location(0, 0, 0, 1, 0, 1), errorMessages[0].location)
        assertParserRejectsSource("[1,2,", listOf(LIST_NO_CLOSE))
    }

    @Test
    fun testUnopenedListError() {
        assertParserRejectsSource("]", listOf(LIST_NO_OPEN))
        assertParserRejectsSource("key: ]", listOf(LIST_NO_OPEN))
    }

    @Test
    fun testInvalidColonInList() {
        assertParserRejectsSource("[key: 1]", listOf(LIST_STRAY_COLON))
    }

    @Test
    fun testInvalidListElementError() {
        assertParserRejectsSource("[} 1]", listOf(OBJECT_NO_OPEN))
    }

    @Test
    fun testKeywordWithoutValue() {
        assertParserRejectsSource("key:", listOf(OBJECT_KEY_NO_VALUE))
        assertParserRejectsSource("key: key_with_val: 10 another_key:", listOf(OBJECT_KEY_NO_VALUE, OBJECT_KEY_NO_VALUE))
        assertParserRejectsSource("[{key:} 1]", listOf(OBJECT_KEY_NO_VALUE))
    }

    @Test
    fun testUnclosedObjectError() {
        assertParserRejectsSource("{", listOf(OBJECT_NO_CLOSE))
        assertParserRejectsSource("{ key: value   ", listOf(OBJECT_NO_CLOSE))
    }

    @Test
    fun testUnopenedObjectError() {
        assertParserRejectsSource("}", listOf(OBJECT_NO_OPEN))
        assertParserRejectsSource("[1, 2, }]", listOf(OBJECT_NO_OPEN))
    }

    @Test
    fun testInvalidTrailingKson() {
        assertParserRejectsSource("[1] illegal_key: illegal_value", listOf(EOF_NOT_REACHED))
        assertParserRejectsSource("{ key: value } 4.5", listOf(EOF_NOT_REACHED))
        assertParserRejectsSource("key: value illegal extra identifiers", listOf(EOF_NOT_REACHED))
    }

    @Test
    fun testTwoConsecutiveStrings() {
        assertParserRejectsSource("'a string''an illegal second string'", listOf(EOF_NOT_REACHED))
    }

    @Test
    fun testStringWithNullByte() {
        assertParserRejectsSource("my_bad_string: 'a a' ", listOf(STRING_CONTROL_CHARACTER))
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
            {
              nested_obj: {
                key: value
              }
              nested_list: [
                1.1,
                2.1
              ]
            }
        """.trimIndent())
    }

    @Test
    fun testSourceWithComment() {
        assertParsesTo("""
            # this is a comment
            "string"
        """,
        """
            # this is a comment
            "string"
        """.trimIndent())
    }

    @Test
    fun testMultipleCommentsOnNestedElement() {
        assertParsesTo(
            """
              [
                # first comment
                # second comment
                # third comment
                one,
                two
              ]
            """,
            """
              [
                # first comment
                # second comment
                # third comment
                one,
                two
              ]
            """.trimIndent())
    }

    @Test
    fun testCommentPreservationOnConstants() {

        assertParsesTo(
            """
                # comment on a number
                4.5
            """,
            """
                # comment on a number
                4.5
            """.trimIndent()
        )

        assertParsesTo(
            """
                # comment on a boolean
                false
            """,
            """
                # comment on a boolean
                false
            """.trimIndent()
        )

        assertParsesTo(
            """
                # comment on an identifier
                id
            """,
            """
                # comment on an identifier
                id
            """.trimIndent()
        )

        assertParsesTo(
            """
                # comment on a string
                "a string"
            """,
            """
                # comment on a string
                "a string"
            """.trimIndent()
        )
    }

    @Test
    fun testTrailingCommentPreservationOnConstants() {

        assertParsesTo(
            """
                4.5 # trailing comment
            """,
            """
                # trailing comment
                4.5
            """.trimIndent()
        )

        assertParsesTo(
            """
                false # trailing comment
            """,
            """
                # trailing comment
                false
            """.trimIndent()
        )

        assertParsesTo(
            """
                id # trailing comment
            """,
            """
                # trailing comment
                id
            """.trimIndent()
        )

        assertParsesTo(
            """
                "a string" # trailing comment
            """,
            """
                # trailing comment
                "a string"
            """.trimIndent()
        )
    }

    @Test
    fun testCommentPreservationOnObjects() {
        assertParsesTo(
            """
                # a comment
                key
                
                :
                    # an odd but legal comment on this val
                    val 
                # another comment
                key2: val2
            """,
            """
               {
                 # a comment
                 # an odd but legal comment on this val
                 key: val
                 # another comment
                 key2: val2
               }
            """.trimIndent()
        )
    }

    @Test
    fun testCommentsPreservationOnCommas() {
        assertParsesTo(
            """
                key1:val1
                # this comment should be preserved on this property
                ,
                key2:val2
                # as should this one
                ,
            """,
            """
                {
                  # this comment should be preserved on this property
                  key1: val1
                  # as should this one
                  key2: val2
                }
            """.trimIndent()
        )
    }

    @Test
    fun testCommentPreservationOnLists() {
        assertParsesTo(
            """
                # comment on list
                [
                # comment on first_element
                first_element,
                # comment on second_element
                second_element ]
            """,
            """
                # comment on list
                [
                  # comment on first_element
                  first_element,
                  # comment on second_element
                  second_element
                ]
            """.trimIndent()
        )

        assertParsesTo(
            """
                # comment on first_element
                - first_element
                # comment on second_element
                - second_element
            """,
            """
                [
                  # comment on first_element
                  first_element,
                  # comment on second_element
                  second_element
                ]
            """.trimIndent(),
            "should always anchor dash-delimited list comments to the elements " +
                    "since there's no syntactic way to identify a comment on the whole list"
        )

        assertParsesTo(
            """
                # a list of lists
                [
                  [1.2, # trailing comment on constant element
                    # a nested list element
                    2.2, 3.2],
                  # a nested dash-delimited list
                  - [10.2]
                  - # a further nested braced list
                    [4.2,
                    # a further nested braced list element
                    5.2] # trailing comment on nested list
                  - [9.2,8.2]
                ]
            """,
            """
              # a list of lists
              [
                [
                  # trailing comment on constant element
                  1.2,
                  # a nested list element
                  2.2,
                  3.2
                ],
                [
                  # a nested dash-delimited list
                  [
                    10.2
                  ],
                  # a further nested braced list
                  # trailing comment on nested list
                  [
                    4.2,
                    # a further nested braced list element
                    5.2
                  ],
                  [
                    9.2,
                    8.2
                  ]
                ]
              ]
            """.trimIndent(),
            "should preserve comments in nested lists"
        )
    }

    @Test
    fun testCommentPreservationOnEmbedBlocks() {
        assertParsesTo(
            """
                # a comment on an embed block
                %%
                embedded stuff
                %%
            """,
            """
               # a comment on an embed block
               %%
               embedded stuff
               %%
            """.trimIndent()
        )
    }

    @Test
    fun testTrailingCommentOnList() {
        assertParsesTo(
            """
                # leading
                [one, # trailing "one"
                two # trailing "two"
                ] # trailing list brace
            """,
            """
                # leading
                # trailing list brace
                [
                  # trailing "one"
                  one,
                  # trailing "two"
                  two
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testTrailingCommentsInObjects() {
        assertParsesTo(
            """
                # leading
                {
                  keyword:value # trailing
                }
            """,
            """
                # leading
                {
                  # trailing
                  keyword: value
                }
            """.trimIndent()
        )

        assertParsesTo(
            """
                {
                # leading
                keyword:value # trailing
                }
            """,
            """
                {
                  # leading
                  # trailing
                  keyword: value
                }
            """.trimIndent()
        )

        assertParsesTo(
            """
                obj_name # trailing name
                {
                  key: value
                }
            """,
            """
                # trailing name
                obj_name {
                  key: value
                }
            """.trimIndent()
        )
    }

    @Test
    fun testDocumentEndComments() {
        assertParsesTo(
            """
                null
                
                
                
                # these are some trailing
                # comments that would like 
                # to be preserved at the end
                # of the file
            """,
            """
                null
                
                # these are some trailing
                # comments that would like 
                # to be preserved at the end
                # of the file
            """.trimIndent()
        )
    }

    @Test
    fun testIllegalObjectNesting() {
        assertParserRejectsSource("""
            {'1':{'2':{'3':{'4':{'5':{'6':{'7':{'8':0}}}}}}}}
        """.trimIndent(), listOf(MAX_NESTING_LEVEL_EXCEEDED), 8)
    }

    @Test
    fun testIllegalListNesting() {
        // test six nested lists with a nesting limit of 5
        assertParserRejectsSource("[[[[[[]]]]]]", listOf(MAX_NESTING_LEVEL_EXCEEDED), 5)

        // 50 open brackets per line plus 7 makes one too many to parse with a limit of 256
        // Note that we don't both collecting unclosed list errors in this case:
        //   we simply bail out of the parse with the important error
        assertParserRejectsSource("""
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[
        """.trimIndent(), listOf(MAX_NESTING_LEVEL_EXCEEDED), 256)

        // same test as above, but with dashed sub-lists sprinkled in
        assertParserRejectsSource("""
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[ - [
            [[[[[[[[[ - 1 - 2 - [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[ - 3 - [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[
        """.trimIndent(), listOf(MAX_NESTING_LEVEL_EXCEEDED), 256)
    }

    @Test
    fun testIllegalMixedListAndObjectNesting() {
        // test nesting a mix of objects, bracket lists, and dashed lists
        assertParserRejectsSource("""
            [
              { 
                 a: - { 
                        b: [ { c: - [] } ]
                      }
              }
            ]
        """.trimIndent(), listOf(MAX_NESTING_LEVEL_EXCEEDED), 7)
    }
}
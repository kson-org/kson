package org.kson

import org.kson.CompileTarget.*
import org.kson.ast.KsonRoot
import org.kson.stdlibx.collections.ImmutableList
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.*
import org.kson.testSupport.validateJson
import org.kson.testSupport.validateYaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KsonTest {
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
     * [expectedKsonFromAst] (this often looks like a truism, ie. `key: val` parses to `key: val`, but it's
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
    private fun assertParsesTo(
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
            Kson.parseToAst(source, CoreCompileConfig(maxNestingLevel = maxNestingLevel))
        } else {
            Kson.parseToAst(source)
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
            "\"This is a string\"",
            "\"This is a string\"",
            "\"This is a string\""
        )
    }

    @Test
    fun testEmptyString() {
        assertParsesTo("''", "\"\"", "\"\"", "\"\"")
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
        assertParsesTo("42", "42", "42", "42")
        assertParsesTo("042", "42", "42", "42")
        assertParsesTo("42.1", "42.1", "42.1", "42.1")
        assertParsesTo("00042.1", "42.1", "42.1", "42.1")
        assertParsesTo("42.1E0", "42.1e0", "42.1e0", "42.1e0")
        assertParsesTo("42.1e0", "42.1e0", "42.1e0", "42.1e0")
        assertParsesTo("4.21E1", "4.21e1", "4.21e1", "4.21e1")
        assertParsesTo("421E-1", "421e-1", "421e-1", "421e-1")
        assertParsesTo("4210e-2", "4210e-2", "4210e-2", "4210e-2")
        assertParsesTo("0.421e2", "0.421e2", "0.421e2", "0.421e2")
        assertParsesTo("0.421e+2", "0.421e+2", "0.421e+2", "0.421e+2")
        assertParsesTo("42.1E+0", "42.1e+0", "42.1e+0", "42.1e+0")
        assertParsesTo("00042.1E0", "42.1e0", "42.1e0", "42.1e0")
        assertParsesTo("-42.1", "-42.1", "-42.1", "-42.1")
        assertParsesTo("-42.1E0", "-42.1e0", "-42.1e0", "-42.1e0")
        assertParsesTo("-42.1e0", "-42.1e0", "-42.1e0", "-42.1e0")
        assertParsesTo("-4.21E1", "-4.21e1", "-4.21e1", "-4.21e1")
        assertParsesTo("-421E-1", "-421e-1", "-421e-1", "-421e-1")
        assertParsesTo("-4210e-2", "-4210e-2", "-4210e-2", "-4210e-2")
        assertParsesTo("-0.421e2", "-0.421e2", "-0.421e2", "-0.421e2")
        assertParsesTo("-0.421e+2", "-0.421e+2", "-0.421e+2", "-0.421e+2")
        assertParsesTo("-42.1E+0", "-42.1e+0", "-42.1e+0", "-42.1e+0")
        assertParsesTo("-00042.1E0", "-42.1e0", "-42.1e0", "-42.1e0")
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
            "true",
            "true",
            "true"
        )

        assertParsesTo(
            """
                false
            """,
            "false",
            "false",
            "false"
        )
    }

    @Test
    fun testNilLiteralSource() {
        assertParsesTo(
            """
                null
            """,
            "null",
            "null",
            "null"
        )
    }

    @Test
    fun testEmptyListSource() {
        assertParsesTo(
            """
                []
            """,
            "[]",
            "[]",
            "[]"
        )
    }

    @Test
    fun testSquareBracketListSource() {
        assertParsesTo(
            """
                ["a string"]
            """,
            """
                [
                  "a string"
                ]
            """.trimIndent(),
            """
                - "a string"
            """.trimIndent(),
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
            """.trimIndent(),
            """
                - 42.4
                - 43.1
                - 44.7
            """.trimIndent(),
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
            """
                - true
                - false
                - null
            """.trimIndent(),
            """
                [
                  true,
                  false,
                  null
                ]
            """.trimIndent(),
            "should support an optional trailing comma in lists"
        )

        assertParsesTo(
            """
                [true, false, [1.2, 3.4, 5.6]]
            """,
            """
                [
                  true,
                  false,
                  [
                    1.2,
                    3.4,
                    5.6
                  ]
                ]
            """.trimIndent(),
            """
                - true
                - false
                - 
                  - 1.2
                  - 3.4
                  - 5.6
            """.trimIndent(),
            """
                [
                  true,
                  false,
                  [
                    1.2,
                    3.4,
                    5.6
                  ]
                ]
            """.trimIndent()
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
            """.trimIndent(),
            """
                - "a string"
            """.trimIndent(),
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
            """.trimIndent(),
            """
                - 42.4
                - 43.1
                - 44.7
            """.trimIndent(),
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
            """
                - true
                - false
                - null
            """.trimIndent(),
            """
                [
                  true,
                  false,
                  null
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testDelimitedDashList() {
        assertParsesTo("""
                <>
            """.trimIndent(),
            "[]",
            "[]",
            "[]"
        )

        assertParsesTo("""
                < - a - b - c >
            """.trimIndent(),
            """
                [
                  a,
                  b,
                  c
                ]
            """.trimIndent(),
            """
                - a
                - b
                - c
            """.trimIndent(),
            """
                [
                  "a",
                  "b",
                  "c"
                ]
            """.trimIndent()
        )

        assertParsesTo("""
                < 
                  - a 
                  - b 
                  - c 
                >
            """.trimIndent(),
            """
                [
                  a,
                  b,
                  c
                ]
            """.trimIndent(),
            """
                - a
                - b
                - c
            """.trimIndent(),
            """
                [
                  "a",
                  "b",
                  "c"
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testUnclosedDelimitedDashList() {
        assertParserRejectsSource("<", listOf(LIST_NO_CLOSE))
    }

    @Test
    fun testDashListNestedWithCommaList() {
        assertParsesTo("""
            [- []]
        """.trimIndent(),
            """
               [
                 [
                   []
                 ]
               ]
            """.trimIndent(),
            """
               - 
                 - 
                   []
            """.trimIndent(),
            """
               [
                 [
                   []
                 ]
               ]
            """.trimIndent()
        )
    }

    @Test
    fun testDashListNestedWithObject() {
        assertParsesTo("""
            - { 
                nestedDashList: - a
                                - b
                                - c
              }
        """.trimIndent(),
            """
                [
                  {
                    nestedDashList: [
                      a,
                      b,
                      c
                    ]
                  }
                ]
            """.trimIndent(),
            """
                - nestedDashList:
                  - a
                  - b
                  - c
            """.trimIndent(),
            """
                [
                  {
                    "nestedDashList": [
                      "a",
                      "b",
                      "c"
                    ]
                  }
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testDashListNestedWithDashList() {
        assertParsesTo("""
            - <
                - a
                - b
                - <
                    - a1
                    - b1
                    - c1
                  >
                - c
              >
        """.trimIndent(),
            """
                [
                  [
                    a,
                    b,
                    [
                      a1,
                      b1,
                      c1
                    ],
                    c
                  ]
                ]
            """.trimIndent(),
            """
                - 
                  - a
                  - b
                  - 
                    - a1
                    - b1
                    - c1
                  - c
            """.trimIndent(),
            """
                [
                  [
                    "a",
                    "b",
                    [
                      "a1",
                      "b1",
                      "c1"
                    ],
                    "c"
                  ]
                ]
            """.trimIndent()
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
        """.trimIndent(),
            """
            - null
            - true
            - 
              - sublist
            - 
              - another
              - sublist
        """.trimIndent(),
            """
            [
              null,
              true,
              [
                "sublist"
              ],
              [
                "another",
                "sublist"
              ]
            ]
        """.trimIndent()
        )
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
        """.trimIndent(), listOf(DANGLING_LIST_DASH))
    }

    @Test
    fun testEmptyObjectSource() {
        assertParsesTo(
            """
                {}
            """,
            "{}",
            "{}",
            "{}"
        )
    }

    @Test
    fun testObjectSource() {
        val expectKsonRootObjectAst = """
            {
              key: val
              "string key": 66.3
              hello: "y'all"
            }
            """.trimIndent()

        val expectedYamlRootObject = """
            key: val
            "string key": 66.3
            hello: "y'all"
        """.trimIndent()

        val expectedJsonRootObject = """
            {
              "key": "val",
              "string key": 66.3,
              "hello": "y'all"
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
            expectKsonRootObjectAst,
            expectedYamlRootObject,
            expectedJsonRootObject,
            "should parse as a root object when optional root parens are provided"
        )

        assertParsesTo(
            """
                key: val
                "string key": 66.3
                hello: "y'all"
            """,
            expectKsonRootObjectAst,
            expectedYamlRootObject,
            expectedJsonRootObject
        )
    }

    @Test
    fun testObjectSourceMixedWithStringContainingRawNewlines() {
        assertParsesTo(
            """
                first: "value"
                second: "this is a string with a
                raw newline in it and at its end
                "
            """.trimIndent(),
            """
                {
                  first: "value"
                  second: "this is a string with a
                raw newline in it and at its end
                "
                }
            """.trimIndent(),
            """
                first: "value"
                second: "this is a string with a
                raw newline in it and at its end
                "
            """.trimIndent(),
            """
                {
                  "first": "value",
                  "second": "this is a string with a\nraw newline in it and at its end\n"
                }
            """.trimIndent()
        )
    }

    @Test
    fun testObjectSourceWithInvalidInternals() {
        assertParserRejectsSource("""
            {
                key: value
                [1,2,3]
                key2: value2
                "not a property"
                key4: value4
                key5: value5
                test:
        """.trimIndent(), listOf(OBJECT_NO_CLOSE, OBJECT_BAD_INTERNALS, OBJECT_BAD_INTERNALS, OBJECT_KEY_NO_VALUE))
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
            """
                #comment
                a: b
            """.trimIndent(),
            """
                {
                  "a": "b"
                }
            """.trimIndent(),
            "should parse as a root object when optional root parens are provided"
        )
    }

    @Test
    fun testObjectSourceOptionalComma() {
        val expectKsonForRootObjectAst = """
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
            expectKsonForRootObjectAst,
            """
               key: val
               "string key": 66.3
               hello: "y'all"
            """.trimIndent(),
            """
               {
                 "key": "val",
                 "string key": 66.3,
                 "hello": "y'all"
               }
            """.trimIndent(),
            "should parse object ignoring optional commas, even trailing"
        )

        assertParsesTo(
            """
                key: val
                "string key": 66.3,
                hello: "y'all"
            """,
            expectKsonForRootObjectAst,
            """
               key: val
               "string key": 66.3
               hello: "y'all"
            """.trimIndent(),
            """
               {
                 "key": "val",
                 "string key": 66.3,
                 "hello": "y'all"
               }
            """.trimIndent(),
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
            """.trimIndent(),
            """
               |2
                     this is a raw embed
                 
            """.trimIndent(),
            """
                "    this is a raw embed\n"
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
            """.trimIndent(),
            """
                |2
                      select * from something
                  
            """.trimIndent(),
            """
                "    select * from something\n"
            """.trimIndent()
        )
    }

    /**
     * Regression test for a parsing problem we had at the boundary:
     * this was blowing up with an index out of bounds rather than
     * parsing with an [EMBED_BLOCK_NO_CLOSE] error as it should
     */
    @Test
    fun testUnclosedEmbedWithEscape() {
        assertParserRejectsSource(
            """
                %%
                %\%
            """.trimIndent(),
            listOf(EMBED_BLOCK_NO_CLOSE)
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
    fun testEmbedBlockPartialDelim() {
        assertParserRejectsSource(
            """
                %
            """,
            listOf(EMBED_BLOCK_NO_CLOSE, EMBED_BLOCK_DANGLING_DELIM)
        )

        assertParserRejectsSource(
            """
                $
            """,
            listOf(EMBED_BLOCK_NO_CLOSE, EMBED_BLOCK_DANGLING_DELIM)
        )

        assertParserRejectsSource(
            """
                test: %myTag
                    Some embedBlockContent
                %%
            """,
            listOf(EMBED_BLOCK_DANGLING_DELIM)
        )
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
        assertParserRejectsSource("[key: 1, :]", listOf(LIST_INVALID_ELEM))
    }

    @Test
    fun testInvalidListElementError() {
        assertParserRejectsSource("[} 1]", listOf(OBJECT_NO_OPEN))
    }

    @Test
    fun testKeywordWithoutValue() {
        assertParserRejectsSource("key:", listOf(OBJECT_KEY_NO_VALUE))
        assertParserRejectsSource("key: key_with_val: 10 another_key:", listOf(OBJECT_KEY_NO_VALUE))
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
    fun testUnopenedDashListError() {
        assertParserRejectsSource(">", listOf(LIST_NO_OPEN))
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

    @Test
    fun testSourceWithComment() {
        assertParsesTo("""
            # this is a comment
            "string"
        """,
        """
            # this is a comment
            "string"
        """.trimIndent(),
        """
            # this is a comment
            "string"
        """.trimIndent(),
            """
            "string"
        """.trimIndent()
        )
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
            """.trimIndent(),
            """
              # first comment
              # second comment
              # third comment
              - one
              - two
            """.trimIndent(),
            """
              [
                "one",
                "two"
              ]
            """.trimIndent()
        )
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
            """.trimIndent(),
            """
                # comment on a number
                4.5
            """.trimIndent(),
            "4.5"
        )

        assertParsesTo(
            """
                # comment on a boolean
                false
            """,
            """
                # comment on a boolean
                false
            """.trimIndent(),
            """
                # comment on a boolean
                false
            """.trimIndent(),
            "false"
        )

        assertParsesTo(
            """
                # comment on an identifier
                id
            """,
            """
                # comment on an identifier
                id
            """.trimIndent(),
            """
                # comment on an identifier
                id
            """.trimIndent(),
            "\"id\""
        )

        assertParsesTo(
            """
                # comment on a string
                "a string"
            """,
            """
                # comment on a string
                "a string"
            """.trimIndent(),
            """
                # comment on a string
                "a string"
            """.trimIndent(),
            "\"a string\""
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
            """.trimIndent(),
            """
                # trailing comment
                4.5
            """.trimIndent(),
            "4.5"
        )

        assertParsesTo(
            """
                false # trailing comment
            """,
            """
                # trailing comment
                false
            """.trimIndent(),
            """
                # trailing comment
                false
            """.trimIndent(),
            "false"
        )

        assertParsesTo(
            """
                id # trailing comment
            """,
            """
                # trailing comment
                id
            """.trimIndent(),
            """
                # trailing comment
                id
            """.trimIndent(),
            "\"id\""
        )

        assertParsesTo(
            """
                "a string" # trailing comment
            """,
            """
                # trailing comment
                "a string"
            """.trimIndent(),
            """
                # trailing comment
                "a string"
            """.trimIndent(),
            "\"a string\""
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
            """.trimIndent(),
            """
               # a comment
               # an odd but legal comment on this val
               key: val
               # another comment
               key2: val2
            """.trimIndent(),
            """
               {
                 "key": "val",
                 "key2": "val2"
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
            """.trimIndent(),
            """
                # this comment should be preserved on this property
                key1: val1
                # as should this one
                key2: val2
            """.trimIndent(),
            """
                {
                  "key1": "val1",
                  "key2": "val2"
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
            """.trimIndent(),
            """
                # comment on list
                # comment on first_element
                - first_element
                # comment on second_element
                - second_element
            """.trimIndent(),
            """
                [
                  "first_element",
                  "second_element"
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
            """
                # comment on first_element
                - first_element
                # comment on second_element
                - second_element
            """.trimIndent(),
            """
                [
                  "first_element",
                  "second_element"
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
            """
            # a list of lists
            - 
              # trailing comment on constant element
              - 1.2
              # a nested list element
              - 2.2
              - 3.2
            - 
              # a nested dash-delimited list
              - 
                - 10.2
              # a further nested braced list
              # trailing comment on nested list
              - 
                - 4.2
                # a further nested braced list element
                - 5.2
              - 
                - 9.2
                - 8.2
            """.trimIndent(),
            """
                [
                  [
                    1.2,
                    2.2,
                    3.2
                  ],
                  [
                    [
                      10.2
                    ],
                    [
                      4.2,
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
            """.trimIndent(),
            """
               # a comment on an embed block
               |
                 embedded stuff
                 
            """.trimIndent(),
            """
              "embedded stuff\n"
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
            """.trimIndent(),
            """
                # leading
                # trailing list brace
                # trailing "one"
                - one
                # trailing "two"
                - two
            """.trimIndent(),
            """
                [
                  "one",
                  "two"
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
            """.trimIndent(),
            """
                # leading
                # trailing
                keyword: value
            """.trimIndent(),
            """
                {
                  "keyword": "value"
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
            """.trimIndent(),
            """
                # leading
                # trailing
                keyword: value
            """.trimIndent(),
            """
                {
                  "keyword": "value"
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
            """.trimIndent(),
            """
                null
                
                # these are some trailing
                # comments that would like 
                # to be preserved at the end
                # of the file
            """.trimIndent(),
            "null"
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

        // same test as above, but with dashed sub-lists sprinkled in
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

    @Test
    fun testEmbedBlockWithNoLeadingWhitespace() {
        assertParsesTo(
            """
                %%
                first line
                second line
                third line
                %%
            """.trimIndent(),
            """
                %%
                first line
                second line
                third line
                %%
            """.trimIndent(),
            """
                |
                  first line
                  second line
                  third line
                  
            """.trimIndent(),
            """
                "first line\nsecond line\nthird line\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithConsistentLeadingWhitespace() {
        assertParsesTo(
            """
                %%
                    indented line one
                    indented line two
                    indented line three
                %%
            """.trimIndent(),
            """
                %%
                    indented line one
                    indented line two
                    indented line three
                %%
            """.trimIndent(),
            """
                |2
                      indented line one
                      indented line two
                      indented line three
                  
            """.trimIndent(),
            """
                "    indented line one\n    indented line two\n    indented line three\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithVaryingLeadingWhitespace() {
        assertParsesTo(
            """
                %%
                no indent
                    four spaces
                        eight spaces
                  two spaces
                %%
            """.trimIndent(),
            """
                %%
                no indent
                    four spaces
                        eight spaces
                  two spaces
                %%
            """.trimIndent(),
            """
                |
                  no indent
                      four spaces
                          eight spaces
                    two spaces
                  
            """.trimIndent(),
            """
                "no indent\n    four spaces\n        eight spaces\n  two spaces\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithEmptyLines() {
        assertParsesTo(
            """
                %%
                    indented line
                
                    another indented line
                    
                    final indented line
                %%
            """.trimIndent(),
            """
                %%
                    indented line
                
                    another indented line
                    
                    final indented line
                %%
            """.trimIndent(),
            """
                |2
                      indented line
                  
                      another indented line
                      
                      final indented line
                  
            """.trimIndent(),
            """
                "    indented line\n\n    another indented line\n    \n    final indented line\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWhitespacePreservation() {
        // all this whitespace must be preserved faithfully, including the leading indent
        assertParsesTo(
            "%%\n" +
            "  \n" +
            "     \n" +
            "        \n" +
            "%%",
            "%%\n" +
            "  \n" +
            "     \n" +
            "        \n" +
            "%%",
            "|2\n" +
            "    \n" +
            "       \n" +
            "          \n" +
            "  ",
            "\"  \\n     \\n        \\n\""
        )
    }

    @Test
    fun testEmbedBlockWithTabsAndSpaces() {
        assertParsesTo(
            """
                %%
                	tabbed line
                    spaced line
                  mixed line
                %%
            """.trimIndent(),
            """
                %%
                	tabbed line
                    spaced line
                  mixed line
                %%
            """.trimIndent(),
            """
                |2
                  	tabbed line
                      spaced line
                    mixed line
                  
            """.trimIndent(),
            """
                "\ttabbed line\n    spaced line\n  mixed line\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithRetainedTags() {
        assertParsesTo(
            """
                %%sql
                select * from my_table
                %%
            """.trimIndent(),
            """
                %%sql
                select * from my_table
                %%
            """.trimIndent(),
            """
                embedTag: "sql"
                embedContent: |
                  select * from my_table
                  
            """.trimIndent(),
            """
                {
                  "embedTag": "sql",
                  "embedContent": "select * from my_table\n"
                }
            """.trimIndent(),
            compileSettings = CompileSettings(
                yamlSettings = Yaml(retainEmbedTags = true),
                jsonSettings = Json(retainEmbedTags = true)
            )
        )

        assertParsesTo(
            """
                %%python
                def hello():
                    print("Hello world!")
                    
                hello()
                %%
            """.trimIndent(),
            """
                %%python
                def hello():
                    print("Hello world!")
                    
                hello()
                %%
            """.trimIndent(),
            """
                embedTag: "python"
                embedContent: |
                  def hello():
                      print("Hello world!")
                      
                  hello()
                  
            """.trimIndent(),
            """
                {
                  "embedTag": "python",
                  "embedContent": "def hello():\n    print(\"Hello world!\")\n    \nhello()\n"
                }
            """.trimIndent(),
            compileSettings = CompileSettings(
                yamlSettings = Yaml(retainEmbedTags = true),
                jsonSettings = Json(retainEmbedTags = true)
            )
        )

        assertParsesTo(
            """
                %%
                This is an embed block
                with no tag
                %%
            """.trimIndent(),
            """
                %%
                This is an embed block
                with no tag
                %%
            """.trimIndent(),
            // we render empty tags too because this metadata distinguishes between an empty embed block and
            // a multi-line string
            """
                embedTag: ""
                embedContent: |
                  This is an embed block
                  with no tag
                  
            """.trimIndent(),
            """
                {
                  "embedTag": "",
                  "embedContent": "This is an embed block\nwith no tag\n"
                }
            """.trimIndent(),

            compileSettings = CompileSettings(
                yamlSettings = Yaml(retainEmbedTags = true),
                jsonSettings = Json(retainEmbedTags = true)
            )
        )
    }

    @Test
    fun testSanityCheckCommentFreeCompiles() {
        assertParsesTo("""
            # a comment
            key: {
              # nested comment
              value: 42
            }
            # document end comment
        """.trimIndent(),
        """
            {
              key: {
                value: 42
              }
            }
        """.trimIndent(),
        """
            key:
              value: 42
        """.trimIndent(),
            """
            {
              "key": {
                "value": 42
              }
            }
        """.trimIndent(),
            compileSettings = CompileSettings(
                Kson(preserveComments = false),
                Yaml(preserveComments = false),
            )
        )
    }

    @Test
    fun testEmbedBlockWithAlternativeDelimiter() {
        assertParsesTo(
            """
                $$
                    this is a raw embed with alternative delimiter
                $$
            """.trimIndent(),
            // note that we prefer the primary %% delimiter in our transpiler output
            """
                %%
                    this is a raw embed with alternative delimiter
                %%
            """.trimIndent(),
            """
                |2
                      this is a raw embed with alternative delimiter
                  
            """.trimIndent(),
            """
                "    this is a raw embed with alternative delimiter\n"
            """.trimIndent()
        )

        assertParsesTo(
            """
                $$${"sql"}
                    select * from something
                $$
            """.trimIndent(),
            """
                %%sql
                    select * from something
                %%
            """.trimIndent(),
            """
                |2
                      select * from something
                  
            """.trimIndent(),
            """
                "    select * from something\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithEscapes() {
        assertParsesTo(
            """
            %%
            this is an escaped delim %\%
            whereas in this case, this is not $\$
            %%
            """.trimIndent(),
            """
            %%
            this is an escaped delim %\%
            whereas in this case, this is not $\$
            %%
            """.trimIndent(),
            """
            |
              this is an escaped delim %%
              whereas in this case, this is not $\$
              
            """.trimIndent(),
            """
            "this is an escaped delim %%\nwhereas in this case, this is not $\\$\n"
            """.trimIndent()
        )

        assertParsesTo(
            """
            %%
            more %\% %\% %\% than $$ should yield a $$-delimited block
            %%
            """.trimIndent(),
            """
            $$
            more %% %% %% than $\$ should yield a $\$-delimited block
            $$
            """.trimIndent(),
            """
            |
              more %% %% %% than $$ should yield a $$-delimited block
              
            """.trimIndent(),
            """
            "more %% %% %% than $$ should yield a $$-delimited block\n"
            """.trimIndent()
        )
    }

    @Test
    fun testEmbedBlockWithAlternativeDelimiterAndEscapes() {
        assertParsesTo(
            """
            $$
            these double $\$ dollars are %%%% embedded but escaped
            $$
            """.trimIndent(),
            """
            $$
            these double $\$ dollars are %%%% embedded but escaped
            $$
            """.trimIndent(),
            """
            |
              these double $$ dollars are %%%% embedded but escaped
              
            """.trimIndent(),
            """
            "these double $$ dollars are %%%% embedded but escaped\n"
            """.trimIndent()
        )
    }

    @Test
    fun testNestedNonDelimitedObjects() {
        assertParsesTo(
            """
                key:
                  nested_key: 10
                  another_nest_key: 3;
                unnested_key: 44
            """.trimIndent(),
            """
                {
                  key: {
                    nested_key: 10
                    another_nest_key: 3
                  }
                  unnested_key: 44
                }
            """.trimIndent(),
            """
                key:
                  nested_key: 10
                  another_nest_key: 3
                unnested_key: 44
            """.trimIndent(),
            """
                {
                  "key": {
                    "nested_key": 10,
                    "another_nest_key": 3
                  },
                  "unnested_key": 44
                }
            """.trimIndent()
        )
    }

    @Test
    fun testNestedNonDelimitedDashLists() {
        assertParsesTo(
            """
                - 
                  - "sub-list elem 1"
                  - "sub-list elem 2";
                - "outer list elem 1"
            """.trimIndent(),
            """
                [
                  [
                    "sub-list elem 1",
                    "sub-list elem 2"
                  ],
                  "outer list elem 1"
                ]
            """.trimIndent(),
            """
                - 
                  - "sub-list elem 1"
                  - "sub-list elem 2"
                - "outer list elem 1"
            """.trimIndent(),
            """
                [
                  [
                    "sub-list elem 1",
                    "sub-list elem 2"
                  ],
                  "outer list elem 1"
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testIgnoredSemiColonError() {
        assertParserRejectsSource("""
            <
              - "sub-list elem 1"
              - "sub-list elem 2";
              - "sub-list elem 3";
              - "sub-list elem 4"
            >
            """.trimIndent(),
            listOf(IGNORED_DASH_LIST_SEMICOLON, IGNORED_DASH_LIST_SEMICOLON)
        )

        assertParserRejectsSource("""
            {
              key1: "val 1"
              key2: "val 2";
              key3: "val 3";
              key4: "val 4"
            }
            """.trimIndent(),
            listOf(IGNORED_OBJECT_SEMICOLON, IGNORED_OBJECT_SEMICOLON)
        )
    }
}

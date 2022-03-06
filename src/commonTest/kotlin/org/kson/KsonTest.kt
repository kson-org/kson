package org.kson

import org.kson.ast.KsonRoot
import org.kson.collections.ImmutableList
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.messages.Message
import org.kson.testSupport.assertMessageFormats
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
     * @param expectedSourceFromAst the expected [KsonRoot.toKsonSource] output for the parsed [source]
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
            parseResult.ast?.toKsonSource(0),
            message
        )
    }

    /**
     * Assertion helper for testing that [source] is rejected by the parser with the messages listed in
     * [expectedParseMessages]
     *
     * @param source is the kson source to parse into a [KsonRoot]
     * @param expectedParseMessages a list of [Message]s produced by parsing [source]
     * @return the produced messages for further validation
     */
    private fun assertParserRejectsSource(
        source: String,
        expectedParseMessages: List<Message>
    ): ImmutableList<LoggedMessage> {
        val parseResult = Kson.parse(source)

        assertEquals(
            expectedParseMessages,
            parseResult.messages.map { it.message },
            "Should have the expected parse errors."
        )

        assertMessageFormats(parseResult.messages)

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
    fun testStringLiteralSource() {
        assertParsesTo(
            """
                "This is a string"
            """,
            "\"This is a string\""
        )
    }

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
    fun testListSource() {
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
        assertParserRejectsSource("\"unclosed", listOf(Message.STRING_NO_CLOSE))
    }

    @Test
    fun testUnclosedEmbedDelimiterError() {
        assertParserRejectsSource("%%\n", listOf(Message.EMBED_BLOCK_NO_CLOSE))
    }

    @Test
    fun testUnclosedEmbedAlternateDelimiterError() {
        assertParserRejectsSource("$$\n", listOf(Message.EMBED_BLOCK_NO_CLOSE))
    }

    @Test
    fun testUnclosedListError() {
        val errorMessages = assertParserRejectsSource("[", listOf(Message.LIST_NO_CLOSE))
        assertEquals(Location(0, 0, 0, 1, 0, 1), errorMessages[0].location)
        assertParserRejectsSource("[1,2,", listOf(Message.LIST_NO_CLOSE))
    }

    @Test
    fun testUnclosedObjectError() {
        assertParserRejectsSource("{", listOf(Message.OBJECT_NO_CLOSE))
        assertParserRejectsSource("{ key: value   ", listOf(Message.OBJECT_NO_CLOSE))
    }

    @Test
    fun testInvalidTrailingKson() {
        assertParserRejectsSource("[1] illegal_key: illegal_value", listOf(Message.EOF_NOT_REACHED))
        assertParserRejectsSource("{ key: value } 4.5", listOf(Message.EOF_NOT_REACHED))
        assertParserRejectsSource("key: value illegal extra identifiers", listOf(Message.EOF_NOT_REACHED))
    }
}
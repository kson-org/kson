package org.kson.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.kson.ast.KsonRoot

class ParserTest {

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
        val messageSink = MessageSink()
        val tokens = Lexer(source, messageSink).tokenize()
        val parseResult = Parser(tokens).parse()

        assertFalse(messageSink.hasErrors(), "Should not have parsing errors, got:\n\n" + messageSink.print())
        assertEquals(
            expectedSourceFromAst,
            parseResult.toKsonSource(0),
            message
        )
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
        // parser todo add test for integers and scientific notation to match JSON spec
        assertParsesTo(
            """
                42.1
            """,
            "42.1"
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
    fun testEmbedBlockSource() {
        assertParsesTo(
            """
                ```
                    this is a raw embed
                ```
            """,
            """
                ```
                    this is a raw embed
                ```
            """.trimIndent()
        )

        assertParsesTo(
            """
                ```sql
                    select * from something
                ```
            """,
            """
                ```sql
                    select * from something
                ```
            """.trimIndent()
        )
    }
}
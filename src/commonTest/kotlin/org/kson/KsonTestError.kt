package org.kson

import org.kson.ast.KsonRoot
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Base class for tests that exercise and verify [Kson] behavior on invalid Kson.  For tests parsing valid Kson,
 * see [KsonTest] and its subclasses.
 *
 * Subclasses of this test are split out basically along the lines of the grammar.  Tests that cross-cut concerns
 * may live in this root test class.
 */
open class KsonTestError {
    /**
     * Assertion helper for testing that [source] is rejected by the parser with the messages listed in
     * [expectedParseMessageTypes]
     *
     * @param source is the kson source to parse into a [KsonRoot]
     * @param expectedParseMessageTypes a list of [MessageType]s produced by parsing [source]
     * @param maxNestingLevel the maximum allowable nested lists and objects to configure the parser to accept
     * @return the produced messages for further validation
     */
    protected fun assertParserRejectsSource(
        source: String,
        expectedParseMessageTypes: List<MessageType>,
        maxNestingLevel: Int? = null
    ): List<LoggedMessage> {
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

    /**
     * Calls [assertParserRejectsSource], but also tests the [Location] of each [LoggedMessage]
     * by asserting it against [expectedParseMessageLocation].
     *
     * @param source is the kson source to parse into a [KsonRoot]
     * @param expectedParseMessageTypes a list of [MessageType]s produced by parsing [source]
     * @param expectedParseMessageLocation a list of [Location]s produced by parsing [source]
     * @param maxNestingLevel the maximum allowable nested lists and objects to configure the parser to accept
     * @return the produced messages for further validation
     */
    protected fun assertParserRejectsSourceWithLocation(
        source: String,
        expectedParseMessageTypes: List<MessageType>,
        expectedParseMessageLocation: List<Location>,
        maxNestingLevel: Int? = null,
    ): List<LoggedMessage> {
        val loggedMessages = assertParserRejectsSource(source, expectedParseMessageTypes, maxNestingLevel)
        assertEquals(
            expectedParseMessageLocation,
            loggedMessages.map { it.location },
            "Should have the expected locations for the messages."
        )
        return loggedMessages
    }

    @Test
    fun testBlankKsonSource() {
        assertParserRejectsSource("", listOf(BLANK_SOURCE))
        assertParserRejectsSource("  ", listOf(BLANK_SOURCE))
        assertParserRejectsSource("\t", listOf(BLANK_SOURCE))
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
    fun testInvalidTrailingKson() {
        assertParserRejectsSource("[1] illegal_key: illegal_value", listOf(EOF_NOT_REACHED))
        assertParserRejectsSourceWithLocation(
            "{ key: value } 4.5",
            listOf(EOF_NOT_REACHED),
            listOf(Location(0, 15, 0, 18, 15, 18))
        )
        assertParserRejectsSource("key: value illegal extra identifiers", listOf(EOF_NOT_REACHED))
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

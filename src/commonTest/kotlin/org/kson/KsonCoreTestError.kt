package org.kson

import org.kson.ast.KsonRoot
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.messages.MessageType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interface to tie together our tests that exercise and verify [KsonCore] behavior on invalid Kson and give a home to our
 * custom assertions for these tests.  For tests parsing valid Kson, see [KsonCoreTest].
 *
 * The tests of type [KsonCoreTestError] are split out basically along the lines of the grammar.  Tests that cross-cut
 * concerns may live in this root test class.
 */
interface KsonCoreTestError {
    /**
     * Assertion helper for testing that [source] is rejected by the parser with the messages listed in
     * [expectedParseMessageTypes]
     *
     * @param source is the kson source to parse into a [KsonRoot]
     * @param expectedParseMessageTypes a list of [MessageType]s produced by parsing [source]
     * @param maxNestingLevel the maximum allowable nested lists and objects to configure the parser to accept
     * @return the produced messages for further validation
     */
    fun assertParserRejectsSource(
        source: String,
        expectedParseMessageTypes: List<MessageType>,
        maxNestingLevel: Int? = null
    ): List<LoggedMessage> {
        val parseResult = if (maxNestingLevel != null) {
            KsonCore.parseToAst(source, CoreCompileConfig(maxNestingLevel = maxNestingLevel))
        } else {
            KsonCore.parseToAst(source)
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
    fun assertParserRejectsSourceWithLocation(
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
}

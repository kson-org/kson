package org.kson

import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import kotlin.test.Test

class KsonCoreTestListError : KsonTestError {
    @Test
    fun testUnclosedDelimitedDashList() {
        assertParserRejectsSource("<", listOf(LIST_NO_CLOSE))
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
    fun testUnclosedListError() {
        assertParserRejectsSourceWithLocation("[", listOf(LIST_NO_CLOSE), listOf(Location.create(0, 0, 0, 1, 0, 1)))
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
    fun testUnopenedDashListError() {
        assertParserRejectsSource(">", listOf(LIST_NO_OPEN))
    }

    @Test
    fun testIllegalListNesting() {
        // test six nested lists with a nesting limit of 5
        assertParserRejectsSource("[[[[[[]]]]]]", listOf(MAX_NESTING_LEVEL_EXCEEDED), 5)

        // test 157 nested lists with a nesting limit of 156
        assertParserRejectsSource("""
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[
        """.trimIndent(), listOf(MAX_NESTING_LEVEL_EXCEEDED), 156)

        // same test as above, but with dashed sub-lists sprinkled in
        assertParserRejectsSource("""
            [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[ - [
            [[[[[[[[[ - 1 - 2 - [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[[[[[[[[[[[[ - 3 - [[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[
            [[[[[[[
        """.trimIndent(), listOf(MAX_NESTING_LEVEL_EXCEEDED), 156)
    }

    @Test
    fun testIgnoredEndDotError() {
        assertParserRejectsSource("""
            <
              - "sub-list elem 1"
              - "sub-list elem 2" =
              - "sub-list elem 3" =
              - "sub-list elem 4"
            >
            """.trimIndent(),
            listOf(IGNORED_DASH_LIST_END_DASH, IGNORED_DASH_LIST_END_DASH)
        )
    }
}

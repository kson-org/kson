package org.kson

import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import kotlin.test.Test

/**
 * Tests for general/mixed Kson values that don't fit neatly into the other [KsonTestError] tests
 */
class KsonTestGeneralError: KsonTestError {
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
            listOf(Location.create(0, 15, 0, 18, 15, 18))
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

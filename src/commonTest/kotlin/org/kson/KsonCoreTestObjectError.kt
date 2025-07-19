package org.kson

import org.kson.parser.messages.MessageType.*
import kotlin.test.Test

class KsonCoreTestObjectError : KsonCoreTestError {
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
    fun testIllegalObjectNesting() {
        assertParserRejectsSource("""
            {'1':{'2':{'3':{'4':{'5':{'6':{'7':{'8':0}}}}}}}}
        """.trimIndent(), listOf(MAX_NESTING_LEVEL_EXCEEDED), 8)
    }

    @Test
    fun testIgnoredEndDotError() {
        assertParserRejectsSource("""
            {
              key1: "val 1"
              key2: "val 2" .
              key3: "val 3" .
              key4: "val 4"
            }
            """.trimIndent(),
            listOf(IGNORED_OBJECT_END_DOT, IGNORED_OBJECT_END_DOT)
        )
    }

    @Test
    fun testHelpfulErrorForReservedWordKeys() {
        assertParserRejectsSource(
            """
               key: value
               null: "can't use null as a key"
               true: "can't use true as a key"
               false: "can't use false as a key"
            """.trimIndent(),
            listOf(OBJECT_KEYWORD_RESERVED_WORD, OBJECT_KEYWORD_RESERVED_WORD, OBJECT_KEYWORD_RESERVED_WORD)
        )

        assertParserRejectsSource(
            """
               key   : value
               null   : "can't use null as a key" 
               true   : "can't use true as a key" 
               false  : "can't use false as a key" 
            """.trimIndent(),
            listOf(OBJECT_KEYWORD_RESERVED_WORD, OBJECT_KEYWORD_RESERVED_WORD, OBJECT_KEYWORD_RESERVED_WORD)
        )
    }
}

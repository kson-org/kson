package org.kson.parser.messages

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class MessagesTest {
    @Test
    fun testNumberStringConfusionMessagesTeachTheUnquotedStringRule() {
        // the lexer reads any value starting with a digit or `-` as a number, so a value rejected as a
        // number may be a string missing its quotes: every such message must teach the quoting fix
        val invalidNumberMessages = listOf(
            MessageType.INVALID_DIGITS.create("a"),
            MessageType.DANGLING_DECIMAL.create(),
            MessageType.DANGLING_EXP_INDICATOR.create("e"),
            MessageType.ILLEGAL_MINUS_SIGN.create()
        )
        for (message in invalidNumberMessages) {
            assertContains(message.toString(), STRING_QUOTING_HINT)
        }

        // the message for number-like object keys teaches the same rule, phrased for a position
        // where only strings are legal
        val keyMessage = MessageType.OBJECT_KEY_REQUIRES_QUOTES.create("12a")
        assertContains(keyMessage.toString(), UNQUOTED_STRING_START_RULE)
    }

    @Test
    fun testFormat() {
        // sanity check formatting is hooked up correctly by verifying we can find our
        // "tagNameForTest" embedded in the formatted string
        //
        // (NOTE: this naturally relies on assumption that EMBED_BLOCK_NO_CLOSE's formatted
        //        message will always refer to its embed tag.  Apologies if that changes and this fails)
        val message = MessageType.EMBED_BLOCK_NO_CLOSE.create("embedDelimiter")
        assertContains(message.toString(), "embedDelimiter")
    }

    @Test
    fun testFormatNullArgs() {
        assertFailsWith(IllegalArgumentException::class, "should blow up on null argument") {
            MessageType.EMBED_BLOCK_NO_CLOSE.create(null)
        }
    }
}
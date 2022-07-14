package org.kson.parser.messages

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class MessagesTest {
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
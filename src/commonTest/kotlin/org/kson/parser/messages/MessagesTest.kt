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
        // (NOTE: this naturally relies on assumption that EMBED_BLOCK_BAD_START's formatted
        //        message will always refer to its embed tag.  Apologies if that changes and this fails)
        val message = MessageType.EMBED_BLOCK_BAD_START.create("tagNameForTest", "%")
        assertContains(message.toString(), "tagNameForTest")
    }

    @Test
    fun testFormatNullArgs() {
        assertFailsWith(IllegalArgumentException::class, "should blow up on null argument") {
            MessageType.EMBED_BLOCK_BAD_START.create(null, "%")
        }
    }
}
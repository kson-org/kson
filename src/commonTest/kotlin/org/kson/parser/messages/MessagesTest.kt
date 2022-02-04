package org.kson.parser.messages

import kotlin.test.Test
import kotlin.test.assertContains

class MessagesTest {
    @Test
    fun testFormat() {
        // sanity check formatting is hooked up correctly by verifying we can find our
        // "tagNameForTest" embedded in the formatted string
        //
        // (NOTE: this naturally relies on assumption that EMBED_BLOCK_BAD_START's formatted
        //        message will always refer to its embed tag.  Apologies if that changes and this fails)
        val formattedMessage = Message.EMBED_BLOCK_BAD_START.format("tagNameForTest", "%")
        assertContains(formattedMessage, "tagNameForTest")
    }
}
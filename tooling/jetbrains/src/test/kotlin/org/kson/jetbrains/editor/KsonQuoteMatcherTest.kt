package org.kson.jetbrains.editor

import org.junit.Test

class KsonQuoteMatcherTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonQuoteHandler] is correctly hooked up
     */
    @Test
    fun testAutoInsert() {
        doCharTest(
            "<caret>",
            '"',
            "\"<caret>\""
        )

        doCharTest(
            "<caret>",
            '\'',
            "'<caret>'"
        )
    }

    @Test
    fun testManualClose() {
        doCharTest(
            "key: \"unclosed<caret>",
            '"',
            "key: \"unclosed\"<caret>"
        )
    }
}
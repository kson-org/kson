package org.kson.jetbrains.editor

class KsonQuoteMatcherTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonQuoteHandler] is correctly hooked up
     */
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

    fun testManualClose() {
        doCharTest(
            "\"<caret>",
            '"',
            "\"\"<caret>"
        )

        doCharTest(
            "key: \"unclosed<caret>",
            '"',
            "key: \"unclosed\"<caret>"
        )
    }
}
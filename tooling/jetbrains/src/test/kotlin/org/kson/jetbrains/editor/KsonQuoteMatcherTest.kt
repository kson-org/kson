package org.kson.jetbrains.editor

class KsonQuoteMatcherTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonQuoteHandler] is correctly hooked up for auto-inserting close quotes
     * This is the inverse operation to what is tested in [KsonBackspaceHandlerDelegateTest.testDeleteEmptyQuotePairs]
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

package org.kson.jetbrains.editor

class KsonPairedBraceMatcherTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonPairedBraceMatcher] is correctly hooked up
     */
    fun testAutoInsert() {
        doCharTest(
            "<caret>",
            '{',
            "{<caret>}"
        )

        doCharTest(
            "<caret>",
            '[',
            "[<caret>]"
        )
    }
}
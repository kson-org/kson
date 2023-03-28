package org.kson.jetbrains.editor

import org.junit.Test

class KsonPairedBraceMatcherTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonPairedBraceMatcher] is correctly hooked up
     */
    @Test
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
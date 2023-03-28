package org.kson.jetbrains.editor

import org.junit.Test

class KsonCommenterTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonCommenter] is correctly hooked up
     */
    @Test
    fun testInsertLineComment() {
        doLineCommentTest(
            "key: value<caret>",
            "#key: value<caret>"
        )
    }

}
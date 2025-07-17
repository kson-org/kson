package org.kson.jetbrains.editor

class KsonCommenterTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonCommenter] is correctly hooked up
     */
    fun testInsertLineComment() {
        doLineCommentTest(
            "key: value<caret>",
            "#key: value<caret>"
        )
    }

}

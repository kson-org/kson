package org.kson.jetbrains.editor

import com.intellij.openapi.actionSystem.IdeActions

class KsonBackspaceHandlerDelegateTest : KsonEditorActionTest() {
    /**
     * Validate quote pairs are deleted when they are empty and the caret is between them
     * This is the inverse operation to what is tested in [org.kson.jetbrains.editor.KsonQuoteHandlerTest.testAutoInsert]
     */
    fun testDeleteEmptyQuotePairs() {
        doIdeActionTest(
            "\"<caret>\"",
            IdeActions.ACTION_EDITOR_BACKSPACE,
            "<caret>"
        )

        doIdeActionTest(
            "\'<caret>\'",
            IdeActions.ACTION_EDITOR_BACKSPACE,
            "<caret>"
        )
    }
}
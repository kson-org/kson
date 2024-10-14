package org.kson.jetbrains.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileTypes.PlainTextFileType

class KsonBackspaceHandlerDelegateTest : KsonEditorActionTest() {
    /**
     * Validate quote pairs are deleted when they are empty and the caret is between them
     * This is the inverse operation to what is tested in [KsonQuoteMatcherTest.testAutoInsert]
     */
    fun testDeleteEmptyQuotePairs() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_QUOTE, true) {
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

        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_QUOTE, false) {
            doIdeActionTest(
                "\"<caret>\"",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>\""
            )

            doIdeActionTest(
                "\'<caret>\'",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>\'"
            )
        }
    }

    /**
     * Validate angle bracket pairs are deleted when they are empty and the caret is between them
     * This is the inverse operation to what is tested in [KsonTypedHandlerDelegateTest.testAngleBracketAutoInsert]
     */
    fun testDeleteEmptyAngleBracketPairs() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, true) {
            doIdeActionTest(
                "<<caret>>",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>"
            )
        }

        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, false) {
            doIdeActionTest(
                "<<caret>>",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>>"
            )
        }
    }

    /**
     * Sanity check that we do NOT auto-delete closing angle brackets in non-Kson files
     */
    fun testNonKsonAngleBracketAutoDelete() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, true) {
            doIdeActionTest(
                "<<caret>>",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>>",
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )
        }
    }
}
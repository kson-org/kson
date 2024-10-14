package org.kson.jetbrains.editor

import com.intellij.openapi.fileTypes.PlainTextFileType

class KsonTypedHandlerDelegateTest : KsonEditorActionTest() {
    /**
     * Sanity check that our angle-bracket auto-insert code is working.
     * This is the inverse operation to what is tested in [KsonBackspaceHandlerDelegateTest.testDeleteEmptyAngleBracketPairs]
     */
    fun testAngleBracketAutoInsert() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, true) {
            doCharTest(
                "<caret>",
                '<',
                "<<caret>>"
            )
        }

        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, false) {
            doCharTest(
                "<caret>",
                '<',
                "<<caret>"
            )
        }
    }

    /**
     * Sanity check that we do NOT auto-insert closing angle brackets in non-Kson files `>`
     */
    fun testNonKsonAngleBracketAutoInsert() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, true) {
            doCharTest(
                "<caret>",
                '<',
                "<<caret>",
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )
        }
    }
}
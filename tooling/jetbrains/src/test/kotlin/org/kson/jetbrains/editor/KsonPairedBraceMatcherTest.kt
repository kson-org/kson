package org.kson.jetbrains.editor

class KsonPairedBraceMatcherTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonPairedBraceMatcher] is correctly hooked up
     */
    fun testAutoInsert() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, true) {
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

        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, false) {
            doCharTest(
                "<caret>",
                '{',
                "{<caret>"
            )

            doCharTest(
                "<caret>",
                '[',
                "[<caret>"
            )
        }
    }
}
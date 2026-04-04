package org.kson.jetbrains.editor

class KsonPairedBraceMatcherTest : KsonEditorActionTest() {
    /**
     * Sanity check that [KsonPairedBraceMatcher] is correctly hooked up
     */
    fun testAutoInsert() {
        withConfigSetting(ConfigProperty.AutoInsertPairBracked(), true) {
            doCharTest(
                "<caret>",
                '{',
                "{<caret>}",
            )

            doCharTest(
                "<caret>",
                '[',
                "[<caret>]",
            )
        }

        withConfigSetting(ConfigProperty.AutoInsertPairBracked(), false) {
            doCharTest(
                "<caret>",
                '{',
                "{<caret>",
            )

            doCharTest(
                "<caret>",
                '[',
                "[<caret>",
            )
        }
    }
}

package org.kson.jetbrains.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileTypes.PlainTextFileType
import org.kson.jetbrains.file.KsonFileType
import org.kson.parser.behavior.embedblock.EmbedDelim

class KsonBackspaceHandlerDelegateTest : KsonEditorActionTest() {
    /**
     * Validate quote pairs are deleted when they are empty and the caret is between them
     * This is the inverse operation to what is tested in [KsonQuoteMatcherTest.testAutoInsert]
     */
    fun testDeleteEmptyQuotePairs() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_QUOTE(), true) {
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

        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_QUOTE(), false) {
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
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
            doIdeActionTest(
                "<<caret>>",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>"
            )
        }

        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), false) {
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
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
            doIdeActionTest(
                "<<caret>>",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>>",
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )
        }
    }

    /**
     * Validate embed delimiter pairs are deleted when they are empty and the caret is between them
     * This is the inverse operation to what is tested in [KsonTypedHandlerDelegateTest.testEmbedDelimiterAutoInsert]
     */
    fun testDeleteEmptyEmbedDelimitersPairs() {
        for (delimiter in listOf(EmbedDelim.Percent, EmbedDelim.Dollar)) {
            val halfDelim = delimiter.char
            val fullDelim = delimiter.delimiter
            val altFullDelim = if (delimiter == EmbedDelim.Percent) {
                EmbedDelim.Dollar.delimiter
            } else {
                EmbedDelim.Percent.delimiter
            }

            withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
                doIdeActionTest(
                    """
                    $fullDelim<caret>
                    $fullDelim
                    """.trimIndent(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    "$halfDelim<caret>"
                )

                // should delete even if a comma follows the closing delimiter
                doIdeActionTest(
                    """
                    $fullDelim<caret>
                    $fullDelim,
                    """.trimIndent(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    "$halfDelim<caret>,"
                )

                // should only delete when closing embed delimiter has correct indent
                doIdeActionTest(
                    """
                    |my_embed: $fullDelim<caret>
                    |  $fullDelim
                    """.trimMargin(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    "my_embed: $halfDelim<caret>"
                )

                // should not delete if closing embed delimiter does not have the correct indent (i.e. does not look auto-inserted)
                doIdeActionTest(
                    """
                    my_embed: $fullDelim<caret>
                      $fullDelim
                    """.trimIndent(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    """
                    my_embed: $halfDelim<caret>
                    """.trimIndent()
                )

                // should not delete if anything other than a newline follows the opening delimiter
                doIdeActionTest(
                    // note the 'tag' text here after the <caret>
                    """
                    $fullDelim<caret>tag
                    $fullDelim
                    """.trimIndent(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    """
                    $halfDelim<caret>tag
                    $fullDelim
                    """.trimIndent()
                )

                // should not delete if anything other than a newline follows the opening delimiter
                doIdeActionTest(
                    // note the extra space here after the <caret>
                    "$fullDelim<caret> \n" +
                            fullDelim,
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    "$halfDelim<caret> \n" +
                            fullDelim
                )

                // should not delete if anything other than a newline, a comma or EOF follows the closing delimiter
                doIdeActionTest(
                    // note the "stuff" trailing the closing delim here
                    """
                    $fullDelim<caret>
                    $fullDelim stuff
                    """.trimIndent(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    """
                    $halfDelim<caret>
                    $fullDelim stuff
                    """.trimIndent()
                )

                // should not delete if anything other than a newline or EOF follows the closing delimiter
                doIdeActionTest(
                    "$fullDelim<caret>\n" +
                            // note the trailing space here
                            "$fullDelim ",
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    "$halfDelim<caret>\n" +
                            "$fullDelim "
                )

                // should not delete inside strings
                doIdeActionTest(
                    """
                    "$fullDelim<caret>
                    $fullDelim"
                    """.trimIndent(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    """
                    "$halfDelim<caret>
                    $fullDelim"
                    """.trimIndent()
                )

                // should not delete inside embeds
                doIdeActionTest(
                    """
                    $altFullDelim
                      $fullDelim<caret>
                      $fullDelim
                    $altFullDelim"
                    """.trimIndent(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    """
                    $altFullDelim
                      $halfDelim<caret>
                      $fullDelim
                    $altFullDelim"
                    """.trimIndent()
                )
            }

            withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), false) {
                doIdeActionTest(
                    """
                    $fullDelim<caret>
                    $fullDelim
                    """.trimIndent(),
                    IdeActions.ACTION_EDITOR_BACKSPACE,
                    """
                    $halfDelim<caret>
                    $fullDelim
                    """.trimIndent()
                )
            }
        }
    }

    /**
     * Sanity check that we do NOT auto-delete closing embed delimiters in non-Kson files
     */
    fun testNonKsonEmbedDelimAutoDelete() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
            doIdeActionTest(
                """
                %%<caret>
                %%
                """.trimIndent(),
                IdeActions.ACTION_EDITOR_BACKSPACE,
                """
                %<caret>
                %%
                """.trimIndent(),
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )

            doIdeActionTest(
                """
                $$<caret>
                $$
                """.trimIndent(),
                IdeActions.ACTION_EDITOR_BACKSPACE,
                """
                $<caret>
                $$
                """.trimIndent(),
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )
        }
    }

    /**
     * Ensure the lookbehinds in [KsonBackspaceHandlerDelegate] don't trip at the boundary
     */
    fun testBackspaceAtTheBoundary() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
            doIdeActionTest(
                "x<caret>",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>"

            )

            doIdeActionTest(
                "x<caret>y",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "<caret>y"
            )

            doIdeActionTest(
                "xy<caret>z",
                IdeActions.ACTION_EDITOR_BACKSPACE,
                "x<caret>z"
            )
        }
    }

    /**
     * Integration test that verifies both auto-insertion and deletion behavior work together correctly
     */
    fun testAutoInsertAndDeleteIntegration() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
            doTypingAndBackspaceTest(
                "<caret>",
                "<",
                "<<caret>>",
                "<caret>"
            )

            doTypingAndBackspaceTest(
                "<caret>",
                "%%",
                """
                %%<caret>
                %%
                """.trimIndent(),
                "%<caret>"
            )

            doTypingAndBackspaceTest(
                "key: <caret>",
                "$$",
                """
                key: $$<caret>
                  $$
                """.trimIndent(),
                "key: $<caret>"
            )

            doTypingAndBackspaceTest(
                "key: <caret>",
                "%%",
                """
                key: %%<caret>
                  %%
                """.trimIndent(),
                "key: %<caret>"
            )
        }
    }

    private fun doTypingAndBackspaceTest(
        initialText: String,
        charsToType: String,
        expectedAfterTyping: String,
        expectedAfterBackspace: String
    ) {
        // First verify the auto-insertion
        doTypingTest(initialText, charsToType, expectedAfterTyping)

        // Then verify the deletion
        doIdeActionTest(
            expectedAfterTyping,
            IdeActions.ACTION_EDITOR_BACKSPACE,
            expectedAfterBackspace
        )
    }

    private fun doTypingTest(
        initialText: String,
        charsToType: String,
        expectedText: String
    ) {
        myFixture.configureByText(KsonFileType, initialText)
        for (char in charsToType) {
            myFixture.type(char)
        }
        myFixture.checkResult(expectedText)
    }
}

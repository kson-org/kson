package org.kson.jetbrains.editor

import com.intellij.openapi.fileTypes.PlainTextFileType
import org.kson.parser.behavior.embedblock.EmbedDelim

class KsonTypedHandlerDelegateTest : KsonEditorActionTest() {
    /**
     * Sanity check that our angle-bracket auto-insert code is working.
     * This is the inverse operation to what is tested in [KsonBackspaceHandlerDelegateTest.testDeleteEmptyAngleBracketPairs]
     */
    fun testAngleBracketAutoInsert() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
            doCharTest(
                "<caret>",
                '<',
                "<<caret>>"
            )

            // should not auto-insert in comment block
            doCharTest(
                "#    <caret>",
                '<',
                "#    <<caret>"
            )

            // should auto-insert below comment block
            doCharTest(
                """
                    # This is a commented line
                    <caret>
                """.trimIndent(),
                '<',
                """
                    # This is a commented line
                    <<caret>>
                """.trimIndent(),
            )

            // should not auto-insert if already closed
            doCharTest(
                "<caret>>",
                '<',
                "<<caret>>"
            )

            // should not auto-insert in comment block
            doCharTest(
                "#<caret>",
                '<',
                "#<<caret>"
            )

            // should not auto-insert in a string
            doCharTest(
                "\"<caret>\"",
                '<',
                "\"<<caret>\""
            )

            // should not auto-insert in a string
            doCharTest(
                "\"  <caret>\"",
                '<',
                "\"  <<caret>\""
            )
        }

        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), false) {
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
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
            doCharTest(
                "<caret>",
                '<',
                "<<caret>",
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )
        }
    }

    /**
     * Verify our embed-delimiter auto-insert code is working as desired
     * This is the inverse operation to what is tested in [KsonBackspaceHandlerDelegateTest.testDeleteEmptyEmbedDelimitersPairs]
     */
    fun testEmbedDelimiterAutoInsert() {
        for (delimiter in listOf(EmbedDelim.Percent, EmbedDelim.Dollar)) {
            val openDelim = delimiter.openDelimiter
            val closeDelimiter = delimiter.closeDelimiter
            val altDelim = if (delimiter == EmbedDelim.Percent) {
                EmbedDelim.Dollar
            } else {
                EmbedDelim.Percent
            }

            withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
                doCharTest(
                    "<caret>",
                    openDelim,
                    """
                    $openDelim<caret>
                    $closeDelimiter
                    """.trimIndent(),
                )

                // should auto-insert if making a list item, i.e. followed by a comma
                doCharTest(
                    "<caret>,",
                    openDelim,
                    """
                    $openDelim<caret>
                    $closeDelimiter,
                    """.trimIndent(),
                )

                // should not auto-insert if anything other than a comma follows our embed delim
                doCharTest(
                    "<caret>stuff",
                    openDelim,
                    "$openDelim<caret>stuff"
                )

                // should not auto-insert if already closed
                doCharTest(
                    """
                    <caret>
                    some embed content
                    $closeDelimiter
                    """.trimIndent(),
                    openDelim,
                    """
                    $openDelim<caret>
                    some embed content
                    $closeDelimiter
                    """.trimIndent(),
                )

                // should not auto-insert if already closed
                doCharTest(
                    """
                    <caret>
                    $closeDelimiter
                    """.trimIndent(),
                    openDelim,
                    """
                    $openDelim<caret>
                    $closeDelimiter
                    """.trimIndent(),
                )

                // should not auto-insert if closing an embed block
                doCharTest(
                    """
                    $openDelim
                    $openDelim<caret>
                    """.trimIndent(),
                    openDelim,
                    """
                    $openDelim
                    $closeDelimiter<caret>
                    """.trimIndent(),
                )

                // should not auto-insert inside strings
                doCharTest(
                    """
                    "<caret>
                    "
                    """.trimIndent(),
                    openDelim,
                    """
                    "$openDelim<caret>
                    "
                    """.trimIndent(),
                )

                // should not auto-insert inside strings
                doCharTest(
                    """
                    "  <caret>
                    "
                    """.trimIndent(),
                    openDelim,
                    """
                    "  $openDelim<caret>
                    "
                    """.trimIndent(),
                )

                // should not auto-insert inside other embeds
                doCharTest(
                    """
                    ${altDelim.openDelimiter}
                        <caret>
                    ${altDelim.closeDelimiter}
                    """.trimIndent(),
                    openDelim,
                    """
                    ${altDelim.openDelimiter}
                        $openDelim<caret>
                    ${altDelim.closeDelimiter}
                    """.trimIndent()
                )

                // should not auto-insert in commented line
                doCharTest(
                    """
                    #<caret>
                    """.trimIndent(),
                    openDelim,
                    """
                    #$openDelim<caret>
                    """.trimIndent()
                )
            }

            doCharTest(
                """
                    |key1: <caret>
                    """.trimMargin(),
                openDelim,
                """
                    |key1: $openDelim<caret>
                    |  $closeDelimiter
                    """.trimMargin()
            )

            doCharTest(
                """
                    |key1:
                    |  key2: <caret>
                    """.trimMargin(),
                openDelim,
                """
                    |key1:
                    |  key2: $openDelim<caret>
                    |    $closeDelimiter
                    """.trimMargin()
            )

            doCharTest(
                """
                    |key1: <caret>
                    |key2: value2
                    """.trimMargin(),
                openDelim,
                """
                    |key1: $openDelim<caret>
                    |  $closeDelimiter
                    |key2: value2
                    """.trimMargin()
            )

            withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), false) {
                doCharTest(
                    "<caret>",
                    openDelim,
                    "$openDelim<caret>"
                )
            }
        }
    }

    /**
     * Sanity check that we do NOT auto-insert closing embed delimiters in non-Kson files
     */
    fun testNonKsonEmbedDelimiterAutoInsert() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET(), true) {
            doCharTest(
                "%<caret>",
                EmbedDelim.Percent.char,
                "%%<caret>",
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )

            doCharTest(
                "$<caret>",
                EmbedDelim.Dollar.char,
                "$$<caret>",
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )
        }
    }
}

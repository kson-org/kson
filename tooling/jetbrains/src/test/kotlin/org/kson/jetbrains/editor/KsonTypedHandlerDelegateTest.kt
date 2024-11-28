package org.kson.jetbrains.editor

import com.intellij.openapi.fileTypes.PlainTextFileType
import org.kson.parser.EMBED_DELIMITER
import org.kson.parser.EMBED_DELIMITER_ALT


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

    /**
     * Verify our embed-delimiter auto-insert code is working as desired
     * This is the inverse operation to what is tested in [KsonBackspaceHandlerDelegateTest.testDeleteEmptyEmbedDelimitersPairs]
     */
    fun testEmbedDelimiterAutoInsert() {
        for (halfDelim in listOf(org.kson.parser.EMBED_DELIM_CHAR, org.kson.parser.EMBED_DELIM_ALT_CHAR)) {
            val fullDelim = "$halfDelim$halfDelim"
            val altFullDelim = if (fullDelim == EMBED_DELIMITER) {
                EMBED_DELIMITER_ALT
            } else {
                EMBED_DELIMITER
            }

            withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, true) {
                doCharTest(
                    "$halfDelim<caret>",
                    halfDelim,
                    """
                    $fullDelim<caret>
                    $fullDelim
                    """.trimIndent(),
                )

                // should auto-insert if making a list item, i.e. followed by a comma
                doCharTest(
                    "$halfDelim<caret>,",
                    halfDelim,
                    """
                    $fullDelim<caret>
                    $fullDelim,
                    """.trimIndent(),
                )

                // should not auto-insert if anything other than a comma follows our embed delim
                doCharTest(
                    "$halfDelim<caret>stuff",
                    halfDelim,
                    "$fullDelim<caret>stuff"
                )

                // should not auto-insert if already closed
                doCharTest(
                    """
                    $halfDelim<caret>
                    $fullDelim
                    """.trimIndent(),
                    halfDelim,
                    """
                    $fullDelim<caret>
                    $fullDelim
                    """.trimIndent(),
                )

                // should not auto-insert if closing an embed block
                doCharTest(
                    """
                    $fullDelim
                    $halfDelim<caret>
                    """.trimIndent(),
                    halfDelim,
                    """
                    $fullDelim
                    $fullDelim<caret>
                    """.trimIndent(),
                )

                // should not auto-insert inside strings
                doCharTest(
                    """
                    "$halfDelim<caret>
                    "
                    """.trimIndent(),
                    halfDelim,
                    """
                    "$fullDelim<caret>
                    "
                    """.trimIndent(),
                )

                // should not auto-insert inside other embeds
                doCharTest(
                    """
                    $altFullDelim
                        $halfDelim<caret>
                    $altFullDelim
                    """.trimIndent(),
                    halfDelim,
                    """
                    $altFullDelim
                        $fullDelim<caret>
                    $altFullDelim
                    """.trimIndent()
                )
            }

            withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, false) {
                doCharTest(
                    "$halfDelim<caret>",
                    halfDelim,
                    "$fullDelim<caret>"
                )
            }
        }
    }

    /**
     * Sanity check that we do NOT auto-insert closing embed delimiters in non-Kson files
     */
    fun testNonKsonEmbedDelimiterAutoInsert() {
        withConfigSetting(ConfigProperty.AUTOINSERT_PAIR_BRACKET, true) {
            doCharTest(
                "%<caret>",
                '%',
                "%%<caret>",
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )

            doCharTest(
                "$<caret>",
                '$',
                "$$<caret>",
                // NOTE: this is NOT a Kson file
                PlainTextFileType.INSTANCE
            )
        }
    }
}
package org.kson.jetbrains.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.kson.tools.format
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.file.KsonFileType

/**
 * Note that since [KsonExternalFormatter] delegates to [format], the testing here
 * is mostly sanity checking that things are hooked up correctly and that IDE-specific
 * functionality is working, such as IDE configuration
 */
class KsonExternalFormatterTest : BasePlatformTestCase() {
    private lateinit var settings: CodeStyleSettings
    
    override fun setUp() {
        super.setUp()
        settings = CodeStyle.createTestSettings()
        val commonSettings = settings.getCommonSettings(KsonLanguage)
        commonSettings.indentOptions?.INDENT_SIZE = 2
        commonSettings.indentOptions?.USE_TAB_CHARACTER = false
    }

    /**
     * A not-quite-effective regression test for a bug in how [KsonExternalFormatter] interacted with the undo stack
     * in the IDE: an earlier version of [KsonExternalFormatter] extended [AsyncDocumentFormattingService] rather than
     * [AbstractDocumentFormattingService], an in the actual IDE the steps captured in this test (type some chars,
     * perform a "Reformat File..", then Undo) would result in the format and one or more of the chars being Undone.
     *
     * What makes this test "not-quite-effective" is that the intellij plugin test framework seems to synchronize
     * these operations, so this test is not actually able to reproduce the bug on the old code.  But it does document
     * the issue, and it does verify that something that must work does work, so here it is.
     */
    fun testFormatAndUndo() {
        val beforeFormat = "{ key: 'value' nested: { inner: stuff } }"
        val afterFormat = """
            key: value
            nested:
              inner: stuff
        """.trimIndent()

        val settings = CodeStyle.createTestSettings()
        CodeStyle.doWithTemporarySettings(project, settings, Runnable {
            myFixture.configureByText(KsonFileType, "<caret>\n$beforeFormat")

            // type some stuff
            myFixture.type("#")
            myFixture.type("1")
            myFixture.type("2")

            // perform and verify the format action
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
            assertEquals("#12\n$afterFormat", myFixture.editor.document.text)

            // undo the format
            myFixture.performEditorAction(IdeActions.ACTION_UNDO)

            // expect to be at exactly the previous state of unformatted + typed chars
            assertEquals("#12\n$beforeFormat", myFixture.editor.document.text)
        })
    }

    fun testObjectIndentation() {
        doFullFormatTest(
            """
                {
                                key: value
                           }
            """.trimIndent(),
            """
               key: value
            """.trimIndent()
        )
    }

    fun testListIndentation() {
        doFullFormatTest(
            """
                [
                                1, 2, 3
                           ]
            """.trimIndent(),
            """
                - 1
                - 2
                - 3
            """.trimIndent()
        )

        doFullFormatTest(
            """
                list: [
                                1,
                    2,
                                        3
                           ]
            """.trimIndent(),
            """
                list:
                  - 1
                  - 2
                  - 3
            """.trimIndent()
        )
    }

    fun testDashIndentation() {
        doFullFormatTest(
            """
                list:
                        - 1
                - 2
                    - 3
            """.trimIndent(),
            """
              list:
                - 1
                - 2
                - 3
            """.trimIndent()
        )
    }

    fun testCustomIndentSize() {
        settings.getCommonSettings(KsonLanguage).indentOptions?.INDENT_SIZE = 4
        
        doFullFormatTest(
            """
                {
                            key: value
                       }
            """.trimIndent(),
            """
               key: value
            """.trimIndent()
        )

        doFullFormatTest(
            """
                list:
                        - first
                    - second
                            - third
            """.trimIndent(),
            """
                list:
                    - first
                    - second
                    - third
            """.trimIndent()
        )
    }

    /**
     * Note that we just sanity check tabs since pretty much all the indent code is shared with spaces
     */
    fun testTabIndentation() {
        // Configure to use tabs instead of spaces
        val commonSettings = settings.getCommonSettings(KsonLanguage)
        commonSettings.indentOptions?.USE_TAB_CHARACTER = true

        doFullFormatTest(
            """
                {
                        key: value
                   }
            """.trimIndent(),
            """
              ${'\t'}key: value
            """.trimIndent()
        )

        doFullFormatTest(
            """
                list:
                        - first
                    - second
                            - third
            """.trimIndent(),
            """
                ${'\t'}list:
                ${'\t'}${'\t'}- first
                ${'\t'}${'\t'}- second
                ${'\t'}${'\t'}- third
            """.trimIndent()
        )
    }

    /**
     * This method does full reformat of the given [textBefore] and asserts that it matches
     * [expectedTextAfter].
     */
    private fun doFullFormatTest(textBefore: String, expectedTextAfter: String) {
        val file: PsiFile = myFixture.configureByText("A.kson", textBefore)

        CommandProcessor.getInstance().executeCommand(project, {
            ApplicationManager.getApplication().runWriteAction {
                CodeStyle.doWithTemporarySettings(project, settings, Runnable {
                    val rangeToUse = file.textRange
                    val styleManager = CodeStyleManager.getInstance(project)
                    styleManager.reformatText(file, rangeToUse.startOffset, rangeToUse.endOffset)
                })
            }
        }, "", "")

        TestCase.assertEquals("Reformat Code failed", expectedTextAfter, file.text)
    }
}

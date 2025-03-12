package org.kson.jetbrains.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.kson.jetbrains.KsonLanguage

class KsonExternalFormatterTest : BasePlatformTestCase() {
    private lateinit var settings: CodeStyleSettings
    
    override fun setUp() {
        super.setUp()
        settings = CodeStyle.createTestSettings()
        val commonSettings = settings.getCommonSettings(KsonLanguage)
        commonSettings.indentOptions?.INDENT_SIZE = 2
        commonSettings.indentOptions?.USE_TAB_CHARACTER = false
    }

    fun testObjectIndentation() {
        doFullFormatTest(
            """
                {
                                key: value
                           }
            """.trimIndent(),
            """
                {
                  key: value
                }
            """.trimIndent()
        )

        // should not add an indent to a root object with no braces
        doFullFormatTest(
            "key: value",
            "key: value"
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
                [
                  1, 2, 3
                ]
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
                list: [
                  1,
                  2,
                  3
                ]
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
                {
                    key: value
                }
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
                {
                ${'\t'}key: value
                }
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
                ${'\t'}- first
                ${'\t'}- second
                ${'\t'}- third
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

package org.kson.jetbrains.formatter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class KsonExternalFormatterTest : BasePlatformTestCase() {

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

    /**
     * This method does full reformat of the given [textBefore] and asserts that it matches
     * [expectedTextAfter].
     */
    private fun doFullFormatTest(textBefore: String, expectedTextAfter: String) {
        val file: PsiFile = myFixture.configureByText("A.kson", textBefore)
        CommandProcessor.getInstance().executeCommand(project, {
            ApplicationManager.getApplication().runWriteAction(
                {
                    val rangeToUse = file.textRange
                    val styleManager = CodeStyleManager.getInstance(project)
                    styleManager.reformatText(file, rangeToUse.startOffset, rangeToUse.endOffset)
                }
            )
        }, "", "")

        TestCase.assertEquals("Reformat Code failed", expectedTextAfter, (file.text))
    }
}

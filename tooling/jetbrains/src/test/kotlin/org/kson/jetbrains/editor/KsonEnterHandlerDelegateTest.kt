package org.kson.jetbrains.editor

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.file.KsonFileType

class KsonEnterHandlerDelegateTest : BasePlatformTestCase() {
    private fun doTest(before: String, after: String, useTabs: Boolean = false, indentSize: Int = 2) {
        val settings = CodeStyle.createTestSettings()
        val commonSettings = settings.getCommonSettings(KsonLanguage)
        commonSettings.indentOptions?.INDENT_SIZE = indentSize
        commonSettings.indentOptions?.USE_TAB_CHARACTER = useTabs

        CodeStyle.doWithTemporarySettings(project, settings, Runnable {
            WriteCommandAction.runWriteCommandAction(project) {
                myFixture.configureByText(KsonFileType, before)
                myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
            }

            myFixture.checkResult(after)
        })
    }

    fun testAutoInsertOfCloseBrace() {
        doTest(
            """
            root: {<caret>}
            """.trimIndent(),
            """
            root: {
              <caret>
            }
            """.trimIndent()
        )

        doTest(
            """
            root: {<caret>
            }
            """.trimIndent(),
            """
            root: {
              <caret>
            }
            """.trimIndent()
        )

        doTest(
            """
            root: {<caret>
                    }
            """.trimIndent(),
            """
            root: {
              <caret>
            }
            """.trimIndent()
        )

        doTest(
            """
            root: {<caret>       }
            """.trimIndent(),
            """
            root: {
              <caret>
            }
            """.trimIndent()
        )

        doTest(
            """
            root: {
              <caret>
              
            }
            """.trimIndent(),
            """
            root: {
              
              <caret>
              
            }
            """.trimIndent()
        )
    }

    fun testAutoInsertOfCloseBracket() {
        doTest(
            """
            root: [<caret>]
            """.trimIndent(),
            """
            root: [
              <caret>
            ]
            """.trimIndent()
        )

        doTest(
            """
            root: [<caret>
            ]
            """.trimIndent(),
            """
            root: [
              <caret>
            ]
            """.trimIndent()
        )

        doTest(
            """
            root: [<caret>
                    ]
            """.trimIndent(),
            """
            root: [
              <caret>
            ]
            """.trimIndent()
        )

        doTest(
            """
            root: [<caret>       ]
            """.trimIndent(),
            """
            root: [
              <caret>
            ]
            """.trimIndent()
        )
    }

    fun testAutoInsertOfAngleBracket() {
        doTest(
            """
            root: <<caret>>
            """.trimIndent(),
            """
            root: <
              <caret>
            >
            """.trimIndent()
        )

        doTest(
            """
            root: <<caret>
            >
            """.trimIndent(),
            """
            root: <
              <caret>
            >
            """.trimIndent()
        )

        doTest(
            """
            root: <<caret>
                    >
            """.trimIndent(),
            """
            root: <
              <caret>
            >
            """.trimIndent()
        )

        doTest(
            """
            root: <<caret>       >
            """.trimIndent(),
            """
            root: <
              <caret>
            >
            """.trimIndent()
        )
    }
} 

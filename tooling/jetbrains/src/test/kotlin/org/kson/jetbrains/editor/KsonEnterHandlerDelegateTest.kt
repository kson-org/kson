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
            root: <<caret>       >
            """.trimIndent(),
            """
            root: <
              <caret>
            >
            """.trimIndent()
        )
    }

    fun testNestedIndentation() {
        doTest(
            """
            root: {
              nested: {<caret>}
            }
            """.trimIndent(),
            """
            root: {
              nested: {
                <caret>
              }
            }
            """.trimIndent()
        )

        doTest(
            """
            root: [
              [<caret>]
            ]
            """.trimIndent(),
            """
            root: [
              [
                <caret>
              ]
            ]
            """.trimIndent()
        )

        doTest(
            """
            root: {
              <<caret>>
            }
            """.trimIndent(),
            """
            root: {
              <
                <caret>
              >
            }
            """.trimIndent()
        )
    }

    fun testCustomIndentSize() {
        doTest(
            """
            root: {<caret>
            """.trimIndent(),
            """
            root: {
                <caret>
            }
            """.trimIndent(),
            useTabs = false,
            indentSize = 4
        )
    }

    fun testTabIndentation() {
        doTest(
            """
            root: {<caret>
            """.trimIndent(),
            """
            root: {
            ${'\t'}<caret>
            }
            """.trimIndent(),
            useTabs = true
        )
    }

    fun testNoIndentationAfterClosingBrace() {
        doTest(
            """
            root: {
                nested: {
                }<caret>
            """.trimIndent(),
            """
            root: {
                nested: {
                }
                <caret>
            """.trimIndent()
        )
    }

    fun testEmptyLineIndentation() {
        doTest(
            """
            root: {
                <caret>
            """.trimIndent(),
            """
            root: {
                
                <caret>
            """.trimIndent()
        )
    }

    fun testEnterAfterDash() {
        doTest(
            """
            items:
              - first item<caret>
            """.trimIndent(),
            """
            items:
              - first item
              <caret>
            """.trimIndent()
        )
    }

    fun testEnterAfterDelimitedDashListItem() {
        doTest(
            """
            root: <
              - first<caret>
            """.trimIndent(),
            """
            root: <
              - first
              <caret>
            """.trimIndent()
        )
    }

    fun testNestedMixedBrackets() {
        doTest(
            """
            root: {
              list: [
                item: <<caret>
              ]
            }
            """.trimIndent(),
            """
            root: {
              list: [
                item: <
                  <caret>
              ]
            }
            """.trimIndent()
        )
    }

    fun testMidLineEnterInObject() {
        doTest(
            """
            root: { key: value<caret>another: value }
            """.trimIndent(),
            """
            root: { key: value
              <caret>another: value }
            """.trimIndent()
        )
    }

    fun testMidLineEnterInList() {
        doTest(
            """
            list: [one, two,<caret>three, four]
            """.trimIndent(),
            """
            list: [one, two,
              <caret>three, four]
            """.trimIndent()
        )
    }

    fun testComplexNestedStructure() {
        doTest(
            """
            root: {
              items: [
                {
                  sublist: <<caret>
            """.trimIndent(),
            """
            root: {
              items: [
                {
                  sublist: <
                    <caret>
            """.trimIndent()
        )
    }

    fun testIndentationAfterComma() {
        doTest(
            """
            root: {
              first: value,<caret>
            """.trimIndent(),
            """
            root: {
              first: value,
              <caret>
            """.trimIndent()
        )
    }

    fun testDashListWithObject() {
        doTest(
            """
            items: <
              - {<caret>
            """.trimIndent(),
            """
            items: <
              - {
                <caret>
              }
            """.trimIndent()
        )
    }

    fun testCommaListWithObject() {
        doTest(
            """
            items: [
              {<caret>
            """.trimIndent(),
            """
            items: [
              {
                <caret>
              }
            """.trimIndent()
        )
    }

    fun testRespectsCloseDelimiters() {
        doTest(
            """
              {{{}<caret>}}
            """.trimIndent(),
            """
              {{{}
              <caret>}}
            """.trimIndent()
        )

        doTest(
            """
              [[[]<caret>]]
            """.trimIndent(),
            """
              [[[]
              <caret>]]
            """.trimIndent()
        )

        doTest(
            """
              [[<caret>]
            """.trimIndent(),
            """
              [[
                  <caret>
                ]
            """.trimIndent()
        )
    }
}

package org.kson.jetbrains.inject

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.InjectionAssertionData
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.intellij.lang.annotations.Language
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.file.KsonFileType
import org.kson.parser.behavior.embedblock.EmbedDelim

/**
 * Tests for KSON language injection functionality.
 */
class KsonInjectionTest : BasePlatformTestCase() {
    private lateinit var settings: CodeStyleSettings

    override fun setUp() {
        super.setUp()
        settings = CodeStyle.createTestSettings()
        val commonSettings = settings.getCommonSettings(KsonLanguage)
        commonSettings.indentOptions?.INDENT_SIZE = 2
        commonSettings.indentOptions?.USE_TAB_CHARACTER = false
    }

    /**
     * Represents an action that can be performed in the fragment or main editor
     */
    sealed class ActionInEditor {
        /**
         * Type the given text in the editor
         */
        data class TypeText(val text: String) : ActionInEditor()

        /**
         * Perform a standard editor action (like select all, delete, etc)
         */
        data class EditorAction(val actionId: String) : ActionInEditor()
    }

    /**
     * Asserts that language injection is present and working correctly.
     *
     * @param text The KSON text to test with
     * @param injectionAssertion The expected text in the injected file and the corresponding language ID
     */
    fun hasInjectionPresent(
        @Language("kson") text: String,
        injectionAssertion: InjectionAssertionData,
    ) {
        myFixture.configureByText(KsonFileType, text.trimIndent())
        InjectionTestFixture(myFixture).assertInjected(injectionAssertion)
    }

    /**
     * Asserts that the the fragment editor and main editor run in sync.
     *
     * @param text The KSON text to test with
     * @param expectedTextInFragmentEditor the text we expect to see in the fragment editor
     * @param expectedTextInMainAfterActions The expected text in the main editor after performing actions
     *
     * @param actions A list of actions to perform in the fragment editor
     */
    private fun hasCorrectTextEditors(
        @Language("kson") text: String,
        expectedTextInMainAfterActions: String,
        expectedTextInFragmentEditor: String = "",
        actions: List<ActionInEditor>,
    ) {
        myFixture.configureByText(KsonFileType, text.trimIndent())

        if (expectedTextInFragmentEditor.isNotEmpty()) {
            val fragmentEditor = InjectionTestFixture(myFixture).openInFragmentEditor()

            assertEquals(
                "should find the correct text in fragment editor",
                expectedTextInFragmentEditor,
                fragmentEditor.file.text,
            )

            CodeStyle.doWithTemporarySettings(project, settings, Runnable {
                WriteCommandAction.runWriteCommandAction(project) {
                    for (action in actions) {
                        when (action) {
                            is ActionInEditor.TypeText -> fragmentEditor.type(action.text)
                            is ActionInEditor.EditorAction -> fragmentEditor.performEditorAction(action.actionId)
                        }
                    }
                }
            })

        } else {
            CodeStyle.doWithTemporarySettings(project, settings, Runnable {
                WriteCommandAction.runWriteCommandAction(project) {
                    for (action in actions) {
                        when (action) {
                            is ActionInEditor.TypeText -> myFixture.type(action.text)
                            is ActionInEditor.EditorAction -> myFixture.performEditorAction(action.actionId)
                        }
                    }

                }
            })

        }

        assertEquals(
            "should match with the actions executed in the fragment editor",
            expectedTextInMainAfterActions,
            InjectionTestFixture(myFixture).topLevelFile.text
        )
    }


    /**
     * Assert that manual language injection is available when no injection is present.
     */
    private fun hasLanguageInjectionAvailable(@Language("kson") text: String, injectionAvailable: Boolean) {
        myFixture.configureByText(KsonFileType, text.trimIndent())

        val intentions = myFixture.availableIntentions
        val injectIntention = intentions.find {
            it.text.contains("Inject language", ignoreCase = true) || it.familyName.contains(
                "Inject language",
                ignoreCase = true
            )
        }
        if (injectionAvailable) {
            assertNotNull("should have 'Inject language' intention available", injectIntention)
        } else {
            assertNull("should not have 'Inject language' intention available", injectIntention)
        }
    }

    /**
     * Assert that code completion produces the expected result.
     *
     * @param before The KSON text before completion, with <caret> marking the completion position
     * @param after The expected KSON text after completion
     */
    private fun hasCorrectAutoCompletion(
        @Language("kson") before: String,
        @Language("kson") after: String
    ) {
        myFixture.configureByText(KsonFileType, before.trimIndent())
        myFixture.complete(CompletionType.BASIC)
        myFixture.checkResult(after.trimIndent())
    }

    /**
     * Tests that language completion is available and contains expected languages.
     *
     * @param text The KSON text to test with
     * @param inputTag: Tag that will be typed in the editor
     * @param expectedLanguageId A language ID that must be present in completion results
     */
    private fun hasLanguageListCompletionAvailable(
        @Language("kson") text: String,
        inputTag: String,
        expectedLanguageId: String
    ) {
        myFixture.configureByText(KsonFileType, text.trimIndent())

        myFixture.type(inputTag)
        myFixture.complete(CompletionType.BASIC)
        val lookupElements = myFixture.lookupElements

        assertNotNull("Should have completion variants available", lookupElements)

        val completionTexts = lookupElements!!.map { it.lookupString }
        assertTrue(
            "should contain '$expectedLanguageId', but not found in completion list: $completionTexts",
            completionTexts.contains(expectedLanguageId)
        )
    }

    fun testAutomaticLanguageInjection() {
        val language = XMLLanguage.INSTANCE
        val injectedContent =
            """
        |    should be injected xml content
        |    with a newline
        """.trimMargin()
        val fileContent =
            """
            |$$${language.id}
            |$injectedContent$$
            """.trimMargin()
        hasInjectionPresent(
            fileContent,
            InjectionAssertionData(injectedContent, language.id)
        )
    }

    fun testLanguageInjectionActionAvailable() {

        hasLanguageInjectionAvailable(
            """
            key: %%custom
            {<caret>
                "key": "should have injection within `EMBED_CONTENT`"
            }
            %%
        """, true
        )

        hasLanguageInjectionAvailable(
            """
            key: %%<caret>custom
            {
                "key": "should not have injection outside `EMBED_CONTENT`"
            }
            %%
        """, false
        )

        hasLanguageInjectionAvailable(
            """
            key:<caret> %%custom
            {
               "key": "should not have injection outside of `EMBED_CONTENT`"
            }
            %%
        """, false
        )

        hasLanguageInjectionAvailable(
            """
            key: %%html
            {<caret>
                "key": "should not have injection for already injected language"
            }
            %%
        """, false
        )
    }


    fun testFragmentEditorUnescapesOriginalText() {
        for (delim in listOf(EmbedDelim.Dollar, EmbedDelim.Percent)) {
            val fileContent =
                """
                |${delim.delimiter}${XMLLanguage.INSTANCE.id}
                |${delim.char}\${delim.char}<caret>
                |${delim.char}\\${delim.char}
                |${delim.char}\\\${delim.char}
                |${delim.delimiter}
                """.trimMargin()

            hasCorrectTextEditors(
                text = fileContent,
                expectedTextInFragmentEditor =
                    """
                    |${delim.char}${delim.char}
                    |${delim.char}\${delim.char}
                    |${delim.char}\\${delim.char}
                    |
                    """.trimMargin(),
                actions = listOf(),
                expectedTextInMainAfterActions = fileContent.replace("<caret>", ""),
            )
        }
    }

    fun testFragmentEditorCorrectIndent() {
        val fileContent =
            """
                |key: %%xml
                |  <div>
                |    <caret>should not have indent in fragment editor
                |  </div>
                |  %%
                """.trimMargin()

        hasCorrectTextEditors(
            text = fileContent,
            expectedTextInFragmentEditor =
                """
                    |<div>
                    |  should not have indent in fragment editor
                    |</div>
                    |
                    """.trimMargin(),
            actions = listOf(),
            expectedTextInMainAfterActions = fileContent.replace("<caret>", ""),
        )

    }

    fun testFragmentEditorPreservesIndent() {
        val fileContent =
            """
                |key: %%xml
                |  indent of two<caret>
                |  %%
                """.trimMargin()

        hasCorrectTextEditors(
            text = fileContent,
            actions = listOf(
                ActionInEditor.EditorAction(IdeActions.ACTION_EDITOR_ENTER),
                ActionInEditor.TypeText("should still have indent of two after typing in fragment editor")
            ),
            expectedTextInFragmentEditor =
                """
                |indent of two
                |
            """.trimMargin(),
            expectedTextInMainAfterActions =
                """
                    |key: %%xml
                    |  indent of two
                    |  should still have indent of two after typing in fragment editor
                    |  %%
                    """.trimMargin(),
        )
    }

    fun testFragmentEditorPreservesIndentBeginningOfLine() {
        for (delim in listOf(EmbedDelim.Dollar, EmbedDelim.Percent)) {
            val fileContent =
                """
                |key: ${delim.delimiter}${XMLLanguage.INSTANCE.id}
                |  
                |  should keep indent of two after typing in fragment editor<caret>
                |   ${delim.delimiter}
                """.trimMargin()

            hasCorrectTextEditors(
                text = fileContent,
                expectedTextInFragmentEditor =
                    """
                        |
                        |should keep indent of two after typing in fragment editor
                        | 
                    """.trimMargin(),
                actions = listOf(
                    ActionInEditor.EditorAction(IdeActions.ACTION_EDITOR_ENTER),
                    ActionInEditor.TypeText("should still have indent of two after typing in fragment editor")
                ),
                expectedTextInMainAfterActions =
                    """
                    |key: ${delim.delimiter}${XMLLanguage.INSTANCE.id}
                    |  
                    |  should keep indent of two after typing in fragment editor
                    |  should still have indent of two after typing in fragment editor
                    |   ${delim.delimiter}
                    """.trimMargin(),
            )
        }
    }

    fun testFragmentEditorCorrectEscaping() {
        for (delim in listOf(EmbedDelim.Dollar, EmbedDelim.Percent)) {
            val fileContent =
                """
                |${delim.delimiter}${XMLLanguage.INSTANCE.id}
                |<caret><xml>${delim.delimiter}
            """.trimMargin()

            hasCorrectTextEditors(
                text = fileContent,
                expectedTextInFragmentEditor = "<xml>",
                actions = listOf(
                    ActionInEditor.TypeText("${delim.char}${delim.char}\n"),
                    ActionInEditor.TypeText("${delim.char}\\${delim.char}\n"),
                    ActionInEditor.TypeText("${delim.char}\\\\${delim.char}\n"),
                ),
                expectedTextInMainAfterActions =
                    """
                    |${delim.delimiter}${XMLLanguage.INSTANCE.id}
                    |${delim.char}\${delim.char}
                    |${delim.char}\\${delim.char}
                    |${delim.char}\\\${delim.char}
                    |<xml>${delim.delimiter}
                    """.trimMargin(),
            )
        }
    }

    fun testLanguageListCompletion() {
        val fileContent = """
            key: %%<caret>
            %%
        """.trimIndent()

        hasLanguageListCompletionAvailable(fileContent, "j", "json")
        hasLanguageListCompletionAvailable(fileContent, "h", "html")
    }

    fun testDeleteAllTextInFragmentEditor() {
        val fileContent =
            """
                |%%xml
                |  <div><caret>
                |    <p>Some content to delete</p>
                |  </div>
                |  %%
                """.trimMargin()

        hasCorrectTextEditors(
            text = fileContent,
            expectedTextInFragmentEditor =
                """
                    |<div>
                    |  <p>Some content to delete</p>
                    |</div>
                    |
                """.trimMargin(),
            actions = listOf(
                ActionInEditor.EditorAction(IdeActions.ACTION_SELECT_ALL),
                ActionInEditor.EditorAction(IdeActions.ACTION_EDITOR_DELETE)
            ),
            expectedTextInMainAfterActions =
                """
                    |%%xml
                    |%%
                    """.trimMargin(),
        )
    }


    fun testTypeOnLastLineInFragmentEditor() {
        val fileContent =
            """
                |key: %%xml
                |  <div><caret>
                |    <p>Current caret</p>
                |  </div>
                |  %%
                """.trimMargin()

        hasCorrectTextEditors(
            text = fileContent,
            expectedTextInFragmentEditor = """
                |<div>
                |  <p>Current caret</p>
                |</div>
                |
                """.trimMargin(),
            actions = listOf(
                ActionInEditor.EditorAction(IdeActions.ACTION_EDITOR_TEXT_END),
                ActionInEditor.TypeText("new text")
            ),
            expectedTextInMainAfterActions =
                """
                    |key: %%xml
                    |  <div>
                    |    <p>Current caret</p>
                    |  </div>
                    |  new text%%
                    """.trimMargin()
        )
    }

    fun testReplaceTextInFragmentEditor() {
        val fileContent =
            """
                |key: %%xml
                |  <div><caret>
                |    <p>Current caret</p>
                |  </div>
                |  %%
                """.trimMargin()

        hasCorrectTextEditors(
            text = fileContent,
            expectedTextInFragmentEditor = """
                |<div>
                |  <p>Current caret</p>
                |</div>
                |
                """.trimMargin(),
            actions = listOf(
                ActionInEditor.EditorAction(IdeActions.ACTION_SELECT_ALL),
                ActionInEditor.EditorAction(IdeActions.ACTION_EDITOR_DELETE),
                ActionInEditor.TypeText("<div>new content</div>")
            ),
            expectedTextInMainAfterActions =
                """
                    |key: %%xml
                    |  <div>
                    |    <p>Current caret</p>
                    |  </div>
                    |  new text%%
                    """.trimMargin()
        )

    }


    fun testEnterInMainEditor() {
        val fileContent =
            """
                |key: %%
                |  <div><caret></div>
                |  %%
                |
                """.trimMargin()


        hasCorrectTextEditors(
            text = fileContent,
            actions = listOf(
                ActionInEditor.EditorAction(IdeActions.ACTION_EDITOR_ENTER),
            ),
            expectedTextInMainAfterActions =
                """
                    |key: %%
                    |  <div>
                    |  
                    |  </div>
                    |  %%
                    """.trimMargin(),
        )
    }

    fun testAutoCompleteXmlFirstLine() {
        hasCorrectAutoCompletion(
            before = """
                |key: %%xml
                |  <analyze-<caret>
                |  %%
                """.trimMargin(),
            after = """
                |key: %%xml
                |  <analyze-string xmlns="http://www.w3.org/1999/XSL/Transform"
                |  %%
                """.trimMargin()
        )
    }

    fun testAutoCompleteXmlLastLine() {
        hasCorrectAutoCompletion(
            before = """
                |key: %%xml
                |  
                |  <analyze-<caret>
                |  %%
                """.trimMargin(),
            after = """
                |key: %%xml
                |  
                |  <analyze-string xmlns="http://www.w3.org/1999/XSL/Transform"
                |  %%
                """.trimMargin()
        )
    }
}

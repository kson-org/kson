package org.kson.jetbrains.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import org.kson.jetbrains.file.KsonFileType

/**
 * Base test for plugin editor actions<br/>
 * <br/>
 * In general, all the tests here work on the same principles: run an action on the given "before" text and compare
 * the result to the given "expected" text.<br/>
 * <br/>
 * Both "before" and "expected" text must include substring "&lt;caret&gt;" to indicate the caret position in the text.<br/>
 */
abstract class KsonEditorActionTest : BasePlatformTestCase() {
    /**
     * [CodeInsightSettings.getInstance] is globally mutable in the tests, and so must be treated careful and always
     * restored to its previous state after changes in a particular test.  This class, together with [withConfigSetting]
     * try to make that easy to get right.
     *
     * More config settings can/should be added here as they are needed for testing.
     *
     * @param getValue a function which returns the current value the config property
     * @param setValue a function which sets the value of the config property
     */
    enum class ConfigProperty(private val getValue: () -> Boolean, private val setValue: (Boolean) -> Unit) {
        AUTOINSERT_PAIR_QUOTE(
            { CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE },
            { CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = it }),
        AUTOINSERT_PAIR_BRACKET(
            { CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET },
            { CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = it });

        /**
         * Set the [ConfigProperty] to the given [Boolean], returning its previous/original value after the set
         * (to facilitate restoring that previous value, as in [withConfigSetting])
         *
         * @return the original value before setting to the given [value].  Useful for restoring the previous setting later.
         */
        fun set(value: Boolean): Boolean {
            val originalValue = getValue()
            runInEdtAndWait {
                ApplicationManager.getApplication().runWriteAction {
                    setValue(value)
                }
            }
            return originalValue
        }
    }

    /**
     * Aspect-style helper for temporarily setting a [ConfigProperty] setting in a test.  This method ensures
     * the original setting is restored when it exits.
     *
     * @param configProperty the property to set
     * @param valueForTestLambda the value to set for [configProperty] in preparation for executing [testLambda]
     * @param testLambda the test code to execute with [configProperty] set to [valueForTestLambda]
     */
    fun withConfigSetting(configProperty: ConfigProperty, valueForTestLambda: Boolean, testLambda: () -> Unit) {
        val originalValue = configProperty.set(valueForTestLambda)
        testLambda()
        configProperty.set(originalValue)
    }

    /**
     * Call this method to test behavior when the "Comment with Line Comment" action is executed.
     * See class documentation for more info: [KsonEditorActionTest]
     */
    fun doLineCommentTest(before: String, expected: String) {
        doExecuteActionTest(before, expected) {
            CommentByLineCommentAction().actionPerformedImpl(
                myFixture.project, myFixture.editor
            )
        }
    }

    /**
     * Call this method to test behavior when the given [charToType] is typed at the &lt;caret&gt;.
     * See class documentation for more info: [KsonEditorActionTest]
     */
    fun doCharTest(before: String, charToType: Char, expected: String) {
        val typedAction = TypedAction.getInstance()
        doExecuteActionTest(
            before, expected
        ) {
            typedAction.actionPerformed(myFixture.editor, charToType, (myFixture.editor as EditorEx).dataContext)
        }
    }

    /**
     * Call this method to test behavior when the given [com.intellij.openapi.actionSystem.IdeActions] is performed
     * at &lt;caret&gt;. See class documentation for more info: [KsonEditorActionTest]
     *
     * @param before the file contents before the action is executed
     * @param ideActionId one of the ideActionIds enumerated in [com.intellij.openapi.actionSystem.IdeActions]
     * @param expected the expected file contents after the actions is executed
     */
    fun doIdeActionTest(before: String, ideActionId: String, expected: String) {
        validateTestStrings(before, expected)
        myFixture.configureByText(KsonFileType, before)
        myFixture.performEditorAction(ideActionId)
        myFixture.checkResult(expected)
    }

    private fun doExecuteActionTest(
        before: String, expected: String, action: Runnable
    ) {
        validateTestStrings(before, expected)
        myFixture.configureByText(KsonFileType, before)
        performWriteAction(myFixture.project, action)
        myFixture.checkResult(expected)
    }

    private fun performWriteAction(project: Project, action: Runnable) {
        ApplicationManager.getApplication().runWriteAction {
            CommandProcessor.getInstance().executeCommand(project, action, "Test Command", null)
        }
    }

    private fun validateTestStrings(before: String, expected: String) {
        require(
            !(!before.contains("<caret>") || !expected.contains("<caret>"))
        ) { "Test strings must contain \"<caret>\" to indicate caret position" }
    }
}
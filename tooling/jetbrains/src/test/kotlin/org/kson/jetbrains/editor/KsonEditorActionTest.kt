package org.kson.jetbrains.editor

import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
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
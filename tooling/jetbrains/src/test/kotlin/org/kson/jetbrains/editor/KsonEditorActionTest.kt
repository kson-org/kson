package org.kson.jetbrains.editor

import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
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
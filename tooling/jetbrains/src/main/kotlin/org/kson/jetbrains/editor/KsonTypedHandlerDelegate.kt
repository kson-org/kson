package org.kson.jetbrains.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.kson.jetbrains.KsonLanguage

class KsonTypedHandlerDelegate : TypedHandlerDelegate() {
    override fun charTyped(char: Char, project: Project, editor: Editor, file: PsiFile): Result {
        // this handler runs on typing events in all filetypes, so
        // be careful to return quickly if this event isn't for us
        if (!file.viewProvider.baseLanguage.isKindOf(KsonLanguage)) {
            return Result.CONTINUE
        }

        val caretOffset = editor.caretModel.offset

        if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && caretOffset > 0) {
            if (char == '<') {
                editor.document.insertString(caretOffset, ">")
            }
        }

        return super.charTyped(char, project, editor, file)
    }
}
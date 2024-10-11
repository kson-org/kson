package org.kson.jetbrains.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class KsonBackspaceHandlerDelegate : BackspaceHandlerDelegate() {
    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        val caretOffset = editor.caretModel.offset
        val text = editor.document.text

        // handle deleting quote pairs
        if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE
            && caretOffset > 0 && text.length > caretOffset) {
            if (text[caretOffset - 1] == '"' && text[caretOffset] == '"'
                || text[caretOffset - 1] == '\'' && text[caretOffset] == '\'') {
                editor.document.deleteString(caretOffset, caretOffset + 1)
            }
        }
    }

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        return false
    }
}
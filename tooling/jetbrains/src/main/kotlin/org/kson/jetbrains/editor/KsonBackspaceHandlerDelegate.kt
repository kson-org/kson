package org.kson.jetbrains.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.kson.jetbrains.KsonLanguage

class KsonBackspaceHandlerDelegate : BackspaceHandlerDelegate() {
    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        // this handler runs on all backspace events in all filetypes, so
        // be careful to return quickly if this event isn't for us
        if (!file.viewProvider.baseLanguage.isKindOf(KsonLanguage)) {
            return
        }

        val caretOffset = editor.caretModel.offset
        val text = editor.document.text

        if (caretOffset > 0 && text.length > caretOffset) {
            // handle deleting quote pairs
            if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE
                && (text[caretOffset - 1] == '"' && text[caretOffset] == '"'
                || text[caretOffset - 1] == '\'' && text[caretOffset] == '\'')) {
                editor.document.deleteString(caretOffset, caretOffset + 1)
            }

            // handle deleting angle bracket pairs
            if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET
                && text[caretOffset - 1] == '<' && text[caretOffset] == '>') {
                editor.document.deleteString(caretOffset, caretOffset + 1)
            }
        }
    }

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        return false
    }
}
package org.kson.jetbrains.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.parser.elem
import org.kson.jetbrains.util.getIndentType
import org.kson.jetbrains.util.getLineIndentLevel
import org.kson.jetbrains.util.hasElementAtOffset
import org.kson.parser.behavior.embedblock.EmbedDelim
import org.kson.parser.TokenType.*

class KsonBackspaceHandlerDelegate : BackspaceHandlerDelegate() {
    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        // this handler runs on all backspace events in all filetypes, so
        // be careful to return quickly if this event isn't for us
        if (!file.viewProvider.baseLanguage.isKindOf(KsonLanguage)) {
            return
        }

        val caretOffset = editor.caretModel.offset
        val document = editor.document
        val text = document.text

        if (caretOffset > 0 && text.length > caretOffset) {
            /**
             * handle deleting [STRING_OPEN_QUOTE]/[STRING_CLOSE_QUOTE] pairs
             */
            if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE
                && (text[caretOffset - 1] == '"' && text[caretOffset] == '"'
                || text[caretOffset - 1] == '\'' && text[caretOffset] == '\'')) {
                document.deleteString(caretOffset, caretOffset + 1)
                return
            }

            if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
                /**
                 * handle deleting [ANGLE_BRACKET_L]/[ANGLE_BRACKET_R] pairs
                 */
                if (text[caretOffset - 1] == '<' && text[caretOffset] == '>') {
                    document.deleteString(caretOffset, caretOffset + 1)
                    return
                }

                /**
                 * handle deleting [EMBED_OPEN_DELIM]/[EMBED_CLOSE_DELIM] pairs
                 */
                for (embedDelimChar in listOf(EmbedDelim.Percent.char, EmbedDelim.Dollar.char)) {
                    if (caretOffset > 1 && text[caretOffset - 2] == embedDelimChar && text[caretOffset - 1] == embedDelimChar) {
                        if (text[caretOffset] != '\n') {
                             // if we're not at the end of the line, then we're definitely not part of an auto-inserted
                             // pair, so no auto-deletion
                            return
                        }

                        // ensure we're not contained in an element where auto-deleting embed block delimiter
                        // would be inappropriate
                        if (file.hasElementAtOffset(caretOffset - 1, embedDeleteProhibitedElems)) {
                            return
                        }

                        val project = file.project
                        val nextLine = document.getLineNumber(caretOffset) + 1
                        val nextLineStartOffset = document.getLineStartOffset(nextLine)
                        val nextLineEndOffset = document.getLineEndOffset(nextLine)
                        val nextLineText = document.getText(TextRange(nextLineStartOffset, nextLineEndOffset))

                        // Calculate the indent level of the inserted new line
                        val indentType = file.getIndentType()
                        val indentLevel = document.getLineIndentLevel(nextLine, indentType)
                        val newIndent = indentType.indentString.repeat(indentLevel)

                        /**
                         * If the next line looks like an embed delimiter inserted by [KsonTypedHandlerDelegate],
                         * clean it up along with the current backspace deletion
                         */
                        val toBeDeletedIfFound = "${newIndent}$embedDelimChar$embedDelimChar"
                        val toBeDeletedIfFoundWithComma = "$toBeDeletedIfFound,"
                        if (nextLineText == toBeDeletedIfFound
                            || nextLineText == toBeDeletedIfFoundWithComma) {
                            CommandProcessor.getInstance().executeCommand(project,{
                                ApplicationManager.getApplication().runWriteAction {
                                    val deletionOffsetInLine = nextLineStartOffset + toBeDeletedIfFound.length
                                    document.deleteString(nextLineStartOffset - 1, deletionOffsetInLine)
                                }
                            }, null, null)
                        }
                    }
                }
            }
        }
    }

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        return false
    }
}

/**
 * The [IElementType]s within which we do NOT want to perform [EMBED_OPEN_DELIM]/[EMBED_CLOSE_DELIM] delimiter auto-deletes
 */
private val embedDeleteProhibitedElems = setOf(elem(EMBED_CONTENT), elem(STRING_CONTENT))

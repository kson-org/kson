package org.kson.jetbrains.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.parser.elem
import org.kson.jetbrains.util.hasElementAtOffset
import org.kson.parser.EmbedDelim
import org.kson.parser.ParsedElementType.EMBED_BLOCK
import org.kson.parser.TokenType.*

class KsonTypedHandlerDelegate : TypedHandlerDelegate() {
    override fun charTyped(char: Char, project: Project, editor: Editor, file: PsiFile): Result {
        // this handler runs on typing events in all filetypes, so
        // be careful to return quickly if this event isn't for us
        if (!file.viewProvider.baseLanguage.isKindOf(KsonLanguage)) {
            return Result.CONTINUE
        }

        val caretOffset = editor.caretModel.offset
        val document = editor.document
        val text = document.text

        if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && caretOffset > 0) {
            /**
             * Handle auto-inserts of [ANGLE_BRACKET_L]/[ANGLE_BRACKET_R] pairs
             */
            if (char == '<') {
                document.insertString(caretOffset, ">")
                return Result.CONTINUE
            }

            /**
             * Handle auto-inserts of [EMBED_OPEN_DELIM]/[EMBED_CLOSE_DELIM] pairs
             */
            if (text.length > 1 && caretOffset > 1) {
                for (embedDelimChar in listOf(EmbedDelim.Percent.char, EmbedDelim.Dollar.char)) {
                    if (char == embedDelimChar && text[caretOffset - 2] == embedDelimChar) {
                        // let's be very conservative with this insert: if we are not at the end of a line,
                        //     or followed by a comma (which is end of line-ish), don't do it
                        if (text.length != caretOffset
                            && text[caretOffset] != '\n'
                            && text[caretOffset] != ',') {
                            return Result.CONTINUE
                        }

                        // ensure we're not contained in an element where embed block delimiter
                        // auto-insertions would be inappropriate
                        if (file.hasElementAtOffset(caretOffset - 2, embedInsertProhibitedElems)) {
                            return Result.CONTINUE
                        }

                        val openDelimLinePosition = editor.caretModel.logicalPosition.column - 2
                        document.insertString(caretOffset, "\n${" ".repeat(openDelimLinePosition)}$embedDelimChar$embedDelimChar")
                        return Result.CONTINUE
                    }
                }
            }
        }

        return Result.CONTINUE
    }
}

/**
 * The [IElementType]s within which we do NOT want to perform [EMBED_OPEN_DELIM]/[EMBED_CLOSE_DELIM] auto-competes
 */
private val embedInsertProhibitedElems = setOf(elem(EMBED_BLOCK), elem(EMBED_CONTENT), elem(STRING))

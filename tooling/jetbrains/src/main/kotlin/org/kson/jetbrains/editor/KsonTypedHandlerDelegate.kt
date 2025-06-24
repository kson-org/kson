package org.kson.jetbrains.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.childrenOfType
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.parser.elem
import org.kson.jetbrains.util.getIndentType
import org.kson.jetbrains.util.getLineIndentLevel
import org.kson.jetbrains.util.hasElementAtOffset
import org.kson.parser.behavior.embedblock.EmbedDelim
import org.kson.parser.ParsedElementType.EMBED_BLOCK
import org.kson.parser.TokenType.*

class KsonTypedHandlerDelegate : TypedHandlerDelegate() {
    override fun charTyped(char: Char, project: Project, editor: Editor, file: PsiFile): Result {
        // this handler runs on typing events in all filetypes, so
        // be careful to return quickly if this event isn't for us
        if (!file.viewProvider.baseLanguage.isKindOf(KsonLanguage)) {
            return Result.CONTINUE
        }

        /** If our file contains an error we immediately continue
         * Indirectly this prevents auto-insertion when we have a dangling '>' or [EMBED_CLOSE_DELIM]
         **/
        if (file.childrenOfType<PsiErrorElement>().isNotEmpty()) {
            return Result.CONTINUE
        }

        val caretOffset = editor.caretModel.offset
        val document = editor.document
        val text = document.text

        /**
         * Position before the just-typed character, used for:
         * 1. Detecting paired delimiters (%% or $$) by matching with the just-typed char
         * 2. Checking if we're in a comment (since the PSI tree hasn't updated yet for the new character we check
         *      whether the previous character is a comment)
         */
        val preTypedPosition = caretOffset - 2

        // ensure we're not contained in an element where we NEVER want to
        // auto-complete (e.g. COMMENTS).
        if (file.hasElementAtOffset(preTypedPosition, autoCompleteProhibitedElems)) {
            return Result.CONTINUE
        }

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
                    if (char == embedDelimChar && text[preTypedPosition] == embedDelimChar) {
                        // let's be very conservative with this insert: if we are not at the end of a line,
                        //     or followed by a comma (which is end of line-ish), don't do it
                        if (text.length != caretOffset
                            && text[caretOffset] != '\n'
                            && text[caretOffset] != ',') {
                            return Result.CONTINUE
                        }

                        // ensure we're not contained in an element where embed block delimiter
                        // auto-insertions would be inappropriate
                        if (file.hasElementAtOffset(preTypedPosition, embedInsertProhibitedElems)) {
                            return Result.CONTINUE
                        }

                        // Insert the new line on which we auto-complete the [EmbedDelim]
                        document.insertString(caretOffset, "\n")

                        // Calculate the indent level of the inserted new line
                        val newLineCaret = caretOffset + 1
                        val lineNumber = document.getLineNumber(newLineCaret)
                        val indentType = file.getIndentType()
                        val indentLevel = document.getLineIndentLevel(lineNumber, indentType)
                        val newIndent = indentType.indentString.repeat(indentLevel)

                        // Insert the embed delimiters with the calculated indent
                        document.insertString(newLineCaret, "${newIndent}$embedDelimChar$embedDelimChar")
                        return Result.CONTINUE
                    }
                }
            }
        }

        return Result.CONTINUE
    }
}

/**
 * The [IElementType]s within which we do NOT want to perform [EMBED_OPEN_DELIM]/[EMBED_CLOSE_DELIM] auto-completes
 */
private val embedInsertProhibitedElems = setOf(elem(EMBED_BLOCK), elem(EMBED_CONTENT), elem(STRING_CONTENT))

/**
 * The [IElementType]s within which we NEVER want to perform auto-completes
 */
private val autoCompleteProhibitedElems = setOf(elem(COMMENT))

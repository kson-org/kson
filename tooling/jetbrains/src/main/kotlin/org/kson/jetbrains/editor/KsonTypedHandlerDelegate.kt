package org.kson.jetbrains.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.parser.elem
import org.kson.jetbrains.util.getIndentType
import org.kson.jetbrains.util.getLineIndentLevel
import org.kson.jetbrains.util.hasElementAtOffset
import org.kson.parser.behavior.embedblock.EmbedDelim
import org.kson.parser.ParsedElementType.EMBED_BLOCK
import org.kson.parser.TokenType.*

class KsonTypedHandlerDelegate : TypedHandlerDelegate() {
    override fun beforeCharTyped(
        char: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
        fileType: FileType
    ): Result {
        // this handler runs on typing events in all filetypes, so
        // be careful to return quickly if this event isn't for us
        if (!file.viewProvider.baseLanguage.isKindOf(KsonLanguage)) {
            return Result.CONTINUE
        }

        val caretOffset = editor.caretModel.offset
        val document = editor.document

        val nextChar = findNextChar(caretOffset, document.text)
        // ensure we're not contained in an element where we NEVER want to auto-complete (e.g. COMMENTS and STRINGS).
        if (file.hasElementAtOffset(caretOffset - 1, autoCompleteProhibitedElems)) {
            return Result.CONTINUE
        }

        if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            when (char) {
                /**
                 * Handle auto-inserts of [ANGLE_BRACKET_L]/[ANGLE_BRACKET_R] pairs
                 */
                '<' -> {
                    if (nextChar != '>') {
                        document.insertString(caretOffset, ">")
                    }
                    return Result.CONTINUE
                }

                /**
                 * Handle auto-inserts of [EMBED_OPEN_DELIM]/[EMBED_CLOSE_DELIM] pairs
                 */
                EmbedDelim.Dollar.char, EmbedDelim.Percent.char -> {
                    // let's be very conservative with this insert: if we are not at the end of a line,
                    //     or followed by a comma (which is end of line-ish), don't do it
                    if (nextChar != '\n' && nextChar != ',') {
                        return Result.CONTINUE
                    }

                    // ensure we're not contained in an element where embed block delimiter
                    // auto-insertions would be inappropriate
                    if (file.hasElementAtOffset(caretOffset - 1, embedInsertProhibitedElems)) {
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
                    val embedDelim = EmbedDelim.fromString(char.toString())
                    document.insertString(newLineCaret, "${newIndent}${embedDelim.closeDelimiter}")
                    return Result.CONTINUE
                }
            }
        }
        return Result.CONTINUE
    }
}

/**
 * Look ahead for the closing delimiter, skipping whitespace
 */
private fun findNextChar(caretOffset: Int, text: String): Char {
    if (caretOffset == text.length){
        return '\n'
    }
    // Look ahead for the closing delimiter, skipping whitespace
    val nextChar = {
        var afterIndex = caretOffset

        while (afterIndex < text.length - 1 && text[afterIndex].isWhitespace() && text[afterIndex] != '\n') {
            afterIndex++
        }

        text[afterIndex]
    }

    return nextChar()
}

/**
 * The [IElementType]s within which we do NOT want to perform [EMBED_OPEN_DELIM]/[EMBED_CLOSE_DELIM] auto-completes
 */
private val embedInsertProhibitedElems = setOf(elem(EMBED_BLOCK), elem(EMBED_CONTENT))

/**
 * The [IElementType]s within which we NEVER want to perform auto-completes
 */
private val autoCompleteProhibitedElems = setOf(elem(COMMENT), elem(STRING_CONTENT), elem(STRING_OPEN_QUOTE))

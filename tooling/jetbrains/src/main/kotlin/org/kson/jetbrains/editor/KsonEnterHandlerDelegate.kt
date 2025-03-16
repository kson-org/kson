package org.kson.jetbrains.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import org.kson.tools.IndentType

class KsonEnterHandlerDelegate : EnterHandlerDelegate {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): Result {
        val document = editor.document
        val text = document.text

        if (caretOffset.get() > 0 && caretOffset.get() < text.length) {
            val before = text[caretOffset.get() - 1]
            
            // Look ahead for the closing delimiter, skipping whitespace
            var afterIndex = caretOffset.get()
            while (afterIndex < text.length && text[afterIndex].isWhitespace()) {
                afterIndex++
                if (text[afterIndex] == '\n') {
                    // only search until the next line for a non-whitespace char
                    break
                }
            }
            val after = if (afterIndex < text.length) text[afterIndex] else null

            // Check if we're between matching delimiters
            if ((before == '{' && after == '}') ||
                (before == '[' && after == ']') ||
                (before == '<' && after == '>')
            ) {
                // Remove any whitespace between the delimiters
                if (afterIndex > caretOffset.get()) {
                    document.deleteString(caretOffset.get(), afterIndex)
                }

                val indentOptions = CodeStyle.getIndentOptions(file)
                val indentType = if (indentOptions.USE_TAB_CHARACTER) {
                    IndentType.Tab()
                } else {
                    IndentType.Space(indentOptions.INDENT_SIZE)
                }
                val indentString = indentType.indentString

                // Get current line's indentation
                val lineNumber = document.getLineNumber(caretOffset.get())
                val lineStartOffset = document.getLineStartOffset(lineNumber)
                val currentIndent = document.text.substring(lineStartOffset, caretOffset.get()).takeWhile { it.isWhitespace() }

                // Insert the properly formatted content with correct indentation
                val insertText = "\n$currentIndent$indentString\n$currentIndent"
                document.insertString(caretOffset.get(), insertText)

                // Move caret to the end of the new indented line
                val newOffset = caretOffset.get() + currentIndent.length + indentString.length + 1
                editor.caretModel.moveToOffset(newOffset)

                return Result.Stop
            }
        }

        return Result.Continue
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): Result {
        return Result.Continue
    }
}

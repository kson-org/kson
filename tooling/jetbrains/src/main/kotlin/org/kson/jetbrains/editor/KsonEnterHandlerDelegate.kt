package org.kson.jetbrains.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.util.getIndentType
import org.kson.jetbrains.util.getLineIndentLevel

class KsonEnterHandlerDelegate : EnterHandlerDelegate {
    override fun preprocessEnter(
        file: PsiFile, editor: Editor, caretOffset: Ref<Int>, caretAdvance: Ref<Int>, dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): Result {
        val offset = caretOffset.get().toInt()
        if (editor !is EditorWindow) {
            return preprocessEnter(file, editor, offset)
        }

        val hostPosition = getHostPosition(dataContext) ?: return Result.Continue
        return preprocessEnter(hostPosition)
    }

    private fun preprocessEnter(
        hostPosition: HostPosition,
    ): Result {
        val (file, editor, offset) = hostPosition
        return preprocessEnter(file, editor, offset)
    }


    @Suppress("SameReturnValue") // Only need to return `Continue` right now.
    private fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        offset: Int,
    ): Result {
        if (!file.language.isKindOf(KsonLanguage)) {
            return EnterHandlerDelegate.Result.Continue
        }
        val document = editor.document
        val text = document.text

        if (offset > 0 && offset < text.length) {
            val before = text[offset - 1]

            var afterIndex = offset

            /**
             * Works around the the newline IntelliJ auto-inserts on ENTER at a '{' that messes with the
             * indent formatting we want to perform
             * todo: figure out if we can disable IntelliJ's built-in handling and handle this
             *   consistently with `{}` and `<>` (note: I spent some time on this and found it non-obvious
             *   and rabbit-holey. To save future folks time, just wanted to note: so as long as this workaround
             *   is working, this can likely be left alone)
             */
            if (before == '{' && text[afterIndex] == '\n') {
                afterIndex++
            }

            // Look ahead for the closing delimiter, skipping whitespace
            while (afterIndex < text.length - 1 && text[afterIndex].isWhitespace() && text[afterIndex] != '\n') {
                afterIndex++
            }

            val after = text[afterIndex]

            /**
             * If we're between matching delimiters, this ENTER event will put the cursor on a new indented line
             * between the two delimiters.  For instance, `[<caret>]` becomes:
             *
             * ```
             * [
             *   <caret>
             * ]
             * ```
             */
            if ((before == '{' && after == '}') ||
                (before == '[' && after == ']') ||
                (before == '<' && after == '>') ||
                // TODO this is to help indenting tags in injected xml-like languages, which should not be our
                //   responsibility, but it seems Kotlin has the same workaround [https://github.com/JetBrains/intellij-community/blob/4d2499e460bd6ab6425de24517d0050b65a78f99/plugins/kotlin/base/code-insight/minimal/src/org/jetbrains/kotlin/idea/editor/KotlinMultilineStringEnterHandler.kt#L82].
                //   Can we figure out how to delegate to the injected language?
                (before == '>' && after == '<')
            ) {
                // Remove any whitespace we found between the delimiters
                if (afterIndex > offset) {
                    document.deleteString(offset, afterIndex)
                }

                // now put position the cursor on its line and indent it
                document.insertString(offset, "\n")
                val nextLineOffset = editor.document.getLineStartOffset(editor.document.getLineNumber(offset) + 1)
                editor.caretModel.moveToOffset(nextLineOffset)
                indentCaretLine(editor, file)
            }
        }
        return Result.Continue
    }

    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result {
        if (editor !is EditorWindow) {
            return postProcessEnter(file, editor)
        }

        val hostPosition = getHostPosition(dataContext) ?: return Result.Continue
        return postProcessEnter(hostPosition.file, hostPosition.editor)
    }

    @Suppress("SameReturnValue") // Only need to return `Continue` right now.
    private fun postProcessEnter(file: PsiFile, editor: Editor): Result {
        if (!file.language.isKindOf(KsonLanguage)) {
            return EnterHandlerDelegate.Result.Continue
        }

        indentCaretLine(editor, file)

        return Result.Continue
    }

    private fun indentCaretLine(editor: Editor, file: PsiFile) {
        val document = editor.document
        val caretModel = editor.caretModel
        val caretOffset = caretModel.offset
        val lineNumber = document.getLineNumber(caretOffset)

        // Delete any leading whitespace on the current line
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStart, lineEndOffset))
        val leadingWhitespaceLength = lineText.takeWhile { it.isWhitespace() }.length
        if (leadingWhitespaceLength > 0) {
            document.deleteString(lineStart, lineStart + leadingWhitespaceLength)
        }

        // Calculate the indent level
        val indentType = file.getIndentType()
        val indentAdjustment = document.getLineIndentLevel(lineNumber, indentType)
        val newIndent = indentType.indentString.repeat(indentAdjustment)

        // Insert the calculated indent
        document.insertString(lineStart, newIndent)
    }

    private data class HostPosition(val file: PsiFile, val editor: Editor, val offset: Int)

    private fun getHostPosition(dataContext: DataContext): HostPosition? {
        val editor = CommonDataKeys.HOST_EDITOR.getData(dataContext) as? EditorEx ?: return null
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null

        val virtualFile = editor.virtualFile ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null

        return HostPosition(psiFile, editor, editor.caretModel.offset)
    }
}

package org.kson.jetbrains.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import org.kson.tools.IndentFormatter
import org.kson.tools.IndentType

/**
 * Calculates the indentation level of a specific line in the document.
 *
 * @param lineNumber The line number to analyze (0-based)
 * @param indentType The type of indentation to consider (e.g., spaces or tabs)
 * @return The number of indentation levels for the specified line
 */
fun Document.getLineIndentLevel(lineNumber: Int, indentType: IndentType): Int {
    val prevLine = if (lineNumber > 0) {
        val lineStart = this.getLineStartOffset(lineNumber - 1)
        val lineEnd = this.getLineEndOffset(lineNumber - 1)
        this.getText(TextRange(lineStart, lineEnd))
    } else {
        ""
    }

    val lineStart = this.getLineStartOffset(lineNumber)
    val lineEnd = this.getLineEndOffset(lineNumber)
    val currentLine = this.getText(TextRange(lineStart, lineEnd))

    return IndentFormatter(indentType).getCurrentLineIndentLevel(prevLine, currentLine)
}
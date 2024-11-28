package org.kson.jetbrains.util

import com.intellij.openapi.editor.Document

/**
 * Convert the given [offset] in this [Document] to its position within its line of the [Document],
 * i.e. the number of [Char]s between the left margin and [offset]
 */
fun Document.getLinePosition(offset: Int): Int {
    val lineNumber = this.getLineNumber(offset)
    val lineStartOffset = this.getLineStartOffset(lineNumber)
    val linePosition = offset - lineStartOffset
    return linePosition
}
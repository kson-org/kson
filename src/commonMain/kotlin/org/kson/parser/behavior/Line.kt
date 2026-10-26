package org.kson.parser.behavior

import org.kson.parser.behavior.Whitespace.isInlineWhitespace

/**
 * A line of text split into its content and line ending.
 */
data class Line(val text: String, val ending: String) {
    fun isInlineWhitespaceOnly(): Boolean = text.all { isInlineWhitespace(it) }
    fun leadingInlineWhitespaceWidth(): Int = text.takeWhile { isInlineWhitespace(it) }.length

    companion object {
        /**
         * 1 if this represents a newline, 2 if this represents a CRLF, 0 otherwise.
         */
        fun endingLength(char: Char?, next: Char?): Int {
            return when {
                char == '\n' -> 1
                char == '\r' && next == '\n' -> 2
                else -> 0
            }
        }

        fun splitToLines(text: String): List<Line> {
            val parts = text.split("\n")
            return parts.mapIndexed { index, part ->
                when {
                    index == parts.lastIndex -> Line(part, "")
                    part.endsWith('\r') -> Line(part.dropLast(1), "\r\n")
                    else -> Line(part, "\n")
                }
            }
        }
    }
}

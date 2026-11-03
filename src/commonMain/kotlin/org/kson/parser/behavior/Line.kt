package org.kson.parser.behavior

import org.kson.parser.behavior.Whitespace.isInlineWhitespace

/**
 * KSON recognizes line endings `\n` (NEWLINE) and `\r\n` (CRLF).
 */
enum class LineEnding(val text: String) {
    NONE(""),
    NEWLINE("\n"),
    CRLF("\r\n");

    val length: Int get() = text.length
    val isEnding: Boolean get() = NONE != this

    companion object {
        /**
         * The line ending starting at [char], given the character after it is [next]
         */
        fun at(char: Char?, next: Char?): LineEnding {
            return when {
                char == '\n' -> NEWLINE
                char == '\r' && next == '\n' -> CRLF
                else -> NONE
            }
        }
    }
}

/**
 * A line of text split into its content and line ending.
 */
data class Line(val text: String, val ending: LineEnding) {
    fun isInlineWhitespaceOnly(): Boolean = text.all { isInlineWhitespace(it) }
    fun leadingInlineWhitespaceWidth(): Int = text.takeWhile { isInlineWhitespace(it) }.length

    companion object {
        fun splitToLines(text: String): List<Line> {
            val parts = text.split("\n")
            return parts.mapIndexed { index, part ->
                when {
                    index == parts.lastIndex -> Line(part, LineEnding.NONE)
                    part.endsWith('\r') -> Line(part.dropLast(1), LineEnding.CRLF)
                    else -> Line(part, LineEnding.NEWLINE)
                }
            }
        }
    }
}

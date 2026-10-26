package org.kson.parser.behavior

object Whitespace {
    /**
     * Space or tab.
     *
     * Note: `\r` is not inline whitespace.
     */
    fun isInlineWhitespace(char: Char?): Boolean {
        return char == ' ' || char == '\t'
    }

    /**
     * Space, tab, \r, \n. This matches JSON.
     */
    fun isWhitespace(char: Char?): Boolean {
        return isInlineWhitespace(char) || char == '\n' || char == '\r'
    }
}

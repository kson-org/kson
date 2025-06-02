package org.kson.parser.behavior

/**
 * Behaviors and rules around unquoted Kson strings
 */
object StringUnquoted {

    private val reservedKeywords = setOf("true", "false", "null")

    /**
     * Returns true if the given string may be used without quotes in a Kson document
     */
    fun isUnquotable(str: String): Boolean {
        return !reservedKeywords.contains(str) && str.isNotBlank() && str.withIndex().all { (index, letter) ->
            if (index == 0) {
                isUnquotedStartChar(letter)
            } else {
                isUnquotedBodyChar(letter)
            }
        }
    }

    /**
     * Returns true if [ch] is a legal first [Char] for an unquoted Kson string
     */
    fun isUnquotedStartChar(ch: Char?): Boolean {
        ch ?: return false
        return ch.isLetter() || (ch == '_')
    }

    /**
     * Returns true if [ch] is a legal [Char] for the body of an unquoted Kson string
     */
    fun isUnquotedBodyChar(ch: Char?): Boolean {
        ch ?: return false
        return isUnquotedStartChar(ch) || ch.isDigit()
    }
}
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
     *
     * This rule is stated to users by [org.kson.parser.messages.UNQUOTED_STRING_START_RULE]---if this
     * rule ever changes, that statement must change with it
     */
    fun isUnquotedStartChar(ch: Char?): Boolean {
        ch ?: return false
        return ch.isLetter() || (ch == '_')
    }

    /**
     * Returns true if [ch] is a legal [Char] for the body of an unquoted Kson string
     *
     * Note that `-` is legal in the body but not as a start [Char], where it would
     * clash with list dashes and negative numbers
     */
    fun isUnquotedBodyChar(ch: Char?): Boolean {
        ch ?: return false
        return isUnquotedStartChar(ch) || ch.isDigit() || ch == '-'
                // combining marks are body-only characters since they attach to the character before them and
                // cannot meaningfully start a string
                || isCombiningMark(ch)
    }

    /**
     * Returns true if [ch] is a combining mark used to spell words: a character that renders
     * attached to the character before it, such as the vowel signs and virama of Devanagari or
     * Arabic diacritics. We support these in unquoted strings as part of trying to holistically
     * support non-ASCII alphabets, similar to the rationale for allowed
     * variable names in Python (see https://peps.python.org/pep-3131)
     *
     * We admit the nonspacing (Mn) and spacing (Mc) mark categories, matching Unicode's
     * identifier rules (UAX #31 ID_Continue, https://www.unicode.org/reports/tr31/).
     * Enclosing marks (Me) are deliberately excluded, also per UAX #31: they decorate
     * symbols rather than spell words.
     */
    private fun isCombiningMark(ch: Char): Boolean {
        val category = ch.category
        return category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK
    }
}

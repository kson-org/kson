package org.kson.parser.behavior

/**
 * Represents a string delimiter in KSON, which can be either `'` or `"`, along with helper methods to
 * escape/unescape [quoteChar] in [String]s
 *
 * Escaping rules: as a superset of Json, Kson's String escaping rules for delimiters work
 *   the same as JSON's rules for escaping backslashes and double-quotes in a string, with the wrinkle that
 *   Kson supports single-quoted strings, in which case the escaping rules are identical but with
 *   respect to single-quote `'` rather than double-quote `"`
 */
sealed class StringQuote(val quoteChar: Char) {

    private val delimiterString = quoteChar.toString()
    private val escapedDelimiterString = "\\" + quoteChar

    /** Single-quote delimiter ('), our "primary" delimiter */
    object SingleQuote : StringQuote('\'')

    /** Double-quote delimiter ("), our "alternate" delimiter */
    object DoubleQuote : StringQuote('"')

    /**
     * Counts the number of occurrences of this [quoteChar] in the given [rawContent]
     */
    fun countDelimiterOccurrences(rawContent: String): Int {
        return rawContent.count { it == quoteChar }
    }

    /**
     * Perform any needed [quoteChar] escapes on this string [rawContent]
     *
     * @param rawContent a "raw" string that has [quoteChar] delimiters that may be unescaped (other escapes are
     *     ignored)
     * @return a copy of [rawContent] with every bare [quoteChar] escaped; existing escape sequences (including
     *     already-escaped quotes) are copied through unchanged
     */
    fun escapeQuotes(rawContent: String): String {
        val sb = StringBuilder(rawContent.length)

        var i = 0
        while (i < rawContent.length) {
            val char = rawContent[i]
            if (char == '\\' && i + 1 < rawContent.length) {
                // an existing escape sequence: copy it through as a unit
                sb.append(char).append(rawContent[i + 1])
                i += 2
            } else {
                if (char == quoteChar) {
                    sb.append('\\')
                }
                sb.append(char)
                i++
            }
        }

        return sb.toString()
    }

    /**
     * Process [quoteChar] escapes in string content delimited by this [StringQuote]
     *
     * @param escapedContent an "escaped" string where delimiters are already escaped (other escapes are ignored)
     * @return a copy of [escapedContent] with all delimiter escapes processed
     */
    fun unescapeQuotes(escapedContent: String): String {
        // unescape any escaped internal quotes
        return escapedContent.replace(escapedDelimiterString, delimiterString)
    }

    override fun toString(): String {
        return delimiterString
    }

    companion object {
        fun fromChar(delimString: Char): StringQuote {
            return when (delimString) {
                '\'' -> SingleQuote
                '"' -> DoubleQuote
                else -> throw UnsupportedOperationException("Unknown string delimiter: $delimString")
            }
        }
    }
} 

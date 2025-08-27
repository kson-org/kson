package org.kson.parser.behavior

/**
 * Represents a string delimiter in KSON, which can be either `'` or `"`, along with helper methods to
 * escape/unescape [quoteChar] in [String]s
 *
 * Escaping rules: as a superset of Json, Kson's String escaping rules for delimiters work
 *   the same as JSON's rules for escaping slashes and double-quotes in a string, with the wrinkle that
 *   Kson supports single-quoted  strings, in which case the escaping rules are identical but with
 *   respect to single-quote `'` rather than double-quote `"`
 */
sealed class StringQuote(private val quoteChar: Char) {

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
     * @param rawContent a "raw" string that has NO escaped delimiters (other escapes are ignored)
     * @return a copy of [rawContent] with all [quoteChar]s escaped
     */
    fun escapeQuotes(rawContent: String): String {
        return rawContent.replace(delimiterString, escapedDelimiterString)
    }

    /**
     * Process [quoteChar] escapes in string content delimited by this [StringQuote]
     *
     * @param escapedContent an "escaped" string where delimiters are already escaped (other escapes are ignored)
     * @return a copy of [escapedContent] with all delimiter escapes processed
     */
    fun unescapeQuotes(escapedContent: String): String {
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

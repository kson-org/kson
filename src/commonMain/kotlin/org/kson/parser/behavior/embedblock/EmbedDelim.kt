package org.kson.parser.behavior.embedblock

import org.kson.parser.TokenType.EMBED_CLOSE_DELIM

/**
 * Represents an embed delimiter in KSON, which starts with an[openDelimiter], a single $ or % character, and ends
 * with a [closeDelimiter], the same character doubled — $$ or %%.
 *
 * Embed blocks are designed to allow other tools to confidently extract their contents as simply as possible,
 * so a naive regex looking for the closing delimiter of either %% or $$ will always be correct.  But of course,
 * we also need to accommodate escaping embedded delimiters, so to accomplish both these goals, escaping an
 * embed delimiter within an embed is slightly novel/intricate.  So (explained in terms of `%%`, `$$` naturally works
 * the same), here's how they work:
 *
 * Escaping: an escaped [EMBED_CLOSE_DELIM] has its second percent char escaped: "%\%" yields a literal
 *   "%%" inside of an embed. Note that this moves the escaping goalpost since we also need to allow %\% literally
 *   inside of embeds.  So: when evaluating escaped `%%`s, we allow arbitrary `\`s before the second
 *   %, and consume one of them.  Then, %\\% gives %\% in the output, %\\\% gives %\\% in the output, etc
 */
sealed class EmbedDelim(val char: Char) {
    /** The full open delimiter string (either "%" or "$") */
    val openDelimiter: Char = char
    /** The full close delimiter string (either "%%" or "$$") */
    val closeDelimiter: String = "$char$char"

    private val delimCharForRegex = Regex.escapeReplacement("$char")

    /**
     * This regex matches strings that need to be escaped for including in a [closeDelimiter] delimited embed block
     * The pattern also matches zero or more trailing slashes ensures that in a situation like `%\%\%`, we correctly
     * identify that the leading `%\%` needs escaping, not the second
     *
     * See [EmbedDelim] class doc for details on embed delimiter escaping
     */
    private val needsEscapesPattern = "$delimCharForRegex\\\\*$delimCharForRegex\\\\*".toRegex()

    /**
     * This regex matches string that are escaped according to the embed block escape rules.
     * The pattern also matches zero or more trailing slashes ensures that in a situation like `%\%\%`, we correctly
     * identify that the leading `%\%` needs unescaping, not the second
     *
     * NOTE: this pattern captures the slashes which must be re-inserted as the first group.
     *   The code in [escapeEmbedContent] relies on this fact—we tolerate this coupling so we
     *   cn cache this compiled [Regex] here
     *
     * See [EmbedDelim] class doc for details on embed delimiter escaping
     */
    private val hasEscapesPattern = "$delimCharForRegex\\\\([\\\\]*)$delimCharForRegex\\\\*".toRegex()

    /** Percent-style delimiter (%, %%), our "primary" delimiter */
    object Percent : EmbedDelim('%')

    /** Dollar-style delimiter ($, $$), our "alternate" delimiter */
    object Dollar : EmbedDelim('$')

    /**
     * Counts the number of occurrences of a delimiter in the content,
     * including escaped delimiters. A delimiter is considered escaped if it has
     * one or mores backslashes between its two characters (e.g. %\%, %\\\%, $\$, etc.)
     */
    fun countDelimiterOccurrences(content: String): Int {
        return needsEscapesPattern.findAll(content).count()
    }

    /**
     * Returns a list of indices where delimiters are escaped in the content.
     * For each escaped delimiter like %\%, returns the index of the first character.
     */
    fun findEscapedDelimiterIndices(content: String): List<Int> {
        return needsEscapesPattern.findAll(content)
            .map { it.range.first }
            .toList()
    }


    /**
     * Perform any needed escapes on this embed content
     */
    fun escapeEmbedContent(content: String): String {
        return content.replace(needsEscapesPattern) { matchResult ->
            // For each match, insert an extra backslash between the delimiter chars
            val match = matchResult.value
            match[0] + match.substring(1, match.length - 1) + "\\" + match.last()
        }
    }

    /**
     * Process escapes in embed content delimiter by this [EmbedDelim]. See the doc on [EmbedDelim] more for details on
     * the escaping rules at work here.
     */
    fun unescapeEmbedContent(content: String): String {
        /**
         * Note: we rely on the structure of [hasEscapesPattern] here, in particular how it
         *   groups in $1 the slashes which must be maintained between escaped delim chars we find
         */
        return content.replace(hasEscapesPattern, "$delimCharForRegex\$1$delimCharForRegex")
    }

    override fun toString(): String {
        return char.toString()
    }

    companion object {
        fun fromString(delimString: String): EmbedDelim {
            return when (delimString) {
                "%" -> Percent
                "$" -> Dollar
                else -> throw UnsupportedOperationException("Unknown embed delimiter string: $delimString")
            }
        }
    }
}

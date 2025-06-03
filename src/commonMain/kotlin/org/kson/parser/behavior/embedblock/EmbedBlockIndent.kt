package org.kson.parser.behavior.embedblock


/**
 * This class represents the behavior for handling embedded block content
 * by parsing and trimming its minimum indentation.
 *
 * @property rawEmbedContent The raw embedded block content as a string
 *                        to be analyzed or transformed.
 */
class EmbedBlockIndent(embedContent: String) {
    private val rawEmbedContent: String = embedContent

    /**
     * Computes the minimum indent of all lines in [rawEmbedContent], then returns
     * the text with that indent trimmed from each line.
     *
     * NOTE: blank lines are considered pure indent and used in this calculation, so for instance:
     *
     * "   this string
     *         has a minimum indent defined
     *       by its last line
     *    "
     *
     * becomes:
     *
     * "  this string
     *      has a minimum indent defined
     *    by its blank last line
     * "
     */
    fun trimMinimumIndent(): String {
        val minCommonIndent = computeMinimumIndent()

        return rawEmbedContent
            .split("\n")
            .joinToString("\n") { it.drop(minCommonIndent) }
    }

    /**
     * Computes the minimum indent in a [rawEmbedContent].
     *
     * NOTE: blank lines are considered pure indent and used in this calculation, so for instance:
     *
     * "   this string
     *         has a minimum indent defined
     *       by its last line
     *    "
     *
     * returns: 2
     */
    fun computeMinimumIndent(): Int {
        val linesWithNewlines = rawEmbedContent.split("\n").map { it + "\n" }

        val minCommonIndent =
            linesWithNewlines.minOfOrNull { it.indexOfFirst { char -> !isInlineWhitespace(char) } } ?: 0

        return minCommonIndent
    }

    /**
     * Returns true if the given [char] is a non-newline whitespace
     */
    private fun isInlineWhitespace(char: Char?): Boolean {
        return char == ' ' || char == '\r' || char == '\t'
    }
}
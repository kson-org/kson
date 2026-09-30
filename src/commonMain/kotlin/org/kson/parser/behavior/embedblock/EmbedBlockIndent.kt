package org.kson.parser.behavior.embedblock

/**
 * The indentation behavior of embed block content: the value of an embed block is its source content
 * with the content's minimum indent trimmed away, see [trimmedContent].
 *
 * @param embedContentSource The complete content of an embed block from its KSON source: everything after the newline
 *   that ends the block's preamble, up to but not including the closing delimiter.
 */
class EmbedBlockIndent(embedContentSource: String) {
    private val lines: List<String> = embedContentSource.split("\n")

    /**
     * The minimum indent of the content, measured across every line that carries content plus
     * the final line, which is the closing delimiter's line.
     *
     * Blank content lines (empty or inline-whitespace-only) are not considered when computing the
     * minimum indent since they can't visually convey their indent and text editors usually auto-trim them
     * on save, so they end up being confusing in practice. They _are_ trimmed based on the computed indent
     * though, so blank lines longer than the computed indent retain any whitespace that "reaches in" to
     * the content; it is naturally considered part of the content.
     *
     * NOTE: the last "line" always contributes indent even if it is blank in this string because that
     * blank line ends with the end-delimiter, which can and does visually convey indent, and must
     * be allowed to contribute a minimum indent to encode for instance indented text such as:
     *
     * %
     *     This embed content contains its own internal indent that it wishes to keep.
     *     The end delimiter defines the minimum indent, preserving this content's indentation
     * %%
     */
    val minimumIndent: Int by lazy {
        lines.withIndex()
            .filter { (index, line) -> index == lines.lastIndex || !isInlineWhitespaceOnly(line) }
            .minOfOrNull { (_, line) -> leadingInlineWhitespaceWidth(line) } ?: 0
    }

    /**
     * How many leading characters are trimmed from each line, in line order, to produce [trimmedContent]:
     * [minimumIndent] for every line that has at least that much leading whitespace, which is every
     * line that carries content, and all of its whitespace for a blank line shorter than that.
     */
    val trimmedIndentPerLine: List<Int> by lazy {
        lines.map { line -> minOf(minimumIndent, line.length) }
    }

    /**
     * The content with [minimumIndent] trimmed from the start of every line.  See [minimumIndent] for
     * how the minimum is determined and [trimmedIndentPerLine] for what each line loses.
     */
    val trimmedContent: String by lazy {
        lines.zip(trimmedIndentPerLine)
            .joinToString("\n") { (line, trimmedWidth) -> line.substring(trimmedWidth) }
    }

    private fun isInlineWhitespaceOnly(line: String): Boolean = leadingInlineWhitespaceWidth(line) == line.length

    private fun leadingInlineWhitespaceWidth(line: String): Int = line.takeWhile { isInlineWhitespace(it) }.length

    /**
     * Returns true if the given [char] is a non-newline whitespace
     */
    private fun isInlineWhitespace(char: Char): Boolean {
        return char == ' ' || char == '\r' || char == '\t'
    }
}

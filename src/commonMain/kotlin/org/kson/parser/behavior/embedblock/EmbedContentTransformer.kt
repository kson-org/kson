package org.kson.parser.behavior.embedblock

import org.kson.parser.Location
import org.kson.parser.behavior.KsonContentTransformer
import org.kson.parser.behavior.Line

/**
 * A [KsonContentTransformer] for Embed Blocks, handling the processing from raw KSON source to actual String
 * value, and maintaining a [Location] source-map back
 *
 * @param rawContent The raw embed content from the original KSON document
 * @param embedDelim The delimiter used to delimit [rawContent] (needed to perform unescaping)
 * @param rawLocation Where rawContent exists in the original KSON document
 */
class EmbedContentTransformer(
    rawContent: String,
    embedDelim: EmbedDelim,
    rawLocation: Location
) : KsonContentTransformer(rawContent, rawLocation) {

    override val processedContent: String

    /**
     * [trimmedIndentPerLine], [trimmedEmbedContent] and [sortedEscapeOffsets] are all the state needed to
     * perform source mapping from [processedContent] back to [rawContent]
     */
    private val trimmedIndentPerLine: List<Int>

    /**
     * [rawContent] with its indent trimmed and the closing delimiter's newline stripped, but with its
     * escapes still in place: the content [processedContent] is unescaped from
     */
    private val trimmedEmbedContent: String

    /**
     * The offsets in [trimmedEmbedContent] of the escape backslashes that unescaping removes, ascending
     */
    private val sortedEscapeOffsets: List<Int>

    init {
        /**
         * Transformation pipeline:
         * 1. Trim the minimum indent
         * 2. If %% was on its own line, drop everything after the end of the prior line
         * 3. Unescape the content
         */
        val indentTrimmer = EmbedBlockIndent(rawContent)
        trimmedIndentPerLine = indentTrimmer.trimmedIndentPerLine
        val indentTrimmed = indentTrimmer.trimmedContent

        trimmedEmbedContent = if (isCloseDelimOnOwnLine(rawContent)) {
            val lines = Line.splitToLines(indentTrimmed)
            val lineBeforeCloseDelim = lines[lines.lastIndex - 1]
            val closeDelimLine = lines.last()
            indentTrimmed.removeSuffix(lineBeforeCloseDelim.ending + closeDelimLine.text)
        } else {
            indentTrimmed
        }

        sortedEscapeOffsets = embedDelim.findEscapePositions(trimmedEmbedContent).toList()
        processedContent = embedDelim.unescapeEmbedContent(trimmedEmbedContent)
    }

    /**
     * Checks whether the close delimiter (%% or $$) is on its own line in the raw content.
     * This is true when the content has more than one line and its last line is only inline whitespace.
     */
    private fun isCloseDelimOnOwnLine(content: String): Boolean {
        val lines = Line.splitToLines(content)
        return lines.size > 1 && lines.last().isInlineWhitespaceOnly()
    }

    /**
     * Maps a single offset in processed content to an offset in raw content by running the
     * transformation pipeline backwards:
     * 1. Add back removed escape backslashes
     * 2. Add back trimmed indentation (per line)
     */
    override fun mapProcessedOffsetToRawOffset(processedOffset: Int): Int {
        // Step 1: Map processed → trimmed (add back escape backslashes)
        val trimmedOffset = mapProcessedOffsetToTrimmedOffset(processedOffset)

        // Step 2: Map trimmed → raw (add back trimmed indent)
        val trimmedUpToOffset = trimmedEmbedContent.take(trimmedOffset)
        val lineIndex = Line.splitToLines(trimmedUpToOffset).lastIndex
        // Every line up to and including the offset's own line had its indent trimmed, so we add back
        // the sum of all the trimmed characters (we go line by line to make sure our count is
        // accurate since a blank line shorter than the minimum indent loses fewer characters than the rest)
        val indentAdjustment = trimmedIndentPerLine.take(lineIndex + 1).sum()
        val rawOffset = trimmedOffset + indentAdjustment

        return rawOffset
    }

    /**
     * Maps an offset in [processedContent] to an offset in [trimmedEmbedContent].
     *
     * For each escape backslash that was removed:
     * - Determine where it would have appeared in processed content
     * - If before our target position, add 1 to the offset
     */
    private fun mapProcessedOffsetToTrimmedOffset(processedOffset: Int): Int {
        var shift = 0
        for (trimmedEscapePos in sortedEscapeOffsets) {
            // Where does this escape appear after removing previous escapes?
            val processedEscapePos = trimmedEscapePos - shift
            if (processedEscapePos < processedOffset) {
                shift++
            } else {
                break
            }
        }
        return processedOffset + shift
    }
}

package org.kson.parser.behavior.embedblock

import org.kson.parser.Location
import org.kson.parser.behavior.KsonContentTransformer

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
     * [sortedEscapeOffsets] and [minIndent] are all the state needed to perform source mapping
     * from [processedContent] back to [rawContent]
     */
    private val sortedEscapeOffsets: List<Int> = embedDelim.findEscapePositions(rawContent).toList()
    private val minIndent: Int

    init {
        /**
         * Transformation pipeline:
         * 1. Unescape the content
         * 2. Trim the minimum indent
         */
        val unescapedContent = embedDelim.unescapeEmbedContent(rawContent)
        val indentTrimmer = EmbedBlockIndent(unescapedContent)
        minIndent = indentTrimmer.computeMinimumIndent()
        processedContent = indentTrimmer.trimMinimumIndent()
    }


    /**
     * Maps a single offset in processed content to an offset in raw content.
     *
     * Reverse transformation pipeline:
     * 1. Add back trimmed indentation (per line)
     * 2. Add back removed escape backslashes
     */
    override fun mapProcessedOffsetToRawOffset(processedOffset: Int): Int {
        // Step 1: Map processed → unescaped (add back trimmed indent)
        val processedUpToOffset = processedContent.take(processedOffset)
        val lineBreaks = processedUpToOffset.count { it == '\n' }
        // Each line (including the first) had minIndent characters removed
        val indentAdjustment = (lineBreaks + 1) * minIndent
        val unescapedOffset = processedOffset + indentAdjustment

        // Step 2: Map unescaped → raw (add back escape backslashes)
        val rawOffset = mapUnescapedOffsetToRawOffset(unescapedOffset)

        return rawOffset
    }

    /**
     * Maps an offset in unescaped content to an offset in raw content.
     *
     * For each escape backslash that was removed:
     * - Determine where it would have appeared in unescaped content
     * - If before our target position, add 1 to the offset
     */
    private fun mapUnescapedOffsetToRawOffset(unescapedOffset: Int): Int {
        var shift = 0
        for (rawEscapePos in sortedEscapeOffsets) {
            // Where does this escape appear after removing previous escapes?
            val unescapedEscapePos = rawEscapePos - shift
            if (unescapedEscapePos < unescapedOffset) {
                shift++
            } else {
                break
            }
        }
        return unescapedOffset + shift
    }
}

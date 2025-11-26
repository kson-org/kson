package org.kson.parser.behavior.embedblock

import org.kson.parser.Coordinates
import org.kson.parser.Location

/**
 * [EmbedContentTransformer] is responsible for taking [rawContent] (embed content source code directly from a source
 * KSON file) and evaluating all escapes and performing all embed indent stripping to transform it into the
 * [processedContent] value that this [rawContent] represents.
 *
 * In addition to this [rawContent] -> [processedContent] transformation, [EmbedContentTransformer] maintains the
 * state needed to act as a source map from [processedContent] -> [rawContent] allowing [Location]s relative
 * to [processedContent] to be mapped back their [Location]s in [rawContent] (and hence the source KSON document).
 * This is key for bubbling messages up from sub-parsers that may log messages on the embedded content up to
 * the original source for that embedded content
 *
 * @param rawContent The raw embed content from the original KSON document
 * @param embedDelim The delimiter used to delimit [rawContent] (needed to perform unescaping)
 * @param rawLocation Where rawContent exists in the original KSON document
 */
class EmbedContentTransformer(
    private val rawContent: String,
    embedDelim: EmbedDelim,
    private val rawLocation: Location
) {
    // The processed content after all transformations
    val processedContent: String

    /**
     * [sortedEscapeOffsets] and [minIndent] are all the state needed to perform source mapping
     * from [processedContent] back to [rawContent]
     */
    private val sortedEscapeOffsets: List<Int> = embedDelim.findEscapePositions(rawContent).toList()
    private val minIndent: Int

    init {
        val unescapedContent = embedDelim.unescapeEmbedContent(rawContent)
        val indentTrimmer = EmbedBlockIndent(unescapedContent)
        minIndent = indentTrimmer.computeMinimumIndent()
        processedContent = indentTrimmer.trimMinimumIndent()
    }


    /**
     * Maps a position range in the processed content to a [Location] in the original KSON source.
     *
     * @param processedStart Character offset in processedContent (0-based)
     * @param processedEnd Character offset in processedContent (0-based, exclusive)
     * @return Location in the original KSON source document
     */
    fun mapToOriginal(processedStart: Int, processedEnd: Int): Location {
        // Step 1: Map offsets through the transformation pipeline (reverse)
        val rawStart = mapProcessedOffsetToRawOffset(processedStart)
        val rawEnd = mapProcessedOffsetToRawOffset(processedEnd)

        // Step 2: Compute line/column coordinates within rawContent
        val startCoordsInRaw = computeCoordinatesInString(rawContent, rawStart)
        val endCoordsInRaw = computeCoordinatesInString(rawContent, rawEnd)

        // Step 3: Add these offsets to the base location's coordinates
        val finalStart = addCoordinates(rawLocation.start, startCoordsInRaw)
        val finalEnd = addCoordinates(rawLocation.start, endCoordsInRaw)

        // Step 4: Compute absolute character offsets in the original document
        val finalStartOffset = rawLocation.startOffset + rawStart
        val finalEndOffset = rawLocation.startOffset + rawEnd

        return Location(finalStart, finalEnd, finalStartOffset, finalEndOffset)
    }

    /**
     * Maps a position range specified as line/column coordinates in the processed content to a Location in the
     * original KSON source.
     *
     * @param startLine Line number in processedContent (0-based)
     * @param startColumn Column number in processedContent (0-based)
     * @param endLine Line number in processedContent (0-based)
     * @param endColumn Column number in processedContent (0-based)
     * @return Location in the original KSON source document
     */
    fun mapToOriginal(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int): Location {
        val processedStart = computeOffsetFromCoordinates(processedContent, startLine, startColumn)
        val processedEnd = computeOffsetFromCoordinates(processedContent, endLine, endColumn)
        return mapToOriginal(processedStart, processedEnd)
    }

    /**
     * Maps a single offset in processed content to an offset in raw content.
     *
     * Reverse transformation pipeline:
     * 1. Add back trimmed indentation (per line)
     * 2. Add back removed escape backslashes
     */
    private fun mapProcessedOffsetToRawOffset(processedOffset: Int): Int {
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

    /**
     * Computes line/column coordinates for a given offset within a string.
     */
    private fun computeCoordinatesInString(text: String, offset: Int): Coordinates {
        val upToOffset = text.take(offset)
        val line = upToOffset.count { it == '\n' }
        val lastNewline = upToOffset.lastIndexOf('\n')
        val column = if (lastNewline == -1) offset else offset - lastNewline - 1
        return Coordinates(line, column)
    }

    /**
     * Computes a character offset from line/column coordinates within a string.
     */
    private fun computeOffsetFromCoordinates(text: String, line: Int, column: Int): Int {
        var currentLine = 0
        var offset = 0

        while (currentLine < line && offset < text.length) {
            if (text[offset] == '\n') {
                currentLine++
            }
            offset++
        }

        return offset + column
    }

    /**
     * Adds coordinate offsets to a base coordinate
     */
    private fun addCoordinates(base: Coordinates, offset: Coordinates): Coordinates {
        return if (offset.line == 0) {
            // No change to the line, so just add columns
            Coordinates(base.line, base.column + offset.column)
        } else {
            // Offset puts us on a later line, so offset.column is the absolute column on that line
            Coordinates(base.line + offset.line, offset.column)
        }
    }
}

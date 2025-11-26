package org.kson.parser.behavior

import org.kson.parser.Coordinates
import org.kson.parser.Location

/**
 * [KsonContentTransformer] is responsible for taking [rawContent] (content directly from a source
 * KSON file) and performing transformations (such as unescaping and/or indent stripping) to transform
 * it into the [processedContent] value that this [rawContent] represents.
 *
 * In addition to this [rawContent] -> [processedContent] transformation, [KsonContentTransformer] maintains the
 * state needed to act as a source map from [processedContent] -> [rawContent] allowing [Location]s relative
 * to [processedContent] to be mapped back their [Location]s in [rawContent] (and hence the source KSON document).
 * This is key for bubbling messages up from sub-parsers that may log messages on the embedded content up to
 * the original source for that embedded content.
 *
 * @param rawContent The raw content from the original KSON document
 * @param rawLocation Where rawContent exists in the original KSON document
 */
abstract class KsonContentTransformer(
    protected val rawContent: String,
    protected val rawLocation: Location
) {
    /**
     * The processed content after all transformations
     */
    abstract val processedContent: String

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
     * Subclasses implement this to handle their specific transformation logic
     * (e.g., escaping, indent trimming, or both).
     */
    protected abstract fun mapProcessedOffsetToRawOffset(processedOffset: Int): Int

    /**
     * Computes line/column coordinates for a given offset within a string.
     */
    protected fun computeCoordinatesInString(text: String, offset: Int): Coordinates {
        val upToOffset = text.take(offset)
        val line = upToOffset.count { it == '\n' }
        val lastNewline = upToOffset.lastIndexOf('\n')
        val column = if (lastNewline == -1) offset else offset - lastNewline - 1
        return Coordinates(line, column)
    }

    /**
     * Computes a character offset from line/column coordinates within a string.
     */
    protected fun computeOffsetFromCoordinates(text: String, line: Int, column: Int): Int {
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
    protected fun addCoordinates(base: Coordinates, offset: Coordinates): Coordinates {
        return if (offset.line == 0) {
            // No change to the line, so just add columns
            Coordinates(base.line, base.column + offset.column)
        } else {
            // Offset puts us on a later line, so offset.column is the absolute column on that line
            Coordinates(base.line + offset.line, offset.column)
        }
    }
}

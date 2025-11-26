package org.kson.parser.behavior.quotedstring

import org.kson.parser.Location
import org.kson.parser.behavior.KsonContentTransformer

/**
 * A [KsonContentTransformer] for quoted KSON Strings, handling the processing from raw KSON source to actual String
 * value, and maintaining a [Location] source-map back
 *
 * @param rawContent The raw quoted string content from the original KSON document (without surrounding quotes)
 * @param rawLocation Where rawContent exists in the original KSON document
 */
class QuotedStringContentTransformer(
    rawContent: String,
    rawLocation: Location
) : KsonContentTransformer(rawContent, rawLocation) {
    // The processed content after all transformations
    override val processedContent: String

    /**
     * [escapeInfoList] contains information about all escape sequences in [rawContent].
     * This is all the state needed to perform source mapping from [processedContent] back to [rawContent].
     */
    private val escapeInfoList: List<EscapeInfo>

    init {
        val (unescapedContent, escapes) = unescapeAndTrackEscapes(rawContent)
        processedContent = unescapedContent
        escapeInfoList = escapes
    }

    /**
     * Maps a single offset in processed content to an offset in raw content.
     *
     * For each escape sequence that was processed:
     * - Determine where it appears in the processed content
     * - If before our target position, add the difference between raw and processed length
     */
    override fun mapProcessedOffsetToRawOffset(processedOffset: Int): Int {
        var shift = 0

        for (escape in escapeInfoList) {
            // Calculate where this escape appears in processed content
            // It appears at its raw position minus the cumulative shift from previous escapes
            val escapeProcessedPos = escape.rawPosition - shift

            if (escapeProcessedPos < processedOffset) {
                // This escape is before our target position, so we need to account for it
                // The escape took 'rawLength' chars in raw but 'processedLength' chars in processed
                shift += (escape.rawLength - escape.processedLength)
            } else {
                break
            }
        }

        return processedOffset + shift
    }
}

/**
 * Information about an escape sequence in the raw content.
 * @param rawPosition The position in rawContent where the backslash starts
 * @param rawLength The total length of the escape sequence in rawContent (e.g., "\n" is 2, "\u0041" is 6)
 * @param processedLength The length of the resulting character(s) in processedContent (usually 1, but can be 2 for surrogate pairs)
 */
private data class EscapeInfo(
    val rawPosition: Int,
    val rawLength: Int,
    val processedLength: Int
)

@Deprecated("Only supports testing to ensure behavior after a refactor. Will be removed.")
fun unescapeStringContent(content: String): String {
    return unescapeAndTrackEscapes(content).first
}

/**
 * Unescapes the content and tracks all escape sequences for source mapping.
 * Returns a pair of (unescaped content, list of escape info).
 */
private fun unescapeAndTrackEscapes(content: String): Pair<String, List<EscapeInfo>> {
    val sb = StringBuilder(content.length)
    val escapes = mutableListOf<EscapeInfo>()
    var i = 0

    while (i < content.length) {
        val char = content[i]

        if (char == '\\' && i + 1 < content.length) {
            val rawStart = i
            when (val escaped = content[i + 1]) {
                '"', '\\', '/', '\'' -> {
                    sb.append(escaped)
                    escapes.add(EscapeInfo(rawStart, 2, 1))
                    i += 2
                }
                'b' -> {
                    sb.append('\b')
                    escapes.add(EscapeInfo(rawStart, 2, 1))
                    i += 2
                }
                'f' -> {
                    sb.append('\u000C')
                    escapes.add(EscapeInfo(rawStart, 2, 1))
                    i += 2
                }
                'n' -> {
                    sb.append('\n')
                    escapes.add(EscapeInfo(rawStart, 2, 1))
                    i += 2
                }
                'r' -> {
                    sb.append('\r')
                    escapes.add(EscapeInfo(rawStart, 2, 1))
                    i += 2
                }
                't' -> {
                    sb.append('\t')
                    escapes.add(EscapeInfo(rawStart, 2, 1))
                    i += 2
                }
                'u' -> {
                    val (chars, consumed) = handleUnicodeEscape(content.substring(i))
                    for (c in chars) {
                        sb.append(c)
                    }
                    escapes.add(EscapeInfo(rawStart, consumed, chars.size))
                    i += consumed
                }
                else -> {
                    // Unknown escape sequence, append backslash as is
                    sb.append(char)
                    i++
                }
            }
        } else {
            sb.append(char)
            i++
        }
    }

    return Pair(sb.toString(), escapes)
}

/**
 * Handles Unicode escape sequences including surrogate pairs.
 *
 * @param input the string containing the Unicode escape starting with \u
 * @return Pair of (characters produced, characters consumed from input)
 */
private fun handleUnicodeEscape(input: String): Pair<CharArray, Int> {
    // Check if we have enough characters for a Unicode escape (\uXXXX = 6 chars)
    if (input.length < 6) {
        // Not enough characters for a valid Unicode escape
        return Pair(charArrayOf('\\'), 1)
    }

    // Check if this is actually a Unicode escape
    if (input[0] != '\\' || input[1] != 'u') {
        return Pair(charArrayOf('\\'), 1)
    }

    val hexStr = input.substring(2, 6)
    val codePoint = hexStr.toIntOrNull(16) ?: run {
        // Invalid hex sequence, return backslash
        return Pair(charArrayOf('\\'), 1)
    }

    // Check for high surrogate
    if (codePoint.toChar().isHighSurrogate()) {
        // Look for low surrogate
        if (input.length >= 12 &&
            input[6] == '\\' &&
            input[7] == 'u') {

            val lowHexStr = input.substring(8, 12)
            val lowCodePoint = lowHexStr.toIntOrNull(16)

            if (lowCodePoint != null && lowCodePoint.toChar().isLowSurrogate()) {
                // Valid surrogate pair - return both surrogates and consumed 12 chars
                return Pair(charArrayOf(codePoint.toChar(), lowCodePoint.toChar()), 12)
            }
        }
    }

    // Regular Unicode character or unpaired surrogate - consumed 6 chars
    return Pair(charArrayOf(codePoint.toChar()), 6)
}

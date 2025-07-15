package org.kson.ast

import org.kson.parser.behavior.StringQuote

/**
 * Render the given string for inclusion in a JSON string literal.
 * This handles JSON-required escape sequences according to the [JSON RFC 8259 specification](https://datatracker.ietf.org/doc/html/rfc8259)
 *
 * @param content The string to escape
 * @return The given [content], prepared for inclusion in Json as a string
 */
fun renderForJsonString(content: String): String {
    val sb = StringBuilder(content.length + 2)
    
    var i = 0
    while (i < content.length) {
        val char = content[i]
        when {
            char == '"' -> sb.append("\\\"")
            char == '\\' -> sb.append("\\\\")
            char == '/' -> sb.append("\\/")  // Optional but included for maximum compatibility
            char == '\b' -> sb.append("\\b")
            char == '\u000C' -> sb.append("\\f")
            char == '\n' -> sb.append("\\n")
            char == '\r' -> sb.append("\\r")
            char == '\t' -> sb.append("\\t")
            char.isHighSurrogate() && i + 1 < content.length && content[i + 1].isLowSurrogate() -> {
                // Calculate code point from surrogate pair
                val high = char.code
                val low = content[++i].code
                val codePoint = 0x10000 + ((high - 0xD800) shl 10) + (low - 0xDC00)
                appendSurrogatePair(sb, codePoint)
            }
            char.code < 0x20 -> appendUnicodeEscape(sb, char.code)
            char == '\u2028' || char == '\u2029' -> appendUnicodeEscape(sb, char.code)
            else -> sb.append(char)
        }
        i++
    }
    
    return sb.toString()
}

/**
 * Kson strings allow raw whitespace, but otherwise escapes are identical to Json (modulo which [StringQuote] the
 * Kson string uses), so this function can help prepare an escaped Kson string for rendering as a Json string
 *
 * @param ksonEscapedString a string escaped according to Kson string escaping rules
 * @return a string escaped according to Json's rules (modulo the [StringQuote] of the [ksonEscapedString])
 */
fun escapeRawWhitespace(ksonEscapedString: String): String {
    val sb = StringBuilder(ksonEscapedString.length + 2)

    var i = 0
    while (i < ksonEscapedString.length) {
        when (val char = ksonEscapedString[i]) {
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(char)
        }
        i++
    }

    return sb.toString()
}

/**
 * Appends a Unicode escape sequence (\uXXXX) for the given code point.
 * JSON requires certain characters to be escaped as Unicode sequences:
 * - Control characters (U+0000 to U+001F)
 * - Unicode line/paragraph separators (U+2028, U+2029)
 *
 * @param sb The StringBuilder to append to
 * @param codePoint The Unicode code point to escape
 */
private fun appendUnicodeEscape(sb: StringBuilder, codePoint: Int) {
    sb.append("\\u")
    sb.append(codePoint.toString(16).uppercase().padStart(4, '0'))
}

/**
 * Appends a surrogate pair as two Unicode escape sequences for characters outside the BMP (Basic Multilingual Plane).
 * JSON requires characters beyond U+FFFF to be represented as surrogate pairs (see the paragraph starting
 * with "To escape an extended character that is not in the Basic Multilingual Plane..." in
 * [Section 7 of RFC 8259](https://datatracker.ietf.org/doc/html/rfc8259#section-7)
 *
 * This converts a supplementary code point to high and low surrogates and formats them as \uXXXX\uXXXX.
 *
 * @param sb The StringBuilder to append to
 * @param codePoint The Unicode code point to convert to a surrogate pair
 */
private fun appendSurrogatePair(sb: StringBuilder, codePoint: Int) {
    val high = (0xD800 or ((codePoint - 0x10000) shr 10))
    val low = (0xDC00 or ((codePoint - 0x10000) and 0x3FF))
    appendUnicodeEscape(sb, high)
    appendUnicodeEscape(sb, low)
}

/**
 * Unescape a string by converting escape sequences back to their original characters.
 * This is the reverse operation of [renderForJsonString].
 *
 * @param stringContent to unescape
 * @return the unescaped string
 */
fun unescapeStringContent(stringContent: String): String {
    val sb = StringBuilder(stringContent.length)
    var i = 0
    
    while (i < stringContent.length) {
        val char = stringContent[i]
        
        if (char == '\\' && i + 1 < stringContent.length) {
            when (val escaped = stringContent[i + 1]) {
                '"', '\\', '/' -> {
                    sb.append(escaped)
                    i += 2
                }
                'b' -> {
                    sb.append('\b')
                    i += 2
                }
                'f' -> {
                    sb.append('\u000C')
                    i += 2
                }
                'n' -> {
                    sb.append('\n')
                    i += 2
                }
                'r' -> {
                    sb.append('\r')
                    i += 2
                }
                't' -> {
                    sb.append('\t')
                    i += 2
                }
                'u' -> {
                    val (chars, consumed) = handleUnicodeEscape(stringContent.substring(i))
                    for (c in chars) {
                        sb.append(c)
                    }
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
    
    return sb.toString()
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

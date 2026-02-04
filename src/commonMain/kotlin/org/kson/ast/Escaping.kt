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
 * Converts `\/` escape sequences to plain `/` while preserving other escape sequences
 * (e.g. `\\/` which is an escaped backslash followed by a literal `/`).
 *
 * This is implemented as a standalone unescape to support YAML use-cases which does not allow the `\/` escape
 *
 * @param ksonEscapedString a string with Kson escape sequences intact
 * @return the string with `\/` escapes resolved to `/`
 */
fun unescapeForwardSlashes(ksonEscapedString: String): String {
    val sb = StringBuilder(ksonEscapedString.length)

    var i = 0
    while (i < ksonEscapedString.length) {
        val char = ksonEscapedString[i]
        if (char == '\\' && i + 1 < ksonEscapedString.length) {
            val next = ksonEscapedString[i + 1]
            if (next == '/') {
                // \/ is not a valid YAML escape; emit just /
                sb.append('/')
                i += 2
            } else {
                // preserve all other escape sequences as-is (consuming both chars to avoid
                // misinterpreting the second char, e.g. \\/ should stay as \\/ not become \/)
                sb.append(char)
                sb.append(next)
                i += 2
            }
        } else {
            sb.append(char)
            i++
        }
    }

    return sb.toString()
}

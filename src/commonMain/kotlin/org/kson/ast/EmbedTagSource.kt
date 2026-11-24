package org.kson.ast

import org.kson.parser.behavior.StringQuote.DoubleQuote
import org.kson.parser.behavior.Whitespace.isInlineWhitespace

/**
 * The KSON source of an embed block's tag, ready for rendering, see [embedBlockSource] and [doubleQuotedStringSource]
 *
 * [EmbedTagSource]s must be created through [fromTag] or [fromTagNode], which ensure all escapes are properly set
 */
internal class EmbedTagSource private constructor(private val escapedTag: String) {
    /**
     * The tag as written in embed block syntax
     */
    val embedBlockSource: String
        get() = escapedTag.takeWhile { isInlineWhitespace(it) }.let { leadingWhitespace ->
            buildString {
                /**
                 * Leading (raw) whitespace is not part of an embed tag (see [org.kson.parser.TokenType.EMBED_TAG]),
                 * so any leading whitespace that wants to be part of the tag must be encoded
                 */
                leadingWhitespace.forEach { appendUnicodeEscape(this, it.code) }
                append(escapedTag.substring(leadingWhitespace.length))
            }
        }

    /**
     * The tag as the content of a double-quoted string, for the `embedTag` property in object form
     */
    val doubleQuotedStringSource: String
        get() = DoubleQuote.escapeQuotes(escapedTag)

    companion object {
        fun fromTag(embedTag: String): EmbedTagSource {
            // an embed tag escapes identically to JSON, save for not wanting double-quotes escaped
            return EmbedTagSource(DoubleQuote.unescapeQuotes(renderForJsonString(embedTag)))
        }

        fun fromTagNode(embedTagNode: StringNodeImpl): EmbedTagSource {
            /**
             * [StringNodeImpl.delimiterUnescapedRawContent] already holds almost all the escapes we want. We also
             * escape raw whitespace to ensure we don't include any raw newlines that would prematurely end this tag
             */
            return EmbedTagSource(escapeRawWhitespace(embedTagNode.delimiterUnescapedRawContent))
        }
    }
}

package org.kson.ast

import org.kson.parser.behavior.StringQuote.DoubleQuote
import org.kson.parser.behavior.Whitespace.isInlineWhitespace

/**
 * The KSON [escapedSource] of an embed block's tag, ready for rendering in embed block syntax.
 *
 * [EmbedTagSource]s must be created through [fromTag] or [fromTagNode], which ensure all escapes are properly set
 */
internal class EmbedTagSource private constructor(tagSource: String) {
    val escapedSource: String = tagSource.takeWhile { isInlineWhitespace(it) }.let { leadingWhitespace ->
        // leading (raw) whitespace is not considered part of an embed tag (see [org.kson.parser.TokenType.EMBED_TAG]),
        // so any leading whitespace that wants to be part of the tag must be encoded
        leadingWhitespace.map { "\\u" + it.code.toString(16).padStart(4, '0') }.joinToString("") +
            tagSource.substring(leadingWhitespace.length)
    }

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

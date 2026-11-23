package org.kson.ast

import org.kson.parser.behavior.StringQuote.DoubleQuote

/**
 * The KSON [escapedSource] of an embed block's tag, ready for rendering in embed block syntax.
 *
 * [EmbedTagSource]s must be created through [fromTag] or [fromTagNode], which ensure all escapes are properly set
 */
internal class EmbedTagSource private constructor(val escapedSource: String) {
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

package org.kson.jetbrains.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.util.text.splitToTextRanges
import org.kson.parser.behavior.embedblock.EmbedBlockIndent

/**
 * This class is used to handle the trimming of indentation in KSON embedded content blocks.
 * This allows us to remove the indentation when doing language injection.
 */
class KsonTrimIndentHandler {
    /**
     * Calculates and returns the untrimmed ranges in the given KSON embedded content.
     *
     * Each line of the content contributes one range covering the text after the indent that
     * [EmbedBlockIndent] trims from it, through the newline that ends it, so that the injected text
     * is the embed block's value line for line.
     *
     * @param content The KSON embedded content
     * @return A list of [TextRange] objects representing the trimmed lines.
     */
    fun getUntrimmedRanges(content: KsonEmbedContent): List<TextRange> {
        val text = content.text
        val trimmedIndentPerLine = EmbedBlockIndent(text).trimmedIndentPerLine

        val valueTextRange = ElementManipulators.getValueTextRange(content)

        /**
         * We're using this unstable API as part of emulating Kotlin's approach to injected code in indented blocks,
         * [see here](https://github.com/JetBrains/intellij-community/blob/4d2499e460bd6ab6425de24517d0050b65a78f99/plugins/kotlin/injection/base/src/org/jetbrains/kotlin/idea/base/injection/IndentHandler.kt#L36)
         */
        @Suppress("UnstableApiUsage")
        val lineRanges = splitToTextRanges(text, "\n").toList()

        return lineRanges.zip(trimmedIndentPerLine).mapNotNull { (lineRange, trimmedIndent) ->
            val untrimmedStart = lineRange.startOffset + trimmedIndent
            // the +1 keeps this line's newline, so a line trimmed to nothing is still represented
            val lineEndWithNewline = (lineRange.endOffset + 1).coerceAtMost(valueTextRange.endOffset)
            TextRange(untrimmedStart, lineEndWithNewline).intersection(valueTextRange)
        }
    }
}

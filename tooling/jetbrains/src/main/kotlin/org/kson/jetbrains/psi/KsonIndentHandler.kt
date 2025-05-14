package org.kson.jetbrains.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.util.SmartList
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
     * This method splits the content into line ranges and then for each line,
     * removes the indentation if it matches the minimum indentation text.
     *
     * @param content The KSON embedded content
     * @return A list of [TextRange] objects representing the trimmed lines.
     */
    fun getUntrimmedRanges(content: KsonEmbedContent): List<TextRange> {
        val text = content.text
        val minIndent = EmbedBlockIndent(text).computeMinimumIndent()

        val valueTextRange = ElementManipulators.getValueTextRange(content)

        val ranges = SmartList<TextRange>()
        val linesRanges = splitToTextRanges(text, "\n").toList()

        for (lineRange0 in linesRanges) {
            val lineRange = valueTextRange.intersection(lineRange0) ?: continue

            val indentText = " ".repeat(minIndent)
            if (indentText.contentEquals(
                    text.subSequence(
                        lineRange.startOffset,
                        lineRange.startOffset + minIndent
                    )
                )
            ) {
                val startOffset = lineRange.startOffset.coerceAtLeast(valueTextRange.startOffset) + minIndent
                val endOffset = (lineRange.endOffset + 1).coerceAtMost(valueTextRange.endOffset)
                ranges.add(TextRange(startOffset, endOffset))
            } else ranges.add(lineRange)

        }
        return ranges.mapNotNull { it.intersection(valueTextRange) }
    }
}
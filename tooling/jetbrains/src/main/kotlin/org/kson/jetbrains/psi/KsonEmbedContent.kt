package org.kson.jetbrains.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.kson.parser.delimiters.EmbedDelim

open class KsonEmbedContent(node: ASTNode) : KsonPsiElement(node), PsiLanguageInjectionHost {
    val embedBlock: KsonEmbedBlock?
        get() = node.treeParent?.psi as? KsonEmbedBlock

    override fun isValidHost(): Boolean {
        // First check if parent is a valid KsonEmbedBlock
        if (this.embedBlock !is KsonEmbedBlock) return false

        // Check for any error elements in the PSI tree
        var hasErrors = false
        this.embedBlock?.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitErrorElement(element: PsiErrorElement) {
                hasErrors = true
                stopWalking()
            }
        })
        return !hasErrors
    }

    override fun updateText(text: String): PsiLanguageInjectionHost? {
        return ElementManipulators.handleContentChange(this, text)
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost?> {
        return EmbedContentLiteralTextEscaper(this)
    }

    private class EmbedContentLiteralTextEscaper(host: KsonEmbedContent) :
        LiteralTextEscaper<PsiLanguageInjectionHost>(host) {
        private val embedDelim =
            host.embedBlock?.embedDelim ?: EmbedDelim.Percent
        private val minIndent = host.embedBlock?.let { KsonEmbedBlock.minimumIndentEmbedBlock(it) } ?: 0

        /**
         * Decodes the host text by:
         * 1. Unescaping any delimiters in the embed content using the embed block's delimiter rules
         * 2. Removing the minimum common indentation from all lines
         *
         * @param rangeInsideHost The range of text to decode
         * @param outChars StringBuilder to receive the decoded text
         * @return true if decoding was successful, false otherwise
         */
        override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
            val hostText = myHost.text ?: return false

            // For whitespace-only content, on a single line we don't need to decode
            if (hostText.all { it.isWhitespace() } && numberOfLines(hostText) == 1) {
                outChars.append(hostText)
                return true
            }

            // Unescape the embed content
            val unescapedText = embedDelim.unescapeEmbedContent(hostText)

            // Remove the minimum common indentation
            val lines = unescapedText.split("\n")
            val unindentedLines = lines.map { line ->
                line.substring(minIndent)
            }.joinToString("\n")

            outChars.append(unindentedLines)
            return true
        }

        /**
         * Returns the offset in the host text for the given offset in the decoded text.
         * This method handles the reverse mapping of offsets from the decoded text back to the host text,
         * taking into account:
         * 1. The minimum indentation that was removed during decoding
         * 2. Any escaped delimiters in the original text
         *
         * @param offsetInDecoded The offset in the decoded text
         * @param rangeInsideHost The range of text to decode
         * @return The offset in the host text, or -1 if the offset is invalid
         */
        override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
            val embedContentText = myHost.text ?: return -1
            if (embedContentText.isEmpty() || offsetInDecoded < 0) return -1

            // For whitespace-only content on a single line we use direct offset mapping.
            if (embedContentText.all { it.isWhitespace() } && numberOfLines(embedContentText) == 1) {
                return if (offsetInDecoded <= embedContentText.length) offsetInDecoded else -1
            }

            var currentOffset = 0
            var decodedOffset = 0

            // Iterate over the lines of the embed content text and find the line containing the target offset
            // Then we know how many indentations to add back to the line to get the host offset
            for (line in embedContentText.split("\n")) {
                // Calculate the length of the line after removing the minimum indentation
                val decodedLineLength = if (line.length >= minIndent) line.length - minIndent else line.length

                // If the target offset is within the current line, we can return the host offset
                if (decodedOffset + decodedLineLength >= offsetInDecoded) {
                    val remainingOffset = offsetInDecoded - decodedOffset
                    val lineOffset = if (line.length >= minIndent) minIndent + remainingOffset else remainingOffset
                    currentOffset += if (lineOffset <= line.length) lineOffset else line.length
                    break
                }

                // Add the full line length to the host offset
                currentOffset += line.length + 1  // +1 for newline
                // Add the decoded line length to the decoded offset
                decodedOffset += decodedLineLength + 1  // +1 for newline
            }

            // Find all escaped delimiter positions before our target position
            val escapedIndices = embedDelim.findEscapedDelimiterIndices(embedContentText)
                .filter { it < currentOffset }
            currentOffset += escapedIndices.size

            // Ensure the result is within the valid range
            return if (currentOffset <= rangeInsideHost.endOffset) currentOffset else -1
        }

        override fun isOneLine(): Boolean = false
    }

    internal class Manipulator : AbstractElementManipulator<KsonEmbedContent>() {
        /**
         * This is the inverse of the decode method.
         * It takes the content of the embed content and returns the updated embed content. With proper escaping and minimum indentation.
         *
         * This method is called when the user edits the content of the embed content, in a designated editor,
         * to improve the user experience we removed the minimum indentation and escaped the content properly for editing in the designated editor.
         * To bring the content back to the original form we need to add back the minimum indentation and escape the content properly.
         *
         * One caveat is that for certain auto-completes this method is called as well. In that case we don't want to add back the minimum indentation.
         * The workaround is to check if the minimum indent of the content is less than the minimum indent of the embed block.
         * If it is, we add back the minimum indentation, otherwise we don't.
         */
        override fun handleContentChange(
            element: KsonEmbedContent,
            range: TextRange,
            content: String
        ): KsonEmbedContent? {
            val embedBlock = element.embedBlock ?: return null
            val embedDelim = embedBlock.embedDelim

            val minIndentElement = KsonEmbedBlock.minimumIndentEmbedBlock(embedBlock)
            val minIndentContent = minimumIndent(content)

            // Add back the minimum indentation if the content has less indent than the embed block
            val processedContent = if (minIndentContent < minIndentElement || minIndentContent == 0  ) {
                // Add minimum indentation to each line for direct edits
                content.split("\n").map { " ".repeat(minIndentElement) + it }.joinToString("\n")
            } else {
                content
            }

            // Escape the content properly
            val escapedContent = embedDelim.escapeEmbedContent(processedContent)

            // Generate the updated embed content and replace the existing one
            val ksonGenerator = KsonElementGenerator(element.project)
            val updatedEmbedContent = ksonGenerator.createEmbedContent(escapedContent) ?: return null
            return element.replace(updatedEmbedContent) as KsonEmbedContent?
        }
    }

    companion object {
        /**
         * Returns the minimum common number of spaces of the given content.
         */
        private fun minimumIndent(content: String): Int {
            val linesWithNewlines = content.split("\n").map { it + "\n" }
            val minCommonIndent =
                linesWithNewlines.subList(1, linesWithNewlines.size)
                    .minOfOrNull { it.indexOfFirst { char -> !isInlineWhitespace(char) } } ?: 0
            return minCommonIndent
        }

        /**
         * Returns the number of lines in the given content.
         *
         * TODO double check if we shouldn't handle this with the minimumIndent in EmbedBlock
         */
        private fun numberOfLines(content: String): Int {
            return content.split("\n").size
        }

        /**
         * Returns true if the given [char] is a non-newline whitespace
         */
        private fun isInlineWhitespace(char: Char?): Boolean {
            return char == ' ' || char == '\r' || char == '\t'
        }
    }
}

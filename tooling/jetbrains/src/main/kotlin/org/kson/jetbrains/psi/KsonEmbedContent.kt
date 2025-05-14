package org.kson.jetbrains.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.kson.parser.behavior.embedblock.EmbedBlockIndent
import org.kson.parser.behavior.embedblock.EmbedDelim

open class KsonEmbedContent(node: ASTNode) : KsonPsiElement(node), PsiLanguageInjectionHost {
    val embedBlock: KsonEmbedBlock?
        get() = node.treeParent?.psi as? KsonEmbedBlock
    val indentHandler = KsonTrimIndentHandler()
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
        private val embedDelim = host.embedBlock?.embedDelim ?: EmbedDelim.Percent

        /**
         * Decodes the host text by unescaping any delimiters in the embed content using the embed block's
         * delimiter rules
         *
         * @param rangeInsideHost The range of text to decode
         * @param outChars StringBuilder to receive the decoded text
         * @return true if decoding was successful, false otherwise
         */
        override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
            val hostText = rangeInsideHost.substring(myHost.text)

            // Unescape the embed content
            val unescapedText = embedDelim.unescapeEmbedContent(hostText)
            outChars.append(unescapedText)
            return true
        }

        /**
         * Returns the offset in the host text for the given offset in the decoded text.
         * This method handles the reverse mapping of offsets from the decoded text back to the host text,
         * taking into account the escaped delimiters in the original text
         *
         * @param offsetInDecoded The offset in the decoded text
         * @param rangeInsideHost The range of text to decode
         * @return The offset in the host text, or -1 if the offset is invalid
         */
        override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
            val hostText = rangeInsideHost.substring(myHost.text)
            if (hostText.isEmpty() || offsetInDecoded < 0) return -1

            // First ensure the offset is within the range
            if (offsetInDecoded > rangeInsideHost.length) return -1

            // Calculate the actual offset in the host text
            val hostOffset = rangeInsideHost.startOffset + offsetInDecoded

            // Find all escaped delimiter positions before our target position and adjust offset
            val escapedIndices = embedDelim.findEscapedDelimiterIndices(hostText)
                .filter { it < hostOffset }
            val adjustedOffset = hostOffset + escapedIndices.size

            // Ensure the result is within the valid range
            return if (adjustedOffset <= rangeInsideHost.endOffset) adjustedOffset else -1
        }

        override fun isOneLine(): Boolean = false
    }

    internal class Manipulator : AbstractElementManipulator<KsonEmbedContent>() {
        /**
         * This is the inverse of the decode method.
         * It takes the content of the embed content and returns the updated embed content with proper escaping and minimum indentation.
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
            val escapedContentText = embedDelim.escapeEmbedContent(content)

            val minimumIndentEmbedBlock = EmbedBlockIndent(element.text).computeMinimumIndent()
            val indentText = " ".repeat(minimumIndentEmbedBlock)

            val lines = escapedContentText.lines()

            /**
            * TODO this is a bit of a hack now. In most cases this handles an update in the fragment editor well.
            * However, it fails if we are typing on the last line and also shows some buggy behavior with auto completing
            * xml. Removing `index==lines.lastIndex` runs into the issue that the minimum indent is shifted forward
            * each 'update'
            **/
            val indentedLines = lines
                .mapIndexed { index, line ->
                    if (index == 0 || index == lines.lastIndex) line
                    else line.prependIndent(indentText)
                }
                .joinToString("\n")

            val updatedElementText = element.text.replaceRange(range.startOffset, range.endOffset, indentedLines)

            // Generate the updated embed content and replace the existing one
            val ksonGenerator = KsonElementGenerator(element.project)
            val updatedEmbedContent = ksonGenerator.createEmbedBlock(embedDelim, updatedElementText).embedContent ?: return null
            return element.replace(updatedEmbedContent) as KsonEmbedContent?
        }
    }
}

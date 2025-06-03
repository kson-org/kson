package org.kson.jetbrains.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
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
        val embedDelim = embedBlock?.embedDelim ?: EmbedDelim.Percent

        // Escape the content based on the embed delimiter
        val escapedContent = embedDelim.escapeEmbedContent(text)

        // Create a new embed content element with the updated text
        val ksonGenerator = KsonElementGenerator(this.project)
        val newEmbedBlock = ksonGenerator.createEmbedBlock(embedDelim, escapedContent)
        val newEmbedContent = newEmbedBlock.embedContent ?: return null

        // Update the `EmbedContent` node with the newly generated node
        this.parent.node.replaceChild(this.node, newEmbedContent.node)
        return newEmbedContent
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
}

package org.kson.jetbrains.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.kson.parser.delimiters.EmbedDelim

open class KsonEmbedContent(node: ASTNode) : KsonPsiElement(node), PsiLanguageInjectionHost {
    val embedBlock: KsonEmbedBlock?
        get() = node.treeParent?.psi as? KsonEmbedBlock

    override fun isValidHost(): Boolean {
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
        // bd todo this is  called when auto-completing in injected language
        // and when editing a fragment.
        // However, it is not called when starting the edit fragment window.
        // Maybe this is the place to remove indents. Maybe it is the text escaper.
        // Problem stays that it's hard to figure out when to add the indent since we are either in
        // edit_fragment mode (and need to remove it) or in normal mode.
        return ElementManipulators.handleContentChange(this, text)
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost?> {
        return EmbedContentLiteralTextEscaper(this)
    }

    private class EmbedContentLiteralTextEscaper(host: KsonEmbedContent) :
        LiteralTextEscaper<PsiLanguageInjectionHost>(host) {
        private val embedDelim =
            host.embedBlock?.embedDelim ?: EmbedDelim.Percent // Default to %% if no delimiter found

        override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
            val unescapedText = embedDelim.unescapeEmbedContent(myHost.text)
            outChars.append(unescapedText)

            return true
        }

        override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
            val embedContentText = myHost.text ?: return -1
            val unescapedText = embedDelim.unescapeEmbedContent(embedContentText)

            // If we're past the end of unescaped text, return -1
            if (offsetInDecoded > unescapedText.length) return -1

            // Find all escaped delimiter positions before our target position
            val escapedIndices = embedDelim.findEscapedDelimiterIndices(embedContentText)
                .filter { it < offsetInDecoded }

            // Calculate the offset in the host text, accounting for escaped delimiters
            val result = offsetInDecoded + escapedIndices.size
            
            // Ensure the result is within the valid range
            return if (result <= rangeInsideHost.endOffset) result else -1
        }

        override fun isOneLine(): Boolean = false
    }

    internal class Manipulator : AbstractElementManipulator<KsonEmbedContent>() {
        override fun handleContentChange(element: KsonEmbedContent, range: TextRange, content: String): KsonEmbedContent? {
            val embedBlock = element.embedBlock ?: return null
            val embedDelim = embedBlock.embedDelim ?: return null
            val escapedContent = embedDelim.escapeEmbedContent(content)

            val ksonGenerator = KsonElementGenerator(element.project)
            val updatedEmbedContent = ksonGenerator.createEmbedContent(escapedContent) ?: return null
            return element.replace(updatedEmbedContent) as KsonEmbedContent
        }
    }
}

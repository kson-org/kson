package org.kson.jetbrains.psi

import com.intellij.lang.ASTNode
import org.kson.jetbrains.parser.elem
import org.kson.parser.TokenType
import org.kson.parser.delimiters.EmbedDelim
import org.kson.stdlibx.exceptions.ShouldNotHappenException

class KsonEmbedBlock(node: ASTNode) : KsonPsiElement(node) {
    val embedBlockTag: String?
        get() = node.findChildByType(elem(TokenType.EMBED_TAG))?.text

    val embedDelim: EmbedDelim
        get() = getDelim(this)

    val embedContent: KsonEmbedContent?
        get() = node.findChildByType(elem(TokenType.EMBED_CONTENT))?.psi as? KsonEmbedContent


    companion object {
        private fun getDelim(host: KsonEmbedBlock): EmbedDelim {
            return host.children.find {
                it.node.elementType == elem(TokenType.EMBED_OPEN_DELIM)
            }?.text?.let { EmbedDelim.fromString(it) } ?: throw ShouldNotHappenException("Embed delimiter not found")
        }

        /**
         * Returns the minimum common number of spaces of the given content.
         */
        fun minimumIndentEmbedBlock(host: KsonEmbedBlock): Int {
            val linesWithNewlines = host.text.split("\n").map { it + "\n" }
            val minCommonIndent =
                linesWithNewlines.subList(1, linesWithNewlines.size).minOfOrNull { it.indexOfFirst { char -> !isInlineWhitespace(char) } } ?: 0
            return minCommonIndent
        }

        /**
         * Returns true if the given [char] is a non-newline whitespace
         */
        private fun isInlineWhitespace(char: Char?): Boolean {
            return char == ' ' || char == '\r' || char == '\t'
        }
    }
}

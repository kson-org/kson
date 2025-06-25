package org.kson.jetbrains.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.kson.jetbrains.parser.elem
import org.kson.parser.TokenType
import org.kson.parser.behavior.embedblock.EmbedDelim
import org.kson.stdlibx.exceptions.ShouldNotHappenException

class KsonEmbedBlock(node: ASTNode) : KsonPsiElement(node) {
    val embedBlockTag: String
        get() = node.findChildByType(elem(TokenType.EMBED_TAG))?.text ?: ""

    val embedDelim: EmbedDelim
        get() = getDelim(this)

    val embedContent: KsonEmbedContent?
        get() = node.findChildByType(elem(TokenType.EMBED_CONTENT))?.psi as? KsonEmbedContent


    companion object {
        private fun getDelim(host: KsonEmbedBlock): EmbedDelim {
            val embedDelimText =
                host.children.find { it.elementType == elem(TokenType.EMBED_OPEN_DELIM) }?.text
                    ?: throw ShouldNotHappenException("Embed delimiter not found")
            return EmbedDelim.fromString(embedDelimText)
        }
    }
}

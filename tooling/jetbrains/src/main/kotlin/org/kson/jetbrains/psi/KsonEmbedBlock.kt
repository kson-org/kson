package org.kson.jetbrains.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.kson.jetbrains.parser.elem
import org.kson.parser.TokenType
import org.kson.parser.delimiters.EmbedDelim

class KsonEmbedBlock(node: ASTNode) : KsonPsiElement(node) {
    val embedBlockTag: String?
        get() = node.findChildByType(elem(TokenType.EMBED_TAG))?.text

    val embedDelim: EmbedDelim?
        get() = getDelim(this)

    val embedContent: KsonEmbedContent?
        get() = node.findChildByType(elem(TokenType.EMBED_CONTENT))?.psi as? KsonEmbedContent


    companion object {
        fun getDelim(host: KsonEmbedBlock): EmbedDelim? {
            return host.children.find {
                it.node.elementType == elem(TokenType.EMBED_OPEN_DELIM)
            }?.text?.let { EmbedDelim.fromString(it) }
        }
    }
}

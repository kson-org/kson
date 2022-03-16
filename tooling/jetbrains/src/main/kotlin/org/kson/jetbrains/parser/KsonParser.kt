package org.kson.jetbrains.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import org.kson.parser.*
import org.kson.parser.messages.Message

/**
 * [PsiParser] for Kson, implemented by delegating to [Parser]
 */
class KsonParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        val delegatingBuilder = DelegatingBuilder(builder)
        val ksonParser = Parser(delegatingBuilder)
        ksonParser.parse()
        rootMarker.done(root)
        return builder.treeBuilt
    }
}

/**
 * Implements a PSI-compatible [AstBuilder] for [Parser] by delegating to the given [psiBuilder]
 */
private class DelegatingBuilder(val psiBuilder: PsiBuilder) : AstBuilder {
    override fun getTokenType(): TokenType? {
        return if (psiBuilder.tokenType == null) {
            null
        } else {
            (psiBuilder.tokenType as KsonLexedElementType).tokenType
        }
    }

    override fun advanceLexer() {
        psiBuilder.advanceLexer()
    }

    override fun lookAhead(numTokens: Int): TokenType? {
        val lookAheadElement = psiBuilder.lookAhead(numTokens)
        return if (lookAheadElement == null) {
            null
        } else {
            (lookAheadElement as KsonLexedElementType).tokenType
        }
    }

    override fun eof(): Boolean {
        return psiBuilder.eof()
    }

    override fun mark(): AstMarker {
        return object : AstMarker {
            val psiMark = psiBuilder.mark()
            override fun done(elementType: ElementType) {
                psiMark.done(elem(elementType))
            }

            override fun rollbackTo() {
                psiMark.drop()
            }

            override fun error(message: Message, vararg args: String?) {
                psiMark.error(message.format(*args))
            }
        }
    }
}
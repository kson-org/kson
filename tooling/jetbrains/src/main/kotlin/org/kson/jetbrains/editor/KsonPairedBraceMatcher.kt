package org.kson.jetbrains.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.parser.elem
import org.kson.parser.TokenType

private val bracePairs = arrayOf(
    BracePair(
        elem(TokenType.BRACE_L),
        elem(TokenType.BRACE_R),
        true
    ),
    BracePair(
        elem(TokenType.BRACKET_L),
        elem(TokenType.BRACKET_R),
        true
    )
)

class KsonPairedBraceMatcher : PairedBraceMatcher {

    override fun getPairs(): Array<BracePair> {
        return bracePairs
    }

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean {
        return true
    }

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int {
        return openingBraceOffset
    }
}
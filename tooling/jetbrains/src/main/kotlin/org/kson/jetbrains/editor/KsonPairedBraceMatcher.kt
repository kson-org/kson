package org.kson.jetbrains.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.parser.elem
import org.kson.parser.TokenType

private val bracePairs = arrayOf(
    BracePair(
        elem(TokenType.CURLY_BRACE_L),
        elem(TokenType.CURLY_BRACE_R),
        true
    ),
    BracePair(
        elem(TokenType.SQUARE_BRACKET_L),
        elem(TokenType.SQUARE_BRACKET_R),
        true
    ),
    BracePair(
        elem(TokenType.ANGLE_BRACKET_L),
        elem(TokenType.ANGLE_BRACKET_R),
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

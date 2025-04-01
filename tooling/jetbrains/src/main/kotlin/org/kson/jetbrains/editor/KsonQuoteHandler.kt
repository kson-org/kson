package org.kson.jetbrains.editor
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import org.kson.jetbrains.parser.elem
import org.kson.parser.TokenType


class KsonQuoteHandler : SimpleTokenSetQuoteHandler(elem(TokenType.STRING_OPEN_QUOTE), elem(TokenType.STRING_CLOSE_QUOTE)) {

    override fun isOpeningQuote(iterator: HighlighterIterator?, offset: Int): Boolean {
        if (iterator == null) {
            return false
        }

        return iterator.tokenType == elem(TokenType.STRING_OPEN_QUOTE)
    }
}

package org.kson.jetbrains.editor
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import org.kson.jetbrains.parser.elem
import org.kson.parser.TokenType


class KsonQuoteHandler : SimpleTokenSetQuoteHandler(elem(TokenType.STRING_QUOTE)) {

    override fun isOpeningQuote(iterator: HighlighterIterator?, offset: Int): Boolean {
        if (iterator == null) {
            return false
        }

        if (iterator.tokenType != elem(TokenType.STRING_QUOTE)) {
            // current token is not a quote, hence not an opening quote
            return false
        }

        // we want to inspect the token before this quote to see if it's closing a STRING
        iterator.retreat()

        if (iterator.atEnd()) {
            // nothing before this quote, so it's an opener
            return true
        }

        val previousTokenType = iterator.tokenType
        iterator.advance()

        // if the token before this is not a STRING, this is an opening quote
        return previousTokenType != elem(TokenType.STRING)
    }
}

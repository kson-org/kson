package org.kson.jetbrains.parser

import com.intellij.lexer.LexerBase
import com.intellij.lexer.LexerPosition
import com.intellij.psi.tree.IElementType
import org.kson.stdlibx.collections.ImmutableList
import org.kson.stdlibx.collections.toImmutableList
import org.kson.parser.Lexer
import org.kson.parser.Token
import org.kson.parser.TokenType

/**
 * [KsonLexer] implements the [com.intellij.lexer.Lexer] interface by delegating to the main
 * Kson [Lexer]
 */
class KsonLexer : LexerBase() {
    private var ksonTokens: ImmutableList<Token> = emptyList<Token>().toImmutableList()
    // we'll use this index as this lexer's "state" since this lexer is a precomputed array of tokens
    private var ksonTokensIndex = 0
    private var buffer: CharSequence = ""
    private var bufferEnd: Int = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEnd = endOffset
        ksonTokens = Lexer(buffer.substring(startOffset, endOffset),
            // note that we demand a gap-free lex here to properly comply with Jetbrains' Lexer interface demands.
            // see the green "Info" block on this page for details: https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html
            true).tokenize()
        ksonTokensIndex = initialState
    }

    override fun getState(): Int {
        return ksonTokensIndex
    }

    override fun getTokenType(): IElementType? {
        if (ksonTokensIndex >= ksonTokens.size) {
            return null
        }

        val lexedElementType = ksonTokens[ksonTokensIndex].tokenType

        // if we're looking at EOF, we have no more tokens that the IDE cares about
        if (lexedElementType == TokenType.EOF) {
            return null
        }

        return elem(lexedElementType)
    }

    override fun getTokenStart(): Int {
        return ksonTokens[ksonTokensIndex].lexeme.location.startOffset
    }

    override fun getTokenEnd(): Int {
        return ksonTokens[ksonTokensIndex].lexeme.location.endOffset
    }

    override fun advance() {
        ksonTokensIndex++
    }

    override fun restore(position: LexerPosition) {
        ksonTokensIndex = position.state
    }

    override fun getBufferSequence(): CharSequence {
        return buffer
    }

    override fun getBufferEnd(): Int {
        return bufferEnd
    }
}
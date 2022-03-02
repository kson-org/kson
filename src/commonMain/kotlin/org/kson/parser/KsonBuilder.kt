package org.kson.parser

import org.kson.ast.KsonRoot

/**
 * An [AstBuilder] implementation used to produce a [KsonRoot] rooted AST tree based on the given [Token]s
 */
class KsonBuilder(private val tokens: List<Token>): AstBuilder {
    var currentToken = 0

    override fun getTokenType(): TokenType? {
        if (currentToken < tokens.size) {
            return tokens[currentToken].tokenType
        }

        return null
    }

    override fun advanceLexer() {
        currentToken++
    }

    override fun lookAhead(numTokens: Int): TokenType? {
        val aheadToken = currentToken + numTokens
        if (aheadToken < tokens.size) {
            return tokens[aheadToken].tokenType
        }
        return null
    }

    override fun eof(): Boolean {
        // parser todo nuke the EOF token now? Seems it's not as useful/needed with this new builder-style
        //             of parser, so these two checks are redundant
        return currentToken == tokens.size - 1 && tokens[currentToken].tokenType == TokenType.EOF
    }

    override fun mark(): AstMarker {
        // parser todo
        TODO("Must finish implementing marker/builder-style parser")
    }

    fun buildTree(): KsonRoot {
        // parser todo
        TODO("Must finish implementing marker/builder-style parser")
    }
}

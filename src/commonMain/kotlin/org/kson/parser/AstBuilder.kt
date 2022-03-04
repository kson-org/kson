package org.kson.parser

import org.kson.parser.messages.Message

/**
 * [AstBuilder] supports the strategy used by [Parser] to efficiently build an Abstract Syntax Tree with
 * high-resolution parser error reporting.
 *
 * This is inspired by the `PsiBuilder` approached used in
 * [JetBrains platform custom language support](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html),
 * exposing an interface on a token stream that will "[mark]" the AST elements (or ranges of tokens in error)
 * in the token stream, deferring the actual AST tree construction until mark-based parsing is complete.
 */
interface AstBuilder {
    /**
     * Return the [TokenType] of the current token from the underlying lexer, or `null` if lexing is complete
     */
    fun getTokenType(): TokenType?

    /**
     * Advance the underlying lexer to the next token
     */
    fun advanceLexer()

    /**
     * Look ahead [numTokens] tokens in the underlying lexer, or `null` if lexing completes in fewer steps
     */
    fun lookAhead(numTokens: Int): TokenType?

    /**
     * Return true if lexing is complete
     */
    fun eof(): Boolean

    /**
     * Start an [AstMarker] at the current [getTokenType].  This marker must be "resolved" by one of the methods
     * on [AstMarker], and may impact the state of this [AstBuilder] (see [AstMarker.rollbackTo] for instance)
     */
    fun mark(): AstMarker
}

/**
 * [AstMarker] collaborates tightly with [AstBuilder]
 */
interface AstMarker {
    /**
     * Complete this mark, "tagging" the tokens lexed while this mark was outstanding as being of type [elementType]
     */
    fun done(elementType: ElementType)

    /**
     * Declare this mark unneeded, winding the [AstBuilder] that produced it back to when this mark was created with
     * [AstBuilder.mark]
     */
    fun rollbackTo()

    /**
     * Complete this mark, "tagging" the tokens lexed while this mark was outstanding as being in error as
     * described in [message]
     */
    fun error(message: Message, vararg args: String?)
}
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
     * Get the text underlying this token---useful, for instance, in some higher resolution error messages
     */
    fun getTokenText(): String

    /**
     * Advance the underlying lexer to the next token
     */
    fun advanceLexer()

    /**
     * Look ahead [numTokens] tokens in the underlying lexer, or `null` if lexing completes in fewer steps
     */
    fun lookAhead(numTokens: Int): TokenType?

    /**
     * Returns true if all the tokens in this builder's underlying lexer have been advanced over by [advanceLexer]
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
     * Declare this marker unneeded.  This removes the marker from the marker tree, but unlike [rollbackTo]:
     * - the [AstBuilder] is not reset to the marker's start
     * - the marker's children are preserved in the marker tree by stitching them into the tree in the dropped marker's place.
     *
     * Used to facilitate markers that do not correspond to AST nodes (for instance, markers used solely to
     * possibly denote an error), allowing them to be bailed on if not needed
     */
    fun drop()

    /**
     * Declare this mark (and all its children) unneeded, winding the [AstBuilder] that produced it back to when this
     * mark was created with [AstBuilder.mark]
     */
    fun rollbackTo()

    /**
     * Complete this mark, "tagging" the tokens lexed while this mark was outstanding as being in error as
     * described in [message]
     */
    fun error(message: Message)
}
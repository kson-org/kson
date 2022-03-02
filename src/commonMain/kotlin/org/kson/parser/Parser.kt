package org.kson.parser

import org.kson.ast.*
import org.kson.parser.messages.Message
import org.kson.parser.ParsedElementType.*
import org.kson.parser.TokenType.*

/**
 * Defines the Kson parser, implemented as a recursive descent parser which directly implements
 * the following grammar, one method per grammar rule:
 *
 * ```
 * kson -> (objectInternals | value) EOF ;
 * objectInternals -> ( keyword value ","? )* ;
 * value -> objectDefinition
 *        | list
 *        | literal
 *        | embedBlock ;
 * objectDefinition -> ( objectName | "" ) "{" objectInternals "}" ;
 * list -> "[" (value ",")* value? "]"
 * keyword -> ( IDENTIFIER | STRING ) ":" ;
 * literal -> STRING | NUMBER | "true" | "false" | "null" ;
 * embeddedBlock -> "```" (embedTag) NEWLINE CONTENT "```" ;
 * ```
 *
 * See [section 5.1 here](https://craftinginterpreters.com/representing-code.html#context-free-grammars)
 * for details on this grammar notation.
 *
 * See [section 6.2 here](https://craftinginterpreters.com/parsing-expressions.html#recursive-descent-parsing)
 * for excellent context on a similar style of hand-crafted recursive descent parser.
 */
class Parser(val builder: AstBuilder) {

    /**
     * kson -> (objectInternals | value) EOF ;
     */
    fun parse() {
        if (objectInternals() || value()) {
            if (hasUnexpectedTrailingContent()) {
                // parser todo we likely want to see if we can contiue parsing after adding the error noting
                //             the unexpected extra content
            }
        }
    }

    /**
     * objectInternals -> ( keyword value ","? )* ;
     */
    private fun objectInternals(): Boolean {
        var foundProperties = false

        while (true) {
            val propertyMark = builder.mark()
            if (keyword() && value()) {
                foundProperties = true
                propertyMark.done(PROPERTY)
            } else {
                propertyMark.rollbackTo()
                break
            }

            if (builder.getTokenType() == COMMA) {
                // advance past the optional COMMA
                builder.advanceLexer()
            }
        }

        return foundProperties
    }

    /**
     * value -> objectDefinition
     *        | list
     *        | literal
     *        | embedBlock ;
     */
    private fun value(): Boolean {
        val valueMark = builder.mark()
        if (objectDefinition()
            || list()
            || literal()
            || embedBlock()
        ) {
            valueMark.done(VALUE)
            return true
        } else {
            valueMark.rollbackTo()
            return false
        }
    }

    /**
     * objectDefinition -> ( objectName | "" ) "{" objectInternals "}" ;
     */
    private fun objectDefinition(): Boolean {
        if (builder.getTokenType() == BRACE_L
            || builder.getTokenType() == IDENTIFIER && builder.lookAhead(1) == BRACE_L
        ) {
            val objectNameMarker = builder.mark()
            if (builder.getTokenType() == IDENTIFIER) {
                builder.advanceLexer()
                objectNameMarker.done(OBJECT_NAME)
            } else {
                objectNameMarker.rollbackTo()
            }

            val objectDefinitionMark = builder.mark()
            // advance past our BRACE_L
            builder.advanceLexer()

            // parse any object internals, empty or otherwise
            objectInternals()

            if (builder.getTokenType() == BRACE_R) {
                // advance past our BRACE_R
                builder.advanceLexer()
                objectDefinitionMark.done(OBJECT_DEFINITION)
            } else {
                objectDefinitionMark.error(Message.OBJECT_NO_CLOSE)
            }
            return true
        } else {
            // not an objectDefinition
            return false
        }
    }

    /**
     * list -> "[" (value ",")* value? "]"
     */
    private fun list(): Boolean {
        if (builder.getTokenType() == BRACKET_L) {
            // advance past the BRACKET_L
            builder.advanceLexer()
            val listMark = builder.mark()

            while (builder.getTokenType() != BRACKET_R) {
                val valueMark = builder.mark()
                if (value()) {
                    valueMark.done(VALUE)
                } else {
                    TODO("need a legal value here, make a nice error for this")
                }

                if (builder.getTokenType() == COMMA) {
                    // advance past the COMMA
                    builder.advanceLexer()
                    continue
                } else {
                    // no more values
                    break
                }
            }

            if (builder.getTokenType() == BRACKET_R) {
                // just closed a well-formed list
                listMark.done(LIST)
                // advance past the BRACKET_R
                builder.advanceLexer()
            } else {
                listMark.error(Message.LIST_NO_CLOSE)
            }
            return true
        } else {
            // not a list
            return false
        }
    }

    /**
     * keyword -> ( IDENTIFIER | STRING ) ":" ;
     */
    private fun keyword(): Boolean {
        if ((builder.getTokenType() == IDENTIFIER || builder.getTokenType() == STRING)
            && builder.lookAhead(1) == COLON
        ) {
            val keywordMark = builder.mark()
            builder.advanceLexer()

            // advance past the COLON
            builder.advanceLexer()
            keywordMark.done(KEYWORD)
            return true
        } else {
            // not a keyword
            return false
        }
    }

    /**
     * literal -> STRING | NUMBER | "true" | "false" | "null" ;
     */
    private fun literal(): Boolean {
        if (setOf(
                STRING,
                IDENTIFIER,
                NUMBER,
                TRUE,
                FALSE,
                NULL
            ).any { it == builder.getTokenType() }
        ) {
            // consume our literal
            builder.advanceLexer()
            return true
        } else {
            return false
        }
    }

    /**
     * embeddedBlock -> "```" (embedTag) NEWLINE CONTENT "```" ;
     */
    private fun embedBlock(): Boolean {
        if (builder.getTokenType() == EMBED_START) {
            // advance past the EMBED_START
            builder.advanceLexer()
            val embedBlockMark = builder.mark()
            if (builder.getTokenType() == EMBED_TAG) {
                // advance past our embed optional tag
                builder.advanceLexer()
            }

            if (builder.getTokenType() == EMBED_CONTENT && builder.lookAhead(1) == EMBED_END) {
                builder.advanceLexer()
                builder.advanceLexer()
                embedBlockMark.done(EMBED_BLOCK)
            } else if (builder.getTokenType() == EMBED_END) {
                // empty embed block is also legal
                builder.advanceLexer()
            } else {
                throw RuntimeException("Unexpected error: the lexer should have ensured this structue was correct")
            }
            return true
        } else {
            // not an embedBlock
            return false
        }
    }

    /**
     * Returns true if there is still un-parsed content in [builder], logging a message
     * to [messageSink] if it finds unexpected content.  Should only be called to validate the state
     * of [builder] after a successful parse
     */
    private fun hasUnexpectedTrailingContent(): Boolean {
        if (builder.eof()) {
            // all good: every token has been consumed
            return false
        } else {
            // mark the unexpected content
            val unexpectedContentMark = builder.mark()
            while (!builder.eof()) {
                builder.advanceLexer()
            }
            unexpectedContentMark.error(Message.EOF_NOT_REACHED)
            return true
        }
    }
}

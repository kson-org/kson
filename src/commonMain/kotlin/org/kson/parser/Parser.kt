package org.kson.parser

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
 * embeddedBlock -> EMBED_START (embedTag) NEWLINE CONTENT EMBED_END ;
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
        if (objectInternals(false) || value()) {
            if (hasUnexpectedTrailingContent()) {
                // parser todo we likely want to see if we can contiue parsing after adding the error noting
                //             the unexpected extra content
            }
        }
    }

    /**
     * objectInternals -> ( keyword value ","? )* ;
     */
    private fun objectInternals(allowEmpty: Boolean): Boolean {
        var foundProperties = false

        val objectInternalsMark = builder.mark()
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

        if (foundProperties || allowEmpty)  {
            objectInternalsMark.done(OBJECT_INTERNALS)
        } else {
            objectInternalsMark.rollbackTo()
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
        if (objectDefinition()
            || list()
            || literal()
            || embedBlock()
        ) {
            return true
        } else {
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
            val objectDefinitionMark = builder.mark()
            val objectNameMarker = builder.mark()
            if (builder.getTokenType() == IDENTIFIER) {
                builder.advanceLexer()
            }
            // mark out object name even though it may be empty so the resulting marker tree
            // is consistent in both cases
            objectNameMarker.done(OBJECT_NAME)

            // advance past our BRACE_L
            builder.advanceLexer()

            // parse any object internals, empty or otherwise
            objectInternals(true)

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
            val listMark = builder.mark()
            // advance past the BRACKET_L
            builder.advanceLexer()

            while (builder.getTokenType() != BRACKET_R) {
                value()
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
                // advance past the BRACKET_R
                builder.advanceLexer()
                // just closed a well-formed list
                listMark.done(LIST)
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
        val elementType = builder.getTokenType()
        if ((elementType == IDENTIFIER || elementType == STRING)
            && builder.lookAhead(1) == COLON
        ) {
            val keywordMark = builder.mark()
            builder.advanceLexer()
            keywordMark.done(elementType)

            // advance past the COLON
            builder.advanceLexer()
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
        val literalMark = builder.mark()
        val elementType = builder.getTokenType()
        if (elementType != null && setOf(
                STRING,
                IDENTIFIER,
                NUMBER,
                TRUE,
                FALSE,
                NULL
            ).any { it == elementType }
        ) {
            // consume our literal
            builder.advanceLexer()
            literalMark.done(elementType)
            return true
        } else {
            literalMark.rollbackTo()
            return false
        }
    }

    /**
     * embeddedBlock -> "%%" (embedTag) NEWLINE CONTENT "%%" ;
     */
    private fun embedBlock(): Boolean {
        if (builder.getTokenType() == EMBED_START) {
            // advance past the EMBED_START
            builder.advanceLexer()
            val embedBlockMark = builder.mark()
            val embedTagMark = builder.mark()
            if (builder.getTokenType() == EMBED_TAG) {
                // advance past our optional embed tag
                builder.advanceLexer()
            }
            embedTagMark.done(EMBED_TAG)

            val embedBlockContentMark = builder.mark()
            if (builder.getTokenType() == EMBED_CONTENT) {
                // advance past our EMBED_CONTENT
                builder.advanceLexer()
            }
            embedBlockContentMark.done(EMBED_CONTENT)
            embedBlockMark.done(EMBED_BLOCK)
            if (builder.getTokenType() == EMBED_END) {
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
     * Returns true if there is still un-parsed content in [builder], marking an
     * error if it finds unexpected content.  Should only be called to validate the state
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

package org.kson.parser

import org.kson.parser.ParsedElementType.*
import org.kson.parser.TokenType.*
import org.kson.parser.messages.MessageType.*

/**
 * Default maximum nesting of objects and lists to allow in parsing.  This protects against
 * excessive nesting crashing the parser.  This default was chosen somewhat arbitraily,
 * and may not be appropriate for all platforms.  If/when we have issues here, let's tweak
 * as needed (note that this may also be configured in calls to [Parser.parse])
 */
const val DEFAULT_MAX_NESTING_LEVEL = 255

/**
 * Used to bail out of parsing when when excessive nesting is detected
 */
class ExcessiveNestingException: RuntimeException()

/**
 * Defines the Kson parser, implemented as a recursive descent parser which directly implements
 * the following grammar, one method per grammar rule:
 *
 * (Note: UPPERCASE names are terminals, and correspond to [TokenType]s produced by [Lexer])
 * ```
 * kson -> (objectInternals | value) <end-of-file> ;
 * objectInternals -> "," ( keyword value ","? )+
 *              | ( ","? keyword value )*
 *              | ( keyword value ","? )*
 * value -> objectDefinition
 *        | list
 *        | literal
 *        | embedBlock ;
 * objectDefinition -> ( objectName | "" ) "{" objectInternals "}" ;
 * list -> dashList | bracketList
 * # NOTE: dashList may not be (directly) contained in a dashList to avoid ambiguity
 * dashList -> ( LIST_DASH ( value | bracketList ) )*
 * # note that either list type may be contained in a bracket list since there is no ambiguity
 * bracketList -> "[" "," ( value ","? )+ "]"
 *              | "[" ( ","? value )* "]"
 *              | "[" ( value ","? )* "]"
 * keyword -> ( IDENTIFIER | string ) ":" ;
 * literal -> string | IDENTIFIER | NUMBER | "true" | "false" | "null" ;
 * string -> STRING_QUOTE STRING STRING_QUOTE
 * embeddedBlock -> EMBED_START (embedTag) NEWLINE CONTENT EMBED_END ;
 * ```
 *
 * See [section 5.1 here](https://craftinginterpreters.com/representing-code.html#context-free-grammars)
 * for details on this grammar notation.
 *
 * See [section 6.2 here](https://craftinginterpreters.com/parsing-expressions.html#recursive-descent-parsing)
 * for excellent context on a similar style of hand-crafted recursive descent parser
 *
 * @param builder the [AstBuilder] to run this parser on, see [AstBuilder] for more details
 * @param maxNestingLevel the maximum nesting level of objects and lists to allow in parsing
 *   TODO make maxNestingLevel part of a more holistic approach to configuring the parser
 *     if/when we have more dials we want to expose to the user
 */
class Parser(private val builder: AstBuilder, private val maxNestingLevel: Int = DEFAULT_MAX_NESTING_LEVEL) {

    /**
     * kson -> (objectInternals | value) <end-of-file> ;
     */
    fun parse() {
        val rootMarker = builder.mark()
        try {
            if (objectInternals(false) || value()) {
                if (hasUnexpectedTrailingContent()) {
                    // parser todo we likely want to see if we can continue parsing after adding the error noting
                    //             the unexpected extra content
                }
            }
        } catch (nestingException: ExcessiveNestingException) {
            // the value described by this kson document is too deeply nested, so we
            // reset all parsing and mark the whole document with our nesting error
            rootMarker.rollbackTo()
            val nestedExpressionMark = builder.mark()
            while(!builder.eof()) {
                builder.advanceLexer()
            }
            nestedExpressionMark.error(MAX_NESTING_LEVEL_EXCEEDED.create(maxNestingLevel.toString()))
            return
        }
        rootMarker.drop()
    }

    /**
     * objectInternals -> "," ( keyword value ","? )+
     *              | ( ","? keyword value )*
     *              | ( keyword value ","? )*
     */
    private fun objectInternals(allowEmpty: Boolean): Boolean = nestingTracker.nest {
        var foundProperties = false

        val objectInternalsMark = builder.mark()

        // parse the optional leading comma
        if (builder.getTokenType() == COMMA) {
            val leadingCommaMark = builder.mark()
            processComma(builder)

            // prohibit the empty-ISH objects internals containing just commas
            if (builder.getTokenType() == BRACE_R || builder.eof()) {
                leadingCommaMark.error(EMPTY_COMMAS.create())
                objectInternalsMark.done(OBJECT_INTERNALS)
                return@nest true
            } else {
                leadingCommaMark.drop()
            }
        }

        while (true) {
            val propertyMark = builder.mark()
            val keywordMark = builder.mark()
            if (keyword()) {
                val valueMark = builder.mark()

                // if we're followed by another keyword or we don't have a value, we're malformed
                if (keyword() || !value()) {
                    valueMark.rollbackTo()
                    keywordMark.error(OBJECT_KEY_NO_VALUE.create())
                } else {
                    // otherwise we've a well-behaved key:value property
                    valueMark.drop()
                    keywordMark.drop()
                }
                foundProperties = true
                if (builder.getTokenType() == COMMA) {
                    processComma(builder)
                }
                propertyMark.done(OBJECT_PROPERTY)
            } else {
                keywordMark.drop()
                propertyMark.rollbackTo()
                break
            }
        }

        if (foundProperties || allowEmpty) {
            objectInternalsMark.done(OBJECT_INTERNALS)
        } else {
            objectInternalsMark.rollbackTo()
        }

        return@nest foundProperties
    }

    private fun processComma(builder: AstBuilder) {
        val commaMark = builder.mark()
        // advance past the optional COMMA
        builder.advanceLexer()

        // look for extra "empty" commas
        if (builder.getTokenType() == COMMA) {
            while (builder.getTokenType() == COMMA) {
                builder.advanceLexer()
            }
            commaMark.error(EMPTY_COMMAS.create())
        } else {
            commaMark.drop()
        }
    }

    /**
     * value -> objectDefinition
     *        | list
     *        | literal
     *        | embedBlock ;
     */
    private fun value(): Boolean {
        return (objectDefinition()
                || list()
                || literal()
                || embedBlock())
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
                objectDefinitionMark.error(OBJECT_NO_CLOSE.create())
            }
            return true
        } else {
            // not an objectDefinition
            return false
        }
    }

    /**
     * list -> dashList | bracketList
     */
    private fun list(): Boolean {
        return dashList() || bracketList()
    }

    /**
     * dashList -> ( LIST_DASH ( value | bracketList ) )*
     */
    private fun dashList(): Boolean = nestingTracker.nest {
        if (builder.getTokenType() == LIST_DASH) {
            val listMark = builder.mark()

            // parse the dash delimited list elements
            do {
                val listElementMark = builder.mark()
                // advance past the LIST_DASH
                builder.advanceLexer()

                if (builder.getTokenType() == LIST_DASH) {
                    listElementMark.error(DANGLING_LIST_DASH.create())
                } else if (value() || bracketList()) {
                    // this LIST_DASH is not dangling
                    listElementMark.done(LIST_ELEMENT)
                } else {
                    listElementMark.error(DANGLING_LIST_DASH.create())
                }
            } while (builder.getTokenType() == LIST_DASH)

            listMark.done(LIST)
            return@nest true
        }
        return@nest false
    }

    /**
     * bracketList -> "[" "," ( value ","? )+ "]"
     *              | "[" ( ","? value )* "]"
     *              | "[" ( value ","? )* "]"
     */
    private fun bracketList(): Boolean = nestingTracker.nest {
        if (builder.getTokenType() == BRACKET_L) {
            val listMark = builder.mark()
            // advance past the BRACKET_L
            builder.advanceLexer()

            // parse the optional leading comma
            if (builder.getTokenType() == COMMA) {
                val leadingCommaMark = builder.mark()
                processComma(builder)

                // prohibit the empty-ISH list "[,]"
                if (builder.getTokenType() == BRACKET_R) {
                    // advance past the BRACKET_R
                    builder.advanceLexer()
                    leadingCommaMark.error(EMPTY_COMMAS.create())
                    listMark.done(LIST)
                    return@nest true
                } else {
                    leadingCommaMark.drop()
                }
            }

            while (builder.getTokenType() != BRACKET_R && !builder.eof()) {
                val listElementMark = builder.mark()

                if (!value()) {
                    val invalidElementMark = builder.mark()

                    // give a more precise/helpful error if it looks the user is accidentally sticking object notation in a list
                    if (builder.getTokenType() == COLON) {
                        builder.advanceLexer()
                        invalidElementMark.error(LIST_STRAY_COLON.create())
                    } else {
                        // while we're not obviously another element or at the end of the list, mark this non-value as an error
                        while(builder.getTokenType() != BRACKET_R
                            && builder.getTokenType() != COMMA
                            && !builder.eof()) {
                            builder.advanceLexer()
                        }
                        invalidElementMark.error(LIST_INVALID_ELEM.create())
                    }
                }
                if (builder.getTokenType() == COMMA) {
                    processComma(builder)

                    listElementMark.done(LIST_ELEMENT)
                    continue
                } else {
                    listElementMark.done(LIST_ELEMENT)
                }
            }

            if (builder.getTokenType() == BRACKET_R) {
                // advance past the BRACKET_R
                builder.advanceLexer()
                // just closed a well-formed list
                listMark.done(LIST)
            } else {
                listMark.error(LIST_NO_CLOSE.create())
            }
            return@nest true
        } else {
            // not a list
            return@nest false
        }
    }

    /**
     * keyword -> ( IDENTIFIER | string ) ":" ;
     */
    private fun keyword(): Boolean {
        // try to parse a keyword in the style of "IDENTIFIER followed by :"
        if (builder.getTokenType() == IDENTIFIER && builder.lookAhead(1) == COLON) {
            val keywordMark = builder.mark()
            val identifierMark = builder.mark()
            builder.advanceLexer()
            identifierMark.done(IDENTIFIER)
            keywordMark.done(KEYWORD)

            // advance past the COLON
            builder.advanceLexer()
            return true
        }

        // try to parse a keyword in the style of "string followed by :"
        val keywordMark = builder.mark()
        if (string() && builder.getTokenType() == COLON) {
            keywordMark.done(KEYWORD)

            // advance past the COLON
            builder.advanceLexer()
            return true
        } else {
            keywordMark.rollbackTo()
        }

        // not a keyword
        return false
    }

    /**
     * literal -> string | IDENTIFIER | NUMBER | "true" | "false" | "null" ;
     */
    private fun literal(): Boolean {
        if (string()) {
            return true
        }

        val elementType = builder.getTokenType()

        if (elementType == NUMBER) {
            val numberMark = builder.mark()
            val numberCandidate = builder.getTokenText()

            // consume this number candidate
            builder.advanceLexer()

            /**
             * delegate the details of number parsing to [NumberParser]
             */
            val numberParseResult = NumberParser(numberCandidate).parse()

            if (numberParseResult.error != null) {
                numberMark.error(numberParseResult.error)
            } else {
                numberMark.done(NUMBER)
            }
            return true
        }

        val terminalElementMark = builder.mark()
        if (elementType != null && setOf(
                IDENTIFIER,
                TRUE,
                FALSE,
                NULL
            ).any { it == elementType }
        ) {
            // consume our literal
            builder.advanceLexer()
            terminalElementMark.done(elementType)
            return true
        } else {
            terminalElementMark.rollbackTo()
            return false
        }
    }

    /**
     * string -> STRING_QUOTE STRING STRING_QUOTE
     */
    private fun string(): Boolean {
        if (builder.getTokenType() != STRING_QUOTE) {
            // not a string
            return false
        }

        val possiblyUnclosedString = builder.mark()
        // consume our open quote
        builder.advanceLexer()

        val stringMark = builder.mark()

        while (builder.getTokenType() != STRING_QUOTE && !builder.eof()) {
            if (builder.getTokenType() != STRING && builder.getTokenType() != STRING_ILLEGAL_CONTROL_CHARACTER) {
                throw Exception("Expected anything in STRING_QUOTEs to be Lexed as a STRING or STRING_ILLEGAL_CONTROL_CHARACTER")
            }

            if (builder.getTokenType() == STRING) {
                // consume the string
                builder.advanceLexer()
            }

            val controlCharacterMark = builder.mark()
            if (builder.getTokenType() == STRING_ILLEGAL_CONTROL_CHARACTER) {
                val badControlChar = builder.getTokenText()
                builder.advanceLexer()
                controlCharacterMark.error(STRING_CONTROL_CHARACTER.create(badControlChar))
            } else {
                controlCharacterMark.drop()
            }
        }

        stringMark.done(STRING)

        if (builder.eof()) {
            possiblyUnclosedString.error(STRING_NO_CLOSE.create())
            return true
        } else {
            // string is closed, don't need this marker
            possiblyUnclosedString.drop()
            // consume our close quote
            builder.advanceLexer()
            return true
        }
    }

    /**
     * embeddedBlock -> EMBED_START (embedTag) NEWLINE CONTENT EMBED_END ;
     */
    private fun embedBlock(): Boolean {
        if (builder.getTokenType() == EMBED_START) {
            val embedBlockMark = builder.mark()
            val embedStartDelimiter = builder.getTokenText()
            // advance past the EMBED_START
            builder.advanceLexer()
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
                embedBlockContentMark.done(EMBED_CONTENT)
            } else {
                // empty embed blocks are legal
                embedBlockContentMark.drop()
            }

            if (builder.getTokenType() == EMBED_END) {
                builder.advanceLexer()
                embedBlockMark.done(EMBED_BLOCK)
            } else if (builder.eof()) {
                embedBlockMark.error(EMBED_BLOCK_NO_CLOSE.create(embedStartDelimiter))
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
            unexpectedContentMark.error(EOF_NOT_REACHED.create())
            return true
        }
    }

    private var nestingTracker = object {
        private var nestingLevel = 0

        /**
         * "Aspect"-style function to wrap the list and object functions in [Parser] which may recursively nest
         * so we can clearly/consistently track nesting and detect excessive nesting
         */
        fun nest(nestingParserFunction: () -> Boolean): Boolean {
            nestingLevel++
            if (nestingLevel > maxNestingLevel) {
                throw ExcessiveNestingException()
            }
            val parseResult = nestingParserFunction()
            nestingLevel--
            return parseResult
        }
    }
}

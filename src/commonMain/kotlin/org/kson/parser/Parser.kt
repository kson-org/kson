package org.kson.parser

import org.kson.parser.ParsedElementType.*
import org.kson.parser.TokenType.*
import org.kson.parser.messages.MessageType.*

/**
 * Defines the Kson parser, implemented as a recursive descent parser which directly implements
 * the following grammar, one method per grammar rule:
 *
 * (Note: UPPERCASE names are terminals, and correspond to [TokenType]s produced by [Lexer])
 * ```
 * root -> ksonValue <end-of-file>
 * ksonValue -> plainObject
 *            | dashList
 *            | delimitedValue
 * plainObject -> objectInternals "."+
 * objectInternals -> "," ( keyword ksonValue ","? )+
 *                  | ( ","? keyword ksonValue )*
 *                  | ( keyword ksonValue ","? )*
 * dashList -> dashListInternals "="+
 * dashListInternals -> ( LIST_DASH ksonValue )*
 * delimitedValue -> delimitedObject
 *                 | delimitedDashList
 *                 | bracketList
 *                 | literal
 *                 | embedBlock
 * delimitedObject -> "{" objectInternals "}"
 * delimitedDashList -> "<" dashListInternals ">"
 * bracketList -> "[" "," ( ksonValue ","? )+ "]"
 *              | "[" ( ","? ksonValue )* "]"
 *              | "[" ( ksonValue ","? )* "]"
 * literal -> string | NUMBER | IDENTIFIER | "true" | "false" | "null"
 * keyword -> ( IDENTIFIER | string ) ":"
 * string -> STRING_OPEN_QUOTE STRING STRING_CLOSE_QUOTE
 * embedBlock -> EMBED_OPEN_DELIM (EMBED_TAG) EMBED_PREAMBLE_NEWLINE CONTENT EMBED_CLOSE_DELIM
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
     * root -> ksonValue <end-of-file>
     */
    fun parse() {
        if (builder.eof()) {
            // empty file, nothing to do
            return
        }

        val rootMarker = builder.mark()
        try {
            if (ksonValue()) {
                if (hasUnexpectedTrailingContent()) {
                    // parser todo we likely want to see if we can continue parsing after adding the error noting
                    //             the unexpected extra content
                }
                rootMarker.drop()
            } else {
                /**
                 * If we did not consume tokens all they way up to EOF, then we have a bug in how we handle this case
                 * (and how we report helpful errors for it).  Fail loudly so it gets fixed.
                 */
                if (!builder.eof()) {
                    throw RuntimeException("Bug: this parser must consume all tokens in all cases, but failed in this case.")
                } else {
                    rootMarker.drop()
                }
            }
        } catch (nestingException: ExcessiveNestingException) {
            // the value described by this kson document is too deeply nested, so we
            // reset all parsing and mark the whole document with our nesting error
            rootMarker.rollbackTo()
            val nestedExpressionMark = builder.mark()
            while (!builder.eof()) {
                builder.advanceLexer()
            }
            nestedExpressionMark.error(MAX_NESTING_LEVEL_EXCEEDED.create(maxNestingLevel.toString()))
        }
    }

    /**
     * ksonValue -> plainObject
     *           | dashList
     *           | delimitedValue
     */
    private fun ksonValue(): Boolean = plainObject(false) || dashList() || delimitedValue()

    /**
     * plainObject -> objectInternals "."+
     * objectInternals -> "," ( keyword ksonValue ","? )+
     *                  | ( ","? keyword ksonValue )*
     *                  | ( keyword ksonValue ","? )*
     *
     * Note: as in [dashList], we combine these two grammar rules here so it's clean/easy to implement
     *   make a more friendly parse for users by giving warnings when end-dots are used in a delimited list
     */
    private fun plainObject(allowEmpty: Boolean, isDelimited: Boolean = false): Boolean = nestingTracker.nest {
        var foundProperties = false

        val objectMark = builder.mark()

        // parse the optional leading comma
        if (builder.getTokenType() == COMMA) {
            val leadingCommaMark = builder.mark()
            processComma(builder)

            // prohibit the empty-ISH objects internals containing just commas
            if (builder.getTokenType() == CURLY_BRACE_R || builder.eof()) {
                leadingCommaMark.error(EMPTY_COMMAS.create())
                objectMark.done(OBJECT)
                return@nest true
            } else {
                leadingCommaMark.drop()
            }
        }

        while (true) {
            val propertyMark = builder.mark()
            val keywordMark = builder.mark()
            if (keyword()) {
                if (builder.getTokenType() == CURLY_BRACE_R) {
                    // object got closed before giving this keyword a value
                    keywordMark.error(OBJECT_KEY_NO_VALUE.create())
                    propertyMark.done(OBJECT_PROPERTY)
                    break
                }

                val valueMark = builder.mark()

                if (!ksonValue()) {
                    // if we don't have a value, we're malformed
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

                if (builder.getTokenType() == DOT) {
                    val dotMark = builder.mark()
                    builder.advanceLexer()
                    if (!isDelimited) {
                        dotMark.drop()
                        break
                    } else {
                        dotMark.error(IGNORED_OBJECT_END_DOT.create())
                    }
                }
            } else {
                keywordMark.drop()
                propertyMark.rollbackTo()
                break
            }
        }

        if (foundProperties || allowEmpty) {
            objectMark.done(OBJECT)
            return@nest true
        } else {
            // otherwise we're not a valid object internals
            objectMark.rollbackTo()
            return@nest false
        }
    }

    /**
     * dashList -> dashListInternals "="+
     * dashListInternals -> ( LIST_DASH ksonValue )*
     *
     * Note: as in [plainObject], we combine these two grammar rules here so it's clean/easy to implement
     *   make a more friendly parse for users by giving warnings when end-dots are used in a delimited list
     */
    private fun dashList(isDelimited: Boolean = false): Boolean = nestingTracker.nest {
        if (builder.getTokenType() == LIST_DASH) {
            val listMark = builder.mark()

            // parse the dash delimited list elements
            do {
                val listElementMark = builder.mark()
                // advance past the LIST_DASH
                builder.advanceLexer()

                if (ksonValue()) {
                    // this LIST_DASH is not dangling
                    listElementMark.done(LIST_ELEMENT)
                } else {
                    listElementMark.error(DANGLING_LIST_DASH.create())
                }

                if (builder.getTokenType() == END_DASH) {
                    val endDashMark = builder.mark()
                    builder.advanceLexer()
                    if (!isDelimited) {
                        endDashMark.drop()
                        break
                    } else {
                        endDashMark.error(IGNORED_DASH_LIST_END_DASH.create())
                    }
                }
            } while (builder.getTokenType() == LIST_DASH)

            if (!isDelimited) {
                listMark.done(DASH_LIST)
            } else {
                listMark.drop()
            }
            return@nest true
        } else {
            return@nest false
        }
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
     * delimitedValue -> delimitedObject
     *                 | delimitedDashList
     *                 | bracketList
     *                 | literal
     *                 | embedBlock
     */
    private fun delimitedValue(): Boolean {
        if (builder.getTokenType() == CURLY_BRACE_R) {
            val badCloseBrace = builder.mark()
            builder.advanceLexer()
            badCloseBrace.error(OBJECT_NO_OPEN.create())
            return true
        }

        if (builder.getTokenType() == SQUARE_BRACKET_R) {
            val badCloseBrace = builder.mark()
            builder.advanceLexer()
            badCloseBrace.error(LIST_NO_OPEN.create())
            return true
        }

        if (builder.getTokenType() == ANGLE_BRACKET_R) {
            val badCloseBrace = builder.mark()
            builder.advanceLexer()
            badCloseBrace.error(LIST_NO_OPEN.create())
            return true
        }

        if (builder.getTokenType() == ILLEGAL_CHAR) {
            val illegalCharMark = builder.mark()
            val illegalChars = ArrayList<String>()
            while (builder.getTokenType() == ILLEGAL_CHAR) {
                illegalChars.add(builder.getTokenText())
                builder.advanceLexer()
            }
            illegalCharMark.error(ILLEGAL_CHARACTERS.create(illegalChars.joinToString()))
            // note that we allow parsing to continue — we'll act like these illegal chars aren't here in the hopes
            // of making sense of everything else
        }

        return (delimitedObject()
                || delimitedDashList()
                || bracketList()
                || literal()
                || embedBlock())
    }

    /**
     * delimitedObject -> "{" objectInternals "}"
     */
    private fun delimitedObject(): Boolean {
        if (builder.getTokenType() != CURLY_BRACE_L) {
            // no open curly brace, so not a delimitedObject
            return false
        }

        val delimitedObjectMark = builder.mark()

        // advance past our CURLY_BRACE_L
        builder.advanceLexer()

        // parse any object internals, empty or otherwise
        plainObject(allowEmpty = true, isDelimited = true)

        // annotate anything unparsable within this object definition with an error
        while (builder.getTokenType() != CURLY_BRACE_R && !builder.eof()) {
            val malformedInternals = builder.mark()

            while (builder.getTokenType() != CURLY_BRACE_R && !builder.eof()) {
                builder.advanceLexer()
                val keywordMark = builder.mark()
                if (keyword()) {
                    keywordMark.rollbackTo()
                    break
                } else {
                    keywordMark.drop()
                }
            }

            malformedInternals.error(OBJECT_BAD_INTERNALS.create())

            // try to parse more valid object internals so we're only marking OBJECT_BAD_INTERNALS
            // on internals that are actually bad
            plainObject(allowEmpty = false, isDelimited = true)
        }

        if (builder.getTokenType() == CURLY_BRACE_R) {
            // advance past our CURLY_BRACE_R
            builder.advanceLexer()
            delimitedObjectMark.drop()
        } else {
            delimitedObjectMark.error(OBJECT_NO_CLOSE.create())
        }
        return true
    }

    /**
     * delimitedDashList -> "<" dashListInternals ">"
     */
    private fun delimitedDashList(): Boolean {
        if (builder.getTokenType() != ANGLE_BRACKET_L) {
            // no open angle bracket, so not a delimitedDashList
            return false
        }

        val listMark = builder.mark()

        // consume our ANGLE_BRACKET_L
        builder.advanceLexer()

        dashList(true)

        if (builder.getTokenType() == ANGLE_BRACKET_R) {
            builder.advanceLexer()
            listMark.done(DASH_DELIMITED_LIST)
        } else {
            listMark.error(LIST_NO_CLOSE.create())
        }

        return true
    }

    /**
     * bracketList -> "[" "," ( ksonValue ","? )+ "]"
     *              | "[" ( ","? ksonValue )* "]"
     *              | "[" ( ksonValue ","? )* "]"
     */
    private fun bracketList(): Boolean = nestingTracker.nest {
        if (builder.getTokenType() != SQUARE_BRACKET_L) {
            // no open square bracket, so not a bracketList
            return@nest false
        }
        val listMark = builder.mark()
        // advance past the SQUARE_BRACKET_L
        builder.advanceLexer()

        // parse the optional leading comma
        if (builder.getTokenType() == COMMA) {
            val leadingCommaMark = builder.mark()
            processComma(builder)

            // prohibit the empty-ISH list "[,]"
            if (builder.getTokenType() == SQUARE_BRACKET_R) {
                // advance past the SQUARE_BRACKET_R
                builder.advanceLexer()
                leadingCommaMark.error(EMPTY_COMMAS.create())
                listMark.done(BRACKET_LIST)
                return@nest true
            } else {
                leadingCommaMark.drop()
            }
        }

        while (builder.getTokenType() != SQUARE_BRACKET_R && !builder.eof()) {
            val listElementMark = builder.mark()

            if (!ksonValue()) {
                val invalidElementMark = builder.mark()

                // while we're not obviously another element or at the end of the list, mark this non-value as an error
                while (builder.getTokenType() != SQUARE_BRACKET_R
                    && builder.getTokenType() != COMMA
                    && !builder.eof()
                ) {
                    builder.advanceLexer()
                }
                invalidElementMark.error(LIST_INVALID_ELEM.create())
            }
            if (builder.getTokenType() == COMMA) {
                processComma(builder)

                listElementMark.done(LIST_ELEMENT)
                continue
            } else {
                listElementMark.done(LIST_ELEMENT)
            }
        }

        if (builder.getTokenType() == SQUARE_BRACKET_R) {
            // advance past the SQUARE_BRACKET_R
            builder.advanceLexer()
            // just closed a well-formed list
            listMark.done(BRACKET_LIST)
        } else {
            listMark.error(LIST_NO_CLOSE.create())
        }
        return@nest true
    }

    /**
     * literal -> string | NUMBER | IDENTIFIER | "true" | "false" | "null"
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
     * keyword -> ( IDENTIFIER | string ) ":"
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
     * string -> STRING_OPEN_QUOTE STRING STRING_CLOSE_QUOTE
     */
    private fun string(): Boolean {
        if (builder.getTokenType() != STRING_OPEN_QUOTE) {
            // not a string
            return false
        }

        val stringMark = builder.mark()
        val possiblyUnclosedString = builder.mark()
        // consume our open quote
        builder.advanceLexer()

        while (builder.getTokenType() != STRING_CLOSE_QUOTE && !builder.eof()) {
            when (builder.getTokenType()) {
                STRING -> builder.advanceLexer()
                STRING_UNICODE_ESCAPE -> {
                    val unicodeEscapeMark = builder.mark()
                    val unicodeEscapeText = builder.getTokenText()
                    builder.advanceLexer()
                    if (isValidUnicodeEscape(unicodeEscapeText)) {
                        unicodeEscapeMark.drop()
                    } else {
                        unicodeEscapeMark.error(STRING_BAD_UNICODE_ESCAPE.create(unicodeEscapeText))
                    }
                }

                STRING_ESCAPE -> {
                    val stringEscapeMark = builder.mark()
                    val stringEscapeText = builder.getTokenText()
                    builder.advanceLexer()
                    if (isValidStringEscape(stringEscapeText)) {
                        stringEscapeMark.drop()
                    } else {
                        stringEscapeMark.error(STRING_BAD_ESCAPE.create(stringEscapeText))
                    }
                }

                STRING_ILLEGAL_CONTROL_CHARACTER -> {
                    val controlCharacterMark = builder.mark()
                    val badControlChar = builder.getTokenText()
                    builder.advanceLexer()
                    controlCharacterMark.error(STRING_CONTROL_CHARACTER.create(badControlChar))
                }

                else -> {
                    stringMark.rollbackTo()
                    return false
                }
            }
        }

        if (builder.eof()) {
            possiblyUnclosedString.error(STRING_NO_CLOSE.create())
        } else {
            // string is closed, don't need this marker
            possiblyUnclosedString.drop()
            // consume our close quote
            builder.advanceLexer()
        }

        stringMark.done(QUOTED_STRING)
        return true
    }

    private fun isValidStringEscape(stringEscapeText: String): Boolean {
        if (!stringEscapeText.startsWith('\\') || stringEscapeText.length > 2) {
            throw RuntimeException("Should only be asked to validate one-char string escapes, but was passed: $stringEscapeText")
        }

        // detect incomplete escapes (perhaps this escape bumped up against EOF)
        if (stringEscapeText.length == 1) {
            return false
        }

        val escapedChar = stringEscapeText[1]
        return validStringEscapes.contains(escapedChar)
    }

    private fun isValidUnicodeEscape(unicodeEscapeText: String): Boolean {
        if (!unicodeEscapeText.startsWith("\\u")) {
            throw RuntimeException("Should only be asked to validate unicode escapes")
        }

        // clip off the `\u` to make this code point easier to inspect
        val unicodeCodePoint = unicodeEscapeText.replaceFirst("\\u", "")

        if (unicodeCodePoint.length != 4) {
            // must have four chars
            return false
        }

        for (codePointChar in unicodeCodePoint) {
            if (!validHexChars.contains(codePointChar)) {
                return false
            }
        }

        return true
    }

    /**
     * embedBlock -> EMBED_OPEN_DELIM (EMBED_TAG) EMBED_PREAMBLE_NEWLINE CONTENT EMBED_CLOSE_DELIM
     */
    private fun embedBlock(): Boolean {
        if (builder.getTokenType() == EMBED_OPEN_DELIM || builder.getTokenType() == EMBED_DELIM_PARTIAL) {
            val embedBlockMark = builder.mark()
            val embedBlockStartDelimMark = builder.mark()

            val embedStartDelimiter = if (builder.getTokenType() == EMBED_DELIM_PARTIAL) {
                val delimChar = builder.getTokenText()
                builder.advanceLexer()
                embedBlockStartDelimMark.error(EMBED_BLOCK_DANGLING_DELIM.create(delimChar))
                "$delimChar$delimChar"
            } else {
                val delimText = builder.getTokenText()
                builder.advanceLexer()
                embedBlockStartDelimMark.done(EMBED_OPEN_DELIM)
                delimText
            }

            val embedTagMark = builder.mark()
            val embedTagText = if (builder.getTokenType() == EMBED_TAG) {
                val tagText = builder.getTokenText()
                // advance past our optional embed tag
                builder.advanceLexer()
                tagText
            } else {
                // no embed tag
                ""
            }
            embedTagMark.done(EMBED_TAG)

            val prematureEndMark = builder.mark()
            if (builder.getTokenType() == EMBED_CLOSE_DELIM) {
                builder.advanceLexer()
                /**
                 * We are seeing a closing [EMBED_CLOSE_DELIM] before we encountered an [EMBED_PREAMBLE_NEWLINE],
                 * so give an error to help the user fix this construct
                 */
                prematureEndMark.error(EMBED_BLOCK_NO_NEWLINE.create(embedStartDelimiter, embedTagText))
                embedBlockMark.done(EMBED_BLOCK)
                return true
            } else {
                prematureEndMark.drop()
            }

            if (builder.eof()) {
                embedBlockMark.error(EMBED_BLOCK_NO_CLOSE.create(embedStartDelimiter))
                return true
            } else if (builder.getTokenType() != EMBED_PREAMBLE_NEWLINE) {
                embedBlockMark.error(EMBED_BLOCK_NO_CLOSE.create(embedStartDelimiter))
                return true
            }

            // advance past our EMBED_PREAMBLE_NEWLINE
            builder.advanceLexer()

            val embedBlockContentMark = builder.mark()
            if (builder.getTokenType() == EMBED_CONTENT) {
                // advance past our EMBED_CONTENT
                builder.advanceLexer()
                embedBlockContentMark.done(EMBED_CONTENT)
            } else {
                // empty embed blocks are legal
                embedBlockContentMark.drop()
            }

            if (builder.getTokenType() == EMBED_CLOSE_DELIM) {
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

/**
 * Default maximum nesting of objects and lists to allow in parsing.  This protects against
 * excessive nesting crashing the parser.  This default was chosen somewhat arbitrarily,
 * and may not be appropriate for all platforms.  If/when we have issues here, let's tweak
 * as needed (note that this may also be configured in calls to [Parser.parse])
 */
const val DEFAULT_MAX_NESTING_LEVEL = 255

/**
 * Used to bail out of parsing when excessive nesting is detected
 */
class ExcessiveNestingException : RuntimeException()

/**
 * Enumerate the set of valid Kson string escapes for easy validation `\u` is also supported,
 * but is validated separately against [validHexChars]
 */
private val validStringEscapes = setOf('\'', '"', '\\', '/', 'b', 'f', 'n', 'r', 't')
private val validHexChars = setOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F'
)

package org.kson.parser

import org.kson.parser.ParsedElementType.*
import org.kson.parser.TokenType.*
import org.kson.parser.messages.MessageType.*
import org.kson.stdlibx.exceptions.FatalParseException

/**
 * Defines the Kson parser, implemented as a recursive descent parser which directly implements
 * the following grammar, one method per grammar rule:
 *
 * (Note: UPPERCASE names are terminals, and correspond to [TokenType]s produced by [Lexer])
 * ```
 * root -> ksonValue <end-of-file>
 * ksonValue -> dashList
 *            | plainObject
 *            | delimitedValue
 * dashList -> ( LIST_DASH ksonValue )+ "="?
 * plainObject -> ( keyword ksonValue )+ "."?
 * delimitedValue -> delimitedObject
 *                 | delimitedDashList
 *                 | bracketList
 *                 | literal
 *                 | embedBlock
 * delimitedDashList -> "<" dashListInternals ">"
 * dashListInternals -> ( LIST_DASH ksonValue )*
 * delimitedObject -> "{" objectInternals "}"
 * objectInternals -> "," ( keyword ksonValue ","? )+
 *                  | ( ","? keyword ksonValue )*
 *                  | ( keyword ksonValue ","? )*
 * bracketList -> "[" "," ( ksonValue ","? )+ "]"
 *              | "[" ( ","? ksonValue )* "]"
 *              | "[" ( ksonValue ","? )* "]"
 * literal -> string | NUMBER | "true" | "false" | "null"
 * keyword -> string ":"
 * string -> (STRING_OPEN_QUOTE STRING_CONTENT STRING_CLOSE_QUOTE) | UNQUOTED_STRING
 * embedBlock -> EMBED_OPEN_DELIM EMBED_PREAMBLE EMBED_PREAMBLE_NEWLINE CONTENT EMBED_CLOSE_DELIM
 * EMBED_PREAMBLE -> EMBED_TAG (":" EMBED_METADATA)?
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
            val containsKsonValue = ksonValue()
            handleUnexpectedTrailingContent(containsKsonValue)
            if (!builder.eof()) {
                /**
                 * [handleUnexpectedTrailingContent] should have ensured that all tokens are handled
                 */
                throw FatalParseException("Bug: this parser must consume all tokens in all cases")
            }
            rootMarker.drop()
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
    private fun ksonValue(): Boolean = plainObject() || dashList() || delimitedValue()

    /**
     * plainObject -> (keyword ksonValue)+ "."?
     */
    private fun plainObject(): Boolean = nestingTracker.nest {
        val objectMark = builder.mark()
        var foundProperties = false

        while (true) {
            val propertyMark = builder.mark()
            val keywordMark = builder.mark()
            if (keyword()) {
                foundProperties = true
                parseValueForKeyword(keywordMark)
                propertyMark.done(OBJECT_PROPERTY)
            } else {
                keywordMark.drop()
                propertyMark.rollbackTo()
                break
            }
        }

        // Check for an end-dot `.`
        if (builder.getTokenType() == DOT) {
            builder.advanceLexer()
        }

        if (foundProperties) {
            objectMark.done(OBJECT)
            return@nest true
        } else {
            // plain objects must have at least one property
            objectMark.rollbackTo()
            return@nest false
        }
    }

    /**
     * objectInternals -> "," ( keyword ksonValue ","? )+
     *                  | ( ","? keyword ksonValue )*
     *                  | ( keyword ksonValue ","? )*
     */
    private fun objectInternals(): Boolean = nestingTracker.nest {
        var foundProperties = false

        // parse the optional leading comma
        if (builder.getTokenType() == COMMA) {
            val leadingCommaMark = builder.mark()
            processComma(builder)

            // prohibit the empty-ISH objects internals containing just commas
            if (builder.getTokenType() == CURLY_BRACE_R || builder.eof()) {
                leadingCommaMark.error(EMPTY_COMMAS.create())
                return@nest true
            } else {
                leadingCommaMark.drop()
            }
        }

        while (true) {
            val propertyMark = builder.mark()
            val keywordMark = builder.mark()
            if (keyword()) {
                foundProperties = true

                if (builder.getTokenType() == CURLY_BRACE_R) {
                    // object got closed before giving this keyword a value
                    keywordMark.error(OBJECT_KEY_NO_VALUE.create())
                    propertyMark.done(OBJECT_PROPERTY)
                    break
                }

                parseValueForKeyword(keywordMark)
                if (builder.getTokenType() == COMMA) {
                    processComma(builder)
                }
                propertyMark.done(OBJECT_PROPERTY)

                if (builder.getTokenType() == DOT) {
                    val dotMark = builder.mark()
                    builder.advanceLexer()
                    dotMark.error(IGNORED_OBJECT_END_DOT.create())
                }
            } else {
                keywordMark.drop()
                propertyMark.rollbackTo()
                break
            }
        }

        return@nest foundProperties
    }

    /**
     * Helper method to centralize parsing and errors reporting to object values.
     *
     * NOTE: this method resolves the given [keywordMark] [AstMarker]
     */
    private fun parseValueForKeyword(keywordMark: AstMarker) {
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
    }

    /**
     * dashList          -> ( LIST_DASH ksonValue )+ "="?
     * dashListInternals -> ( LIST_DASH ksonValue )*
     *
     * Note: we combine these two grammar rules here to minimize code duplication, disambiguating by [isDelimited]
     */
    private fun dashList(isDelimited: Boolean = false): Boolean = nestingTracker.nest {
        if (builder.getTokenType() != LIST_DASH) {
            return@nest false
        }

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

        if (builder.getTokenType() == CURLY_BRACE_R) {
            // looking at empty object, advance past our end brace and mark it
            builder.advanceLexer()
            delimitedObjectMark.done(OBJECT)
            return true
        }

        // parse our object internals
        objectInternals()

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
            objectInternals()
        }

        if (builder.getTokenType() == CURLY_BRACE_R) {
            // advance past our CURLY_BRACE_R
            builder.advanceLexer()
            delimitedObjectMark.done(OBJECT)
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

                var containsInvalidElement = false
                while (builder.getTokenType() != SQUARE_BRACKET_R &&
                    builder.getTokenType() != COMMA &&
                    !builder.eof()
                ) {
                    builder.advanceLexer()
                    containsInvalidElement = true
                }

                if (containsInvalidElement) {
                    invalidElementMark.error(LIST_INVALID_ELEM.create())
                } else {
                    invalidElementMark.drop()
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
     * literal -> string | NUMBER | "true" | "false" | "null"
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
     * keyword -> string ":"
     */
    private fun keyword(): Boolean {
        val keywordMark = builder.mark()

        // helpful errors for keywords that clash with reserved words
        if ((builder.getTokenType() == NULL
                    || builder.getTokenType() == TRUE
                    || builder.getTokenType() == FALSE
                ) && builder.lookAhead(1) == COLON) {
            val reservedWord = builder.getTokenText()
            builder.advanceLexer()
            keywordMark.error(OBJECT_KEYWORD_RESERVED_WORD.create(reservedWord))

            // advance past the COLON
            builder.advanceLexer()
            return true
        }

        // try to parse a keyword in the style of "string followed by :"
        if (string() && builder.getTokenType() == COLON) {
            keywordMark.done(OBJECT_KEY)

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
     * string -> (STRING_OPEN_QUOTE STRING STRING_CLOSE_QUOTE) | UNQUOTED_STRING
     */
    private fun string(): Boolean {
        if (builder.getTokenType() == UNQUOTED_STRING) {
            val unquotedStringMark = builder.mark()
            builder.advanceLexer()
            unquotedStringMark.done(UNQUOTED_STRING)
            return true
        }

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
                STRING_CONTENT -> builder.advanceLexer()
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
            throw FatalParseException("Should only be asked to validate one-char string escapes, but was passed: $stringEscapeText")
        }

        // detect incomplete escapes (perhaps this escape bumped up against EOF)
        if (stringEscapeText.length == 1) {
            return false
        }

        val escapedChar = stringEscapeText[1]
        return validStringEscapes.contains(escapedChar)
    }

    /**
     * EMBED_PREAMBLE -> EMBED_TAG (":" EMBED_METADATA)?
     *
     * Note: the preamble may be empty, so this always "succeeds" in parsing its rule
     * @return the text of the parsed embed preamble (possibly empty)
     */
    private fun embedPreamble(): String {
        val embedTagMark = builder.mark()
        val embedTag = if (builder.getTokenType() == EMBED_TAG) {
            val tagText = builder.getTokenText()
            builder.advanceLexer()
            embedTagMark.done(EMBED_TAG)
            
            // Check for optional meta tag
            val embedMeta = if (builder.getTokenType() == EMBED_TAG_STOP) {
                val embedTagDelim = builder.mark()
                builder.advanceLexer()
                embedTagDelim.done(EMBED_TAG_STOP)

                val metaTagMark = builder.mark()
                val metaText = builder.getTokenText()
                builder.advanceLexer()
                metaTagMark.done(EMBED_METADATA)
                metaText
            } else {
                ""
            }
            
            // Combine tags if both present
            if (embedMeta.isNotEmpty()) {
                "$tagText:$embedMeta"
            } else {
                tagText
            }
        } else {
            embedTagMark.drop()
            ""
        }
        
        return embedTag
    }

    private fun isValidUnicodeEscape(unicodeEscapeText: String): Boolean {
        if (!unicodeEscapeText.startsWith("\\u")) {
            throw FatalParseException("Should only be asked to validate unicode escapes")
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
        if (builder.getTokenType() == EMBED_OPEN_DELIM) {
            val embedBlockMark = builder.mark()
            
            val embedBlockStartDelimMark = builder.mark()
            val embedStartDelimiter = builder.getTokenText()
            builder.advanceLexer()
            embedBlockStartDelimMark.done(EMBED_OPEN_DELIM)

            val embedPreambleText = embedPreamble()

            val prematureEndMark = builder.mark()
            if (builder.getTokenType() == EMBED_CLOSE_DELIM) {
                builder.advanceLexer()
                /**
                 * We are seeing a closing [EMBED_CLOSE_DELIM] before we encountered an [EMBED_PREAMBLE_NEWLINE],
                 * so give an error to help the user fix this construct
                 */
                prematureEndMark.error(EMBED_BLOCK_NO_NEWLINE.create(embedStartDelimiter, embedPreambleText))
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
     * Handle any un-parsed content in [builder], marking it in error if found.
     * Should only be called to validate the state of [builder] after a successful parse
     */
    private fun handleUnexpectedTrailingContent(containsKsonValue: Boolean) {
        // get a sequence of all unexpectedTrailingContent until the end of the file.
        generateSequence { builder.takeIf { !it.eof() } }
            .forEachIndexed { index, _ ->
                // mark the unexpected content
                val unexpectedContentMark = builder.mark()

                // try to parse a kson value, or consume a token if we can't
                if (!ksonValue()) {
                    builder.advanceLexer()
                }

                // only mark first element in error
                when (index) {
                    0 -> {
                        val errorMessage = if (containsKsonValue) EOF_NOT_REACHED else ONLY_UNEXPECTED_CONTENT
                        unexpectedContentMark.error(errorMessage.create())
                    }
                    else -> unexpectedContentMark.drop()
                }
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
 * Default maximum nesting of objects and lists to allow in parsing.  Because the parser is
 * recursive, excessive nesting could crash it due to a stack overflow.  This default was chosen
 * because it's enough to make our test suite pass on all supported platforms.  If/when we have
 * issues here, let's tweak as needed (note that this may also be configured in calls to
 * [Parser.parse])
 */
const val DEFAULT_MAX_NESTING_LEVEL = 128

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

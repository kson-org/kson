package org.kson.parser

import org.kson.collections.ImmutableList
import org.kson.collections.toImmutableList
import org.kson.collections.toImmutableMap
import org.kson.parser.messages.Message

enum class TokenType {
    BRACE_L,
    BRACE_R,
    BRACKET_L,
    BRACKET_R,
    COLON,
    COMMA,
    COMMENT,
    EMBED_END,
    EMBED_START,
    EMBED_TAG,
    EMBEDDED_BLOCK,
    EOF,
    FALSE,
    IDENTIFIER,
    ILLEGAL_TOKEN,
    NULL,
    NUMBER,
    DOUBLE_QUOTE,
    STRING,
    TRUE,
    WHITESPACE
}

private val KEYWORDS =
    mapOf(
        "null" to TokenType.NULL,
        "true" to TokenType.TRUE,
        "false" to TokenType.FALSE
    ).toImmutableMap()

/**
 * Use the null byte to represent EOF
 */
private const val EOF: Char = '\u0000'

/**
 * [SourceScanner] provides a char-by-char scanning interface which produces [Lexeme]s
 *
 * This is similar to [TokenScanner] in design, but distinct enough to stand alone
 */
private class SourceScanner(private val source: String) {
    private var selectionStartOffset = 0
    private var selectionEndOffset = 0

    private var selectionFirstLine = 0
    private var selectionFirstColumn = 0
    private var selectionEndLine = 0
    private var selectionEndColumn = 0

    fun peek(): Char {
        return if (selectionEndOffset >= source.length) EOF else source[selectionEndOffset]
    }

    fun peekNext(): Char {
        return if (selectionEndOffset + 1 >= source.length) EOF else source[selectionEndOffset + 1]
    }

    fun peekNextNext(): Char {
        return if (selectionEndOffset + 2 >= source.length) EOF else source[selectionEndOffset + 2]
    }

    /**
     * Increase the current text selection by one character, returning that character
     */
    fun advance(): Char {
        val currentChar = source[selectionEndOffset++]
        selectionEndColumn++
        if (currentChar == '\n') {
            // note the line increase so our Lexeme locations are accurate
            selectionEndLine++
            // reset our endColumn counter for this new line
            selectionEndColumn = 0
        }
        return currentChar
    }

    /**
     * Extract the currently selected text as a [Lexeme], moving the scanner past it
     */
    fun extractLexeme(): Lexeme {
        if (selectionEndOffset > source.length) {
            throw RuntimeException("Scanner has been advanced past end of source---missing some needed calls to peek()?")
        }

        val lexeme = Lexeme(
            source.substring(selectionStartOffset, selectionEndOffset),
            currentLocation()
        )
        startNextSelection()
        return lexeme
    }

    private fun startNextSelection() {
        // catch our select start indexes up to the current end indexes to start this
        // scanner's next selection from the next as-yet unconsumed char
        selectionStartOffset = selectionEndOffset
        selectionFirstLine = selectionEndLine
        selectionFirstColumn = selectionEndColumn
    }

    /**
     * Returns a [Location] object representing this [SourceScanner]'s current selection in [source]
     */
    fun currentLocation() =
        Location(
            selectionFirstLine,
            selectionFirstColumn,
            selectionEndLine,
            selectionEndColumn,
            selectionStartOffset,
            selectionEndOffset
        )
}

/**
 * A [String]/[Location] pair representing the raw [text] of a [Token]
 * along with its [location] in the parsed source input
 */
data class Lexeme(val text: String, val location: Location)

/**
 * [Location]s describe the position of a chunk of source inside a given kson source file
 */
data class Location(
    /**
     * Line where this location starts (counting lines starting at zero)
     */
    val firstLine: Int,
    /**
     * Column of [firstLine] where this location starts (counting columns starting zero)
     */
    val firstColumn: Int,
    /**
     * Line where this location ends (counting lines starting at zero)
     */
    val lastLine: Int,
    /**
     * Column of [lastLine] where this location ends (counting columns starting at zero)
     */
    val lastColumn: Int,
    /**
     * The zero-based start offset of this location relative to the whole document
     */
    val startOffset: Int,
    /**
     * The zero-based end offset of this location relative to the whole document
     */
    val endOffset: Int
) {
    companion object {
        /**
         * Merge two locations into a Location which spans from the beginning of [startLocation] to the end of
         * [endLocation].  [startLocation] must be positioned before [endLocation]
         */
        fun merge(startLocation: Location, endLocation: Location): Location {
            if (startLocation.startOffset > endLocation.endOffset) {
                throw RuntimeException("`startLocation` must be before `endLocation`")
            }
            return Location(
                startLocation.firstLine,
                startLocation.firstLine,
                endLocation.lastLine,
                endLocation.lastColumn,
                startLocation.startOffset,
                endLocation.endOffset
            )
        }
    }
}

data class Token(
    /**
     * The [TokenType] of this [Token]
     */
    val tokenType: TokenType,
    /**
     * The [Lexeme] (raw token text and original location) for this token
     */
    val lexeme: Lexeme,
    /**
     * The actual parsed [value] of this token, extracted from [lexeme]
     */
    val value: Any
)

/**
 * Holder class for the [Token]s that [Lexer] produces from the input source.  Manages ensuring that tokens we wish
 * to discard are ignored (tokens whose type is in [ignoreSet])
 *
 * @param ignoreSet [TokenType]s to leave out of the constructed [Token] list return by [toList]
 */
private data class TokenizedSource(private val ignoreSet: Set<TokenType>) {
    private val tokens = mutableListOf<Token>()

    fun add(token: Token) {
        if (ignoreSet.contains(token.tokenType)) {
            return
        }

        tokens.add(token)
    }

    fun toList(): ImmutableList<Token> {
        return tokens.toImmutableList()
    }
}

/**
 * Tokenizes the given `source` into a list of [Token] by calling [tokenize]
 *
 * @param source the input Kson source to tokenize
 * @param messageSink a [MessageSink] to write user-facing messages about the tokenization, for instance errors
 * @param gapFree whether to ensure _all_ source, including whitespace, quotes and illegal characters, is precisely
 *                covered by the resulting [Token] list.  This is needed for instance to properly back a Jetbrains
 *                IDE-compliant lexer with this official lexer.  Default: false
 */
class Lexer(source: String, private val messageSink: MessageSink, gapFree: Boolean = false) {

    private val sourceScanner = SourceScanner(source)
    private val tokens = TokenizedSource(
        if (gapFree) {
            emptySet()
        } else {
            setOf(TokenType.ILLEGAL_TOKEN, TokenType.WHITESPACE, TokenType.DOUBLE_QUOTE)
        }
    )

    fun tokenize(): ImmutableList<Token> {
        while (sourceScanner.peek() != EOF) {
            scan()
        }

        tokens.add(Token(TokenType.EOF, Lexeme("", sourceScanner.currentLocation()), ""))
        return tokens.toList()
    }

    private fun scan() {
        val char = sourceScanner.advance()

        if (isWhitespace(char)) {
            // advance through any sequential whitespace
            while (isWhitespace(sourceScanner.peek()) && sourceScanner.peek() != EOF) {
                sourceScanner.advance()
            }
            addLiteralToken(TokenType.WHITESPACE)
            return
        }

        when (char) {
            '#' -> {
                // comments extend to end of the line
                while (sourceScanner.peek() != '\n' && sourceScanner.peek() != EOF) sourceScanner.advance()

                // we retain comments rather than ignore them in the hopes of preserving them in
                // various serialization use cases (such as YAML serialization)
                addLiteralToken(TokenType.COMMENT)
            }
            '{' -> addLiteralToken(TokenType.BRACE_L)
            '}' -> addLiteralToken(TokenType.BRACE_R)
            '[' -> addLiteralToken(TokenType.BRACKET_L)
            ']' -> addLiteralToken(TokenType.BRACKET_R)
            ':' -> addLiteralToken(TokenType.COLON)
            ',' -> addLiteralToken(TokenType.COMMA)
            '"' -> {
                addLiteralToken(TokenType.DOUBLE_QUOTE)
                string()
            }
            '%' -> {
                if (sourceScanner.peek() == '%') {
                    sourceScanner.advance()
                    addLiteralToken(TokenType.EMBED_START)
                    embeddedHashBlock()
                } else {
                    messageSink.error(
                        addLiteralToken(TokenType.ILLEGAL_TOKEN),
                        Message.EMBED_BLOCK_DANGLING_HASH)
                }
            }
            '`' -> {
                if (sourceScanner.peek() == '`') {
                    sourceScanner.advance()
                    if (sourceScanner.peek() == '`') {
                        sourceScanner.advance()
                        addLiteralToken(TokenType.EMBED_START)
                        embeddedTickBlock()
                    } else {
                        messageSink.error(
                            addLiteralToken(TokenType.ILLEGAL_TOKEN),
                            Message.EMBED_BLOCK_DANGLING_DOUBLETICK
                        )
                    }
                } else {
                    messageSink.error(addLiteralToken(TokenType.ILLEGAL_TOKEN), Message.EMBED_BLOCK_DANGLING_TICK)
                }
            }
            else -> {
                when {
                    char == '-' || isDigit(char) -> {
                        if (char == '-' && !isDigit(sourceScanner.peek())) {
                            /**
                             * todo we likely want to move the mechanics of number parsing out of the lexer and
                             *         into the parser if:
                             *         - we see more edge cases like this, or:
                             *         - we succeed in adding dash-denoted lists into the grammar
                             */
                            messageSink.error(addLiteralToken(TokenType.ILLEGAL_TOKEN), Message.DANGLING_DASH)
                            return
                        }
                        number()
                    }
                    // identifiers start with an alphabetic character or an underscore
                    isAlphaOrUnderscore(char) -> {
                        identifier()
                    }
                    else -> {
                        messageSink.error(
                            addLiteralToken(TokenType.ILLEGAL_TOKEN),
                            Message.UNEXPECTED_CHAR,
                            char.toString()
                        )
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given [char] is whitespace
     */
    private fun isWhitespace(char: Char): Boolean {
        return isInlineWhitespace(char) || char == '\n'
    }

    /**
     * Returns true if the given [char] is a non-newline whitespace
     */
    private fun isInlineWhitespace(char: Char): Boolean {
        return char == ' ' || char == '\r' || char == '\t'
    }

    private fun identifier() {
        while (isAlphaNumeric(sourceScanner.peek())) sourceScanner.advance()

        val lexeme = sourceScanner.extractLexeme()
        val type: TokenType = KEYWORDS[lexeme.text] ?: TokenType.IDENTIFIER
        addToken(type, lexeme, lexeme.text)
    }

    private fun string() {
        // we use this var to track if we need to consume escapes in a string so we only walk its text
        // trying to replace escapes if we know we need to
        var hasEscapedQuotes = false
        while (sourceScanner.peek() != '"' && sourceScanner.peek() != EOF) {
            if (sourceScanner.peek() == '\\' && sourceScanner.peekNext() == '"') {
                hasEscapedQuotes = true
                // ensure we advance past it so it's part of the string
                sourceScanner.advance()
            }
            sourceScanner.advance()
        }

        val stringLocation = if (hasEscapedQuotes) {
            val rawStringLexeme = sourceScanner.extractLexeme()
            val escapedString = rawStringLexeme.text.replace("\\\"", "\"")
            addToken(TokenType.STRING, rawStringLexeme, escapedString)
        } else {
            addLiteralToken(TokenType.STRING)
        }

        if (sourceScanner.peek() == EOF) {
            messageSink.error(stringLocation, Message.STRING_NO_CLOSE)
            return
        }

        // Eat the closing `"`
        sourceScanner.advance()
        addLiteralToken(TokenType.DOUBLE_QUOTE)
    }

    private fun embeddedBlockCommon() {
        val embedTag = if (isAlphaNumeric(sourceScanner.peek())) {
            while (isAlphaNumeric(sourceScanner.peek())) {
                sourceScanner.advance()
            }
            val embedTagLexeme = sourceScanner.extractLexeme()

            addToken(TokenType.EMBED_TAG, embedTagLexeme, embedTagLexeme.text)
            embedTagLexeme.text
        } else {
            null
        }

        while (isInlineWhitespace(sourceScanner.peek())) {
            // advance through any inline whitespace between the '```[tag]' and the required newline
            sourceScanner.advance()
        }

        if (sourceScanner.peek() == '\n') {
            // found the required newline---adavnce past it and tokenize it along with any whitespace we advanced past above
            sourceScanner.advance()
            addLiteralToken(TokenType.WHITESPACE)
        } else {
            messageSink.error(addLiteralToken(TokenType.ILLEGAL_TOKEN), Message.EMBED_BLOCK_BAD_START, embedTag)
        }
    }

    private fun embeddedTickBlock() {
        embeddedBlockCommon()

        // we use this var to track if we need to consume escapes in an embed block so we only walk its text
        // trying to replace escapes if we know we need to
        var hasEscapedEmbedEnd = false

        // read embedded content until the closing ```
        while (
            !(sourceScanner.peek() == '`' && sourceScanner.peekNext() == '`' && sourceScanner.peekNextNext() == '`')
            && sourceScanner.peek() != EOF
        ) {
            if (sourceScanner.peek() == '`' && sourceScanner.peekNext() == '\\') {
                // if this is all slashes until "``", we're looking at an escaped EMBED_END
                sourceScanner.advance()
                while (sourceScanner.peek() == '\\') {
                    sourceScanner.advance()
                }
                if (sourceScanner.peek() == '`' && sourceScanner.peekNext() == '`') {
                    hasEscapedEmbedEnd = true
                    sourceScanner.advance()
                    sourceScanner.advance()
                }
            }
            sourceScanner.advance()
        }

        val embedBlockLexeme = sourceScanner.extractLexeme()

        if (sourceScanner.peek() == EOF) {
            messageSink.error(embedBlockLexeme.location, Message.EMBED_BLOCK_NO_CLOSE)
            return
        }

        val trimmedEmbedBlockContent = trimMinimumIndent(embedBlockLexeme.text)
        val embedTokenValue = if (hasEscapedEmbedEnd) {
            /**
             * Here we trim the escaping slash from escaped EMBED_ENDs.  This is slightly novel/intricate,
             * so some here's some clarifying notes:
             *
             * - an escaped EMBED_END has its second tick escaped: `\`` yields ``` inside of an embed.
             *   Note that this moves the escaping goalpost since we also need to allow `\`` literally inside
             *   of embeds.  So: when evaluating escaped EMBED_ENDs, we allow arbitrary \s before the second
             *   tick, and consume one of them.  Then, `\\`` gives `\`` in the output, `\\\`` gives `\\`` in
             *   the output, etc
             *
             * - the regex for this ends up looking a bit crazy since \ needs to be double escaped in regex, so
             *   matching \ requires saying "\\\\".  Then we use the [\\\\]* to reinsert any additional slashes
             *   in the output
             */
            trimmedEmbedBlockContent.replace(Regex("`\\\\([\\\\]*)``"), "`$1``")
        } else {
            trimmedEmbedBlockContent
        }

        addToken(TokenType.EMBEDDED_BLOCK, embedBlockLexeme, embedTokenValue)

        // process our closing ```
        sourceScanner.advance()
        sourceScanner.advance()
        sourceScanner.advance()
        addLiteralToken(TokenType.EMBED_END)
    }

    private fun embeddedHashBlock() {
        embeddedBlockCommon()

        // we use this var to track if we need to consume escapes in an embed block so we only walk its text
        // trying to replace escapes if we know we need to
        var hasEscapedEmbedEnd = false

        // read embedded content until the closing `%`
        while (
            !(sourceScanner.peek() == '%' && sourceScanner.peekNext() == '%')
            && sourceScanner.peek() != EOF
        ) {
            if (sourceScanner.peek() == '%' && sourceScanner.peekNext() == '\\') {
                // if this is all slashes until "%", we're looking at an escaped EMBED_END
                sourceScanner.advance()
                while (sourceScanner.peek() == '\\') {
                    sourceScanner.advance()
                }
                if (sourceScanner.peek() == '%') {
                    hasEscapedEmbedEnd = true
                    sourceScanner.advance()
                }
            }
            sourceScanner.advance()
        }

        val embedBlockLexeme = sourceScanner.extractLexeme()

        if (sourceScanner.peek() == EOF) {
            messageSink.error(embedBlockLexeme.location, Message.EMBED_BLOCK_NO_CLOSE)
            return
        }

        val trimmedEmbedBlockContent = trimMinimumIndent(embedBlockLexeme.text)
        val embedTokenValue = if (hasEscapedEmbedEnd) {
            /**
             * Here we trim the escaping slash from escaped EMBED_ENDs.  This is slightly novel/intricate,
             * so some here's some clarifying notes:
             *
             * - an escaped EMBED_END has its second tick escaped: `\`` yields ``` inside of an embed.
             *   Note that this moves the escaping goalpost since we also need to allow `\`` literally inside
             *   of embeds.  So: when evaluating escaped EMBED_ENDs, we allow arbitrary \s before the second
             *   tick, and consume one of them.  Then, `\\`` gives `\`` in the output, `\\\`` gives `\\`` in
             *   the output, etc
             *
             * - the regex for this ends up looking a bit crazy since \ needs to be double escaped in regex, so
             *   matching \ requires saying "\\\\".  Then we use the [\\\\]* to reinsert any additional slashes
             *   in the output
             */
            trimmedEmbedBlockContent.replace(Regex("`\\\\([\\\\]*)``"), "`$1``")
        } else {
            trimmedEmbedBlockContent
        }

        addToken(TokenType.EMBEDDED_BLOCK, embedBlockLexeme, embedTokenValue)

        // process our closing %%
        sourceScanner.advance()
        sourceScanner.advance()
        addLiteralToken(TokenType.EMBED_END)
    }

    /**
     * Given a [textBlock], computes the minimum indent of all its lines, then returns
     * the [textBlock] with that indent trimmed from each line.
     *
     * NOTE: blank lines are considered pure indent and used in this calculation, so for instance:
     *
     * "   this string
     *         has a minimum indent defined
     *       by its last line
     *    "
     *
     * becomes:
     *
     * "  this string
     *      has a minimum indent defined
     *    by its blank last line
     * "
     */
    private fun trimMinimumIndent(textBlock: String): String {
        val linesWithNewlines = textBlock.split("\n").map { it + "\n" }

        val minCommonIndent =
            linesWithNewlines.minOfOrNull { it.indexOfFirst { char -> !isInlineWhitespace(char) } } ?: 0

        return textBlock
            .split("\n")
            .joinToString("\n") { it.drop(minCommonIndent) }
    }

    private fun number() {
        // Consume the whole part of the decimal-formatted number
        // NOTE: numbers with leading 0's are still treated as decimal (not octal)
        while (isDigit(sourceScanner.peek())) sourceScanner.advance()

        // Consume an optional fractional part (following a decimal point)
        if (sourceScanner.peek() == '.' && isDigit(sourceScanner.peekNext())) {
            // Consume the decimal point
            sourceScanner.advance()
            while (isDigit(sourceScanner.peek())) sourceScanner.advance()
        }

        // Consume optional exponent part (ex: 1.74e22, 2.801E12, 4.11e-12, 1e16)
        if (sourceScanner.peek() == 'E' || sourceScanner.peek() == 'e') {
            // Consume the 'E' or 'e'
            sourceScanner.advance()
            if (sourceScanner.peek() == '-' || sourceScanner.peek() == '+') {
                // Consume the optional exponent sign
                sourceScanner.advance()
            }
            if (!isDigit(sourceScanner.peek())) {
                // Double.parseDouble considers a trailing 'E' without an exponent part to be a NumberFormatException
                messageSink.error(addLiteralToken(TokenType.ILLEGAL_TOKEN), Message.DANGLING_EXP_INDICATOR)
                return
            }
            while (isDigit(sourceScanner.peek())) sourceScanner.advance()
        }

        val numberLexeme = sourceScanner.extractLexeme()

        /* numberLexeme is now made up of three parts:
         * * one or more digits (required, but may be 0) representing the whole part;
         * * (optional) a decimal point, which is required to be followed by digits representing the fractional part;
         * * (optional) an 'E' or 'e' which is in turn followed by an optional sign and one or more required digits
         *   representing the exponent part
         *
         * This all matches both the JSON grammar and the expectations of Java's double parser, so we'll forward parsing
         * on to that.
         *
         * The parseDouble function throws a NumberFormatException which we aren't catching here, allowing it to bubble
         * out as a RuntimeException to loudly error when/if a new edge case is found.
         *
         * See also java.lang.Double.parseDouble and jdk.internal.math.FloatingDecimal.ASCIIToBinaryBuffer.doubleValue
         */
        val parsedDouble = numberLexeme.text.toDouble()

        addToken(TokenType.NUMBER, numberLexeme, parsedDouble)
    }

    /**
     * Convenience method for adding a [tokenType] [Token] with a "literal" value---i.e. its value is the
     * currently selected text in [sourceScanner]
     *
     * @return the location of the added [Token]
     */
    private fun addLiteralToken(tokenType: TokenType): Location {
        val lexeme = sourceScanner.extractLexeme()
        addToken(tokenType, lexeme, lexeme.text)
        return lexeme.location
    }

    /**
     * Add a token to [tokens]
     *
     * @return the location of the added [Token]
     */
    private fun addToken(type: TokenType, lexeme: Lexeme, value: Any): Location {
        tokens.add(Token(type, lexeme, value))
        return lexeme.location
    }

    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    private fun isAlphaOrUnderscore(c: Char): Boolean {
        return c in 'a'..'z' ||
                c in 'A'..'Z' || c == '_'
    }

    private fun isAlphaNumeric(c: Char): Boolean {
        return isAlphaOrUnderscore(c) || isDigit(c)
    }
}
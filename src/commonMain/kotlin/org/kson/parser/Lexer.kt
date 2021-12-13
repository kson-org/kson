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
    NULL,
    NUMBER,
    STRING,
    TRUE
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
     * Move the scanner past the currently selected text, completely ignoring it.  Returns the dropped
     * [Lexeme]'s [Location]---useful for reporting errors, etc., on dropped [Lexeme]s
     */
    fun dropLexeme(): Location {
        val droppedLexemeLocation = currentLocation()
        startNextSelection()
        return droppedLexemeLocation
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
    private fun currentLocation() =
        /**
         * note that we adjust our internal 0-based indexes to adhere to the 1-based
         * interface of [Location].  See the doc on [Location] for details on this.
         */
        Location(
            selectionFirstLine + 1,
            selectionFirstColumn + 1,
            selectionEndLine + 1,
            selectionEndColumn + 1
        )
}

/**
 * A [String]/[Location] pair representing the raw [text] of a [Token]
 * along with its [location] in the parsed source input
 */
data class Lexeme(val text: String, val location: Location)

/**
 * [Location]s mark the position of a chunk of source inside a given kson source file.
 * These are part of the end-user interface, used to report errors, etc.
 *
 * [Location] objects should be created using base-1 indexes for each position
 * since they are targeted at the end user and so follow [the gnu standard](https://www.gnu.org/prep/standards/html_node/Errors.html)
 *
 * We currently enforce this 1-based rule simply using doc since these are created once, in this file,
 * as part of Lexing in [SourceScanner.currentLocation].  If/when we hit off-by-1 errors because of
 * this trade-off [Location]s, we'll see if we can better formalize/guardrail this
 */
data class Location(
    /**
     * Line where this location starts (counting lines starting at 1, not zero)
     */
    val firstLine: Int,
    /**
     * Column of [firstLine] where this location starts (counting columns starting at 1, not zero)
     */
    val firstColumn: Int,
    /**
     * Line where this location ends (counting lines starting at 1, not zero)
     */
    val lastLine: Int,
    /**
     * Column of [lastLine] where this location ends (counting columns starting at 1, not zero)
     */
    val lastColumn: Int
)

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

class Lexer(source: String, private val messageSink: MessageSink) {

    private val sourceScanner = SourceScanner(source)
    private val tokens = mutableListOf<Token>()

    fun tokenize(): ImmutableList<Token> {
        while (sourceScanner.peek() != EOF) {
            scan()
        }

        tokens.add(Token(TokenType.EOF, Lexeme("", Location(-1, -1, -1, -1)), ""))
        return tokens.toImmutableList()
    }

    private fun scan() {
        val char = sourceScanner.advance()
        if (isInlineWhitespace(char) || char == '\n') {
            // ignore whitespace
            sourceScanner.dropLexeme()
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
                // drop this opening quote---it's not part of the string
                sourceScanner.dropLexeme()
                string()
            }
            '`' -> {
                if (sourceScanner.peek() == '`') {
                    sourceScanner.advance()
                    if (sourceScanner.peek() == '`') {
                        sourceScanner.advance()
                        addLiteralToken(TokenType.EMBED_START)
                        embeddedBlock()
                    } else {
                        messageSink.error(sourceScanner.dropLexeme(), Message.EMBED_BLOCK_DANGLING_DOUBLETICK)
                    }
                } else {
                    messageSink.error(sourceScanner.dropLexeme(), Message.EMBED_BLOCK_DANGLING_TICK)
                }
            }
            else -> {
                when {
                    char == '-' || isDigit(char) -> {
                        number()
                    }
                    // identifiers start with an alphabetic character or an underscore
                    isAlphaOrUnderscore(char) -> {
                        identifier()
                    }
                    else -> {
                        messageSink.error(sourceScanner.dropLexeme(), Message.UNEXPECTED_CHAR, char.toString())
                    }
                }
            }
        }
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
        if (sourceScanner.peek() == EOF) {
            messageSink.error(sourceScanner.dropLexeme(), Message.STRING_NO_CLOSE)
            return
        }

        if (hasEscapedQuotes) {
            val rawStringLexeme = sourceScanner.extractLexeme()
            val escapedString = rawStringLexeme.text.replace("\\\"", "\"")
            addToken(TokenType.STRING, rawStringLexeme, escapedString)
        } else {
            addLiteralToken(TokenType.STRING)
        }

        // Eat the closing `"`
        sourceScanner.advance()
        sourceScanner.dropLexeme()
    }

    private fun embeddedBlock() {
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
            // ignore any inline whitespace between the '```[tag]' and the required newline
            sourceScanner.advance()
        }
        sourceScanner.dropLexeme()

        if (sourceScanner.peek() == '\n') {
            // found the required newline---drop it since it's not part of the content
            sourceScanner.advance()
            sourceScanner.dropLexeme()
        } else {
            messageSink.error(sourceScanner.dropLexeme(), Message.EMBED_BLOCK_BAD_START, embedTag)
        }

        // we use this var to track if we need to consume escapes in an embed blcok so we only walk its text
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

        if (sourceScanner.peek() == EOF) {
            messageSink.error(sourceScanner.dropLexeme(), Message.EMBED_BLOCK_NO_CLOSE)
            return
        }

        val embedBlockLexeme = sourceScanner.extractLexeme()

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
            trimmedEmbedBlockContent.replace(Regex("`\\\\([\\\\]*)``"),"`$1``")
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

        val minCommonIndent = linesWithNewlines
            .map { it.indexOfFirst { char -> !isInlineWhitespace(char) } }
            .minOrNull() ?: 0

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
                messageSink.error(sourceScanner.dropLexeme(), Message.DANGLING_EXP_INDICATOR)
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
     */
    private fun addLiteralToken(tokenType: TokenType) {
        val lexeme = sourceScanner.extractLexeme()
        addToken(tokenType, lexeme, lexeme.text)
    }

    private fun addToken(type: TokenType, lexeme: Lexeme, value: Any) {
        tokens.add(Token(type, lexeme, value))
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
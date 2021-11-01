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
    private var selectionFirstLine = 0
    private var selectionStartOffset = 0
    private var selectionEndLine = 0
    private var selectionEndOffset = 0

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
        if (currentChar == '\n') {
            // note the line increase so our Lexeme locations are accurate
            selectionEndLine++
        }
        return currentChar
    }

    /**
     * Move the scanner past the currently selected text, completely ignoring it.  Returns the dropped
     * [Lexeme]'s [Location]---useful for reporting errors, etc., on dropped [Lexeme]s
     */
    fun dropLexeme(): Location {
        val droppedLexemeLocation = currentLocation()
        selectionFirstLine = selectionEndLine
        selectionStartOffset = selectionEndOffset
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
        selectionFirstLine = selectionEndLine
        selectionStartOffset = selectionEndOffset
        return lexeme
    }

    private fun currentLocation() =
        Location(selectionFirstLine, selectionStartOffset, selectionEndLine, selectionEndOffset)
}

/**
 * A [String]/[Location] pair representing the raw [text] of a [Token]
 * along with its [location] in the parsed source input
 */
data class Lexeme(val text: String, val location: Location)

data class Location(
    val firstLine: Int,
    val firstColumn: Int,
    val lastLine: Int,
    val lastColumn: Int
) {
    /**
     * Common syntax error conventions call for base-1 indexed [Location]s, so pretty much any end-user facing
     * rendering of this message should use the [Location] returned here
     */
    fun asBase1Indexed(): Location {
        return Location(firstLine + 1, firstColumn + 1, lastLine + 1, lastColumn + 1)
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

class Lexer(source: String, private val messageSink: MessageSink) {

    private val sourceScanner = SourceScanner(source)
    private val tokens = mutableListOf<Token>()

    fun tokenize(): ImmutableList<Token> {
        while (sourceScanner.peek() != EOF) {
            scan()
        }

        tokens.add(Token(TokenType.EOF, Lexeme("", Location(-1, -1, -1, -1)), EOF))
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
                    isDigit(char) -> {
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
        while (sourceScanner.peek() != '"' && sourceScanner.peek() != EOF) {
            sourceScanner.advance()
        }
        if (sourceScanner.peek() == EOF) {
            messageSink.error(sourceScanner.dropLexeme(), Message.STRING_NO_CLOSE)
            return
        }

        addLiteralToken(TokenType.STRING)

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

        // read embedded content until the closing ```
        while (
            !(sourceScanner.peek() == '`' && sourceScanner.peekNext() == '`' && sourceScanner.peekNextNext() == '`')
            && sourceScanner.peek() != EOF
        ) {
            sourceScanner.advance()
        }

        if (sourceScanner.peek() == EOF) {
            messageSink.error(sourceScanner.dropLexeme(), Message.EMBED_BLOCK_NO_CLOSE)
            return
        }

        val embedBlockLexeme = sourceScanner.extractLexeme()

        val trimmedEmbedBlockContent = trimMinimumIndent(embedBlockLexeme.text)

        addToken(TokenType.EMBEDDED_BLOCK, embedBlockLexeme, trimmedEmbedBlockContent)

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

    // parser todo match JSON number spec (note: there was a multiplatform inconsistency around
    //             numbers with a 0 decimal, ie. 42.0.  Ensure there's testing around that case)
    private fun number() {
        while (isDigit(sourceScanner.peek())) sourceScanner.advance()

        // Look for a fractional part
        if (sourceScanner.peek() == '.' && isDigit(sourceScanner.peekNext())) {
            // Consume the "."
            sourceScanner.advance()
            while (isDigit(sourceScanner.peek())) sourceScanner.advance()
        }

        val numberLexeme = sourceScanner.extractLexeme()
        addToken(TokenType.NUMBER, numberLexeme, numberLexeme.text.toDouble())
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
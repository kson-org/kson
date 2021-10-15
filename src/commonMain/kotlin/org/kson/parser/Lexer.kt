package org.kson.parser

import org.kson.collections.ImmutableList
import org.kson.collections.toImmutableList
import org.kson.collections.toImmutableMap

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

class Token(val tokenType: TokenType, val value: Any?, val line: Int)

class Lexer(private val source: String, private val messageSink: MessageSink) {
    /**
     * List of [Token]s scanned from [source]
     */
    private val tokens = mutableListOf<Token>()

    /**
     * Start position in [source]
     */
    private var tokenStart = 0

    /**
     * Current character offset from start of [source]
     */
    private var currentOffset = 0

    /**
     * Current line of [currentOffset] in [source]
     */
    private var currentLine = 0

    fun tokenize(): ImmutableList<Token> {
        while (!isAtEnd()) {
            scan()
        }

        tokens.add(Token(TokenType.EOF, EOF, currentLine))
        return tokens.toImmutableList()
    }

    private fun scan() {
        val char = advance()
        if (isInlineWhitespace(char)) {
            // ignore whitespace
            dropCurrentChar()
            return
        }
        when (char) {
            '#' -> {
                // comments extend to end of the line
                while (peek() != '\n' && !isAtEnd()) advance()
                // we retain comments rather than ignore them in the hopes of preserving them in YAML serialization
                addToken(TokenType.COMMENT)
            }
            '{' -> addToken(TokenType.BRACE_L)
            '}' -> addToken(TokenType.BRACE_R)
            '[' -> addToken(TokenType.BRACKET_L)
            ']' -> addToken(TokenType.BRACKET_R)
            ':' -> addToken(TokenType.COLON)
            ',' -> addToken(TokenType.COMMA)
            '\n' -> {
                currentLine++
                dropCurrentChar()
            }
            '"' -> {
                string()
            }
            '`' -> {
                if (peek() == '`') {
                    advance()
                    if (peek() == '`') {
                        advance()
                        addToken(TokenType.EMBED_START)
                        embeddedBlock()
                    } else {
                        messageSink.error(currentLine, "Dangling double-backtick.  Did you mean \"```\"?")
                    }
                } else {
                    messageSink.error(currentLine, "Dangling backtick.  Did you mean \"```\"?")
                }
            }
            else -> {
                when {
                    isDigit(char) -> {
                        number()
                    }
                    isAlpha(char) -> {
                        identifier()
                    }
                    else -> {
                        messageSink.error(currentLine, "Unexpected character: $char")
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
        while (isAlphaNumeric(peek())) advance()

        val text = source.substring(tokenStart, currentOffset)
        val type: TokenType = KEYWORDS[text] ?: TokenType.IDENTIFIER
        addToken(type)
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') currentLine++
            advance()
        }
        if (isAtEnd()) {
            messageSink.error(currentLine, "Unterminated string")
            return
        }

        // Eat the closing `"`
        advance()

        // Trim the surrounding quotes.
        val value = source.substring(tokenStart + 1, currentOffset - 1)
        addToken(TokenType.STRING, value)
    }

    private fun embeddedBlock() {
        val embedTag = if (isAlphaNumeric(peek())) {
            while (isAlphaNumeric(peek())) {
                advance()
            }

            val tag = source.substring(tokenStart, currentOffset)
            addToken(TokenType.EMBED_TAG, tag)
        } else {
            null
        }

        while (isInlineWhitespace(peek())) {
            // ignore any inline whitespace between the '```[tag]' and the required newline
            advance()
            dropCurrentChar()
        }

        if (peek() != '\n') {
            // todo highlight all non-whitespace content in this error
            messageSink.error(
                currentLine,
                "This Embedded Block's content must start on the line after the opening '```${embedTag ?: ""}'"
            )
        } else {
            // found the required newline---drop it since it's not part of the content
            advance()
            dropCurrentChar()
        }

        // read embedded content until the closing ```
        while (
            !(peek() == '`' && peekNext() == '`' && peekNextNext() == '`')
            && peek() != EOF
        ) {
            advance()
        }

        if (peek() == EOF) {
            messageSink.error(currentLine, "Unclosed ```...")
            return
        }

        val embedBlockContent = source.substring(tokenStart, currentOffset)

        val trimmedEmbedBlockContent = trimMinimumIndent(embedBlockContent)

        addToken(TokenType.EMBEDDED_BLOCK, trimmedEmbedBlockContent)

        // process our closing ```
        advance()
        advance()
        advance()
        addToken(TokenType.EMBED_END)
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

    // dm todo match JSON number spec
    private fun number() {
        while (isDigit(peek())) advance()

        // Look for a fractional part
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance()
            while (isDigit(peek())) advance()
        }
        addToken(TokenType.NUMBER, source.substring(tokenStart, currentOffset).toDouble())
    }

    private fun advance(): Char {
        return source[currentOffset++]
    }

    private fun dropCurrentChar() {
        tokenStart = currentOffset
    }

    private fun peek(): Char {
        return if (isAtEnd()) EOF else source[currentOffset]
    }

    private fun peekNext(): Char {
        return if (currentOffset + 1 >= source.length) EOF else source[currentOffset + 1]
    }

    private fun peekNextNext(): Char {
        return if (currentOffset + 2 >= source.length) EOF else source[currentOffset + 2]
    }

    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, value: Any?) {
        val text = source.substring(tokenStart, currentOffset)
        tokens.add(Token(type, value ?: text, currentLine))
        tokenStart = currentOffset
    }

    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    /**
     * dm todo are underscores the only special character we'll allow in identifiers?
     */
    private fun isAlpha(c: Char): Boolean {
        return c in 'a'..'z' ||
                c in 'A'..'Z' || c == '_'
    }

    private fun isAlphaNumeric(c: Char): Boolean {
        return isAlpha(c) || isDigit(c)
    }

    private fun isAtEnd(): Boolean {
        return currentOffset >= source.length
    }
}
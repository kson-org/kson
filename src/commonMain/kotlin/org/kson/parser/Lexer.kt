package org.kson.parser

import org.kson.collections.ImmutableList
import org.kson.collections.toImmutableList
import org.kson.collections.toImmutableMap

enum class TokenType {
    BRACE_L,
    BRACE_R,
    BRACKET_L,
    BRACKET_R,
    EOF,
    // dm todo implement code block lexing
    CODE_BLOCK,
    COLON,
    COMMA,
    COMMENT,
    FALSE,
    IDENTIFIER,
    NULL,
    NUMBER,
    STRING,
    TRUE
}

private val KEYWORDS = mapOf("null" to TokenType.NULL, "true" to TokenType.TRUE, "false" to TokenType.FALSE).toImmutableMap()

/**
 * Use the null byte to represent EOF
 */
private const val EOF: Char = '\u0000'

class Token(val tokenType: TokenType, val lexeme: String, val literal: Any?, val line: Int)

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
            tokenStart = currentOffset
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", EOF, currentLine))
        return tokens.toImmutableList()
    }

    private fun scanToken() {
        when(val char = advance()) {
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
            ' ', '\r', '\t' -> {
                // ignore whitespace
            }
            '\n' -> {
                currentLine++
            }
            '"' -> {
                string()
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

    private fun peek(): Char {
        return if (isAtEnd()) EOF else source[currentOffset]
    }

    private fun peekNext(): Char {
        return if (currentOffset + 1 >= source.length) EOF else source[currentOffset + 1]
    }

    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(tokenStart, currentOffset)
        tokens.add(Token(type, text, literal, currentLine))
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
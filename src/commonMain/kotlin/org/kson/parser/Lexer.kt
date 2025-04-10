package org.kson.parser

import org.kson.collections.ImmutableList
import org.kson.collections.toImmutableList
import org.kson.collections.toImmutableMap
import org.kson.parser.TokenType.*

/**
 * Represents an embed delimiter in KSON, which can be either %% or $$
 */
sealed class EmbedDelim(val char: Char) {
    /** The full delimiter string (either "%%" or "$$") */
    val delimiter: String = "$char$char"

    /** Percent-style delimiter (%%), our "primary" delimiter */
    data object Percent : EmbedDelim('%')

    /** Dollar-style delimiter ($$), our "alternate" delimiter */
    data object Dollar : EmbedDelim('$')
}

private val KEYWORDS =
    mapOf(
        "null" to NULL,
        "true" to TRUE,
        "false" to FALSE
    ).toImmutableMap()

/**
 * [SourceScanner] provides a char-by-char scanning interface which produces [Lexeme]s
 */
private class SourceScanner(private val source: String) {
    private var selectionStartOffset = 0
    private var selectionEndOffset = 0

    private var selectionFirstLine = 0
    private var selectionFirstColumn = 0
    private var selectionEndLine = 0
    private var selectionEndColumn = 0

    fun eof(): Boolean {
        return eofIn(0)
    }

    private fun eofIn(numCharsToEof: Int): Boolean {
        return selectionEndOffset + numCharsToEof >= source.length
    }

    /**
     * Return the next character in this [SourceScanner]
     *   or `null` if there is no next character
     */
    fun peek(): Char? {
        if (eof()) {
            return null
        }
        return source[selectionEndOffset]
    }

    /**
     * Return the character after next in this [SourceScanner]
     *   or null if there is character after next
     */
    fun peekNext(): Char? {
        if (eofIn(1)) {
            return null
        }
        return source[selectionEndOffset + 1]
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
     * The final lexed [value] of this token, extracted (and possibly transformed) from [lexeme]
     */
    val value: String,
    /**
     * The comments that the scanner found for this token.
     *
     * NOTE: if we find a "trailing" comment for this token (i.e. `key: value # trailing comment`), we consider
     *  that to be the "last" line comment for this token.  Since at this point the comments are being translated
     *  from raw source to metadata on this token, the metadata is much nicer being consistent like this.
     *
     *  This also means that if/when this metadata is re-serialized out to source, these "originally" trailing comments
     *  will be rendered as preceding line comments.  This is okay, and aligns well with all the good reasons that
     *  trailing comments are somewhat discouraged ([here is a good summary from Code Complete](https://stackoverflow.com/a/14385596))
     */
    val comments: List<String> = emptyList()
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
 * @param gapFree whether to ensure _all_ source, including comments, whitespace, quotes and illegal chars, is precisely
 *                covered by the resulting [Token] list.  This is needed for instance to properly back a Jetbrains
 *                IDE-compliant lexer with this official lexer.  Default: false
 */
class Lexer(source: String, gapFree: Boolean = false) {

    private val sourceScanner = SourceScanner(source)
    private val tokens = TokenizedSource(
        if (gapFree) {
            emptySet()
        } else {
            setOf(WHITESPACE, COMMENT)
        }
    )

    /**
     * We collect scanned comments into this collection, then drain them on the appropriate
     * [Token] in [commentMetadataForCurrentToken] as we lex the source
     */
    private var currentCommentLines = ArrayList<String>()

    fun tokenize(): ImmutableList<Token> {
        while (!sourceScanner.eof()) {
            scan()
        }

        addToken(EOF, Lexeme("", sourceScanner.currentLocation()), "")

        return tokens.toList()
    }

    private fun scan() {
        val char = sourceScanner.advance()

        if (isWhitespace(char)) {
            // advance through any sequential whitespace
            while (isWhitespace(sourceScanner.peek()) && !sourceScanner.eof()) {
                sourceScanner.advance()
            }
            addLiteralToken(WHITESPACE)
            return
        }

        // a "minus sign" followed by whitespace is actually a list dash
        if (char == '-' && (isWhitespace(sourceScanner.peek()) || sourceScanner.eof())) {
            addLiteralToken(LIST_DASH)
            return
        }

        when (char) {
            '#' -> {
                val commentText = comment()
                currentCommentLines.add(commentText)
            }
            '{' -> addLiteralToken(CURLY_BRACE_L)
            '}' -> addLiteralToken(CURLY_BRACE_R)
            '[' -> addLiteralToken(SQUARE_BRACKET_L)
            ']' -> addLiteralToken(SQUARE_BRACKET_R)
            '<' -> addLiteralToken(ANGLE_BRACKET_L)
            '>' -> addLiteralToken(ANGLE_BRACKET_R)
            ':' -> addLiteralToken(COLON)
            ',' -> addLiteralToken(COMMA)
            '"', '\'' -> {
                addLiteralToken(STRING_OPEN_QUOTE)
                string(char)
            }
            EmbedDelim.Percent.char, EmbedDelim.Dollar.char -> {
                // look for the required second embed delim char
                if (sourceScanner.peek() == char) {
                    sourceScanner.advance()
                    addLiteralToken(EMBED_OPEN_DELIM)
                } else {
                    addLiteralToken(EMBED_DELIM_PARTIAL)
                }
                embeddedBlock(char)
            }
            else -> {
                when {
                    // a minus or a digit starts a number
                    char == '-' || isDigit(char) -> {
                        number()
                    }
                    // identifiers start with an alphabetic character or an underscore
                    isAlphaOrUnderscore(char) -> {
                        identifier()
                    }
                    else -> {
                        addLiteralToken(ILLEGAL_CHAR)
                    }
                }
            }
        }
    }

    private fun comment(): String {
        val commentToken = extractCommentToken()
        tokens.add(commentToken)
        return commentToken.value
    }

    /**
     * Extracts a comment token from [sourceScanner] without adding it to [tokens]
     */
    private fun extractCommentToken(): Token {
        // comments extend to end of the line
        while (sourceScanner.peek() != '\n' && !sourceScanner.eof()) sourceScanner.advance()

        val commentLexeme = sourceScanner.extractLexeme()
        return Token(COMMENT, commentLexeme, commentLexeme.text, emptyList())
    }

    /**
     * Returns true if the given [char] is whitespace
     */
    private fun isWhitespace(char: Char?): Boolean {
        return isInlineWhitespace(char) || char == '\n'
    }

    /**
     * Returns true if the given [char] is a non-newline whitespace
     */
    private fun isInlineWhitespace(char: Char?): Boolean {
        return char == ' ' || char == '\r' || char == '\t'
    }

    private fun identifier() {
        while (isAlphaNumeric(sourceScanner.peek())) sourceScanner.advance()

        val lexeme = sourceScanner.extractLexeme()
        val type: TokenType = KEYWORDS[lexeme.text] ?: IDENTIFIER
        addToken(type, lexeme, lexeme.text)
    }

    private fun string(delimiter: Char) {
        var hasUntokenizedStringCharacters = false
        while (sourceScanner.peek() != delimiter) {
            val nextStringChar = sourceScanner.peek() ?: break

            // check for illegal control characters
            if (nextStringChar.code in 0x00..0x1F
                // unlike JSON, we allow all whitespace control characters
                && !isWhitespace(nextStringChar)
            ) {
                if (hasUntokenizedStringCharacters) {
                    addLiteralToken(STRING)
                    hasUntokenizedStringCharacters = false
                }
                // advance past the illegal char and tokenize it
                sourceScanner.advance()
                addLiteralToken(STRING_ILLEGAL_CONTROL_CHARACTER)
            } else if (nextStringChar == '\\') {
                if (hasUntokenizedStringCharacters) {
                    addLiteralToken(STRING)
                    hasUntokenizedStringCharacters = false
                }

                // advance past the backslash
                sourceScanner.advance()

                if (sourceScanner.peek() == 'u') {
                    sourceScanner.advance()

                    // a '\u' unicode escape must be 4 chars long
                    for (c in 1..4) {
                        if (sourceScanner.peek() == delimiter || sourceScanner.eof()) {
                            break
                        }
                        sourceScanner.advance()
                    }
                    addLiteralToken(STRING_UNICODE_ESCAPE)
                } else {
                    // otherwise, this must be a one-char string escape, provided we're
                    // not up against EOF
                    if (!sourceScanner.eof()) {
                        sourceScanner.advance()
                    }
                    addLiteralToken(STRING_ESCAPE)
                }
            } else {
                sourceScanner.advance()
                hasUntokenizedStringCharacters = true
            }
        }

        if (hasUntokenizedStringCharacters) {
            addLiteralToken(STRING)
        }

        if (sourceScanner.eof()) {
            return
        } else {
            // not at EOF, so we must be looking at the quote that ends this string
            sourceScanner.advance()
            addLiteralToken(STRING_CLOSE_QUOTE)
        }
    }

    private fun embeddedBlock(delimChar: Char) {
        // consume non-newline whitespace right after our opening delimiter
        if (isInlineWhitespace(sourceScanner.peek())) {
            while (isInlineWhitespace(sourceScanner.peek())) {
                sourceScanner.advance()
            }
            addLiteralToken(WHITESPACE)
        }

        if (sourceScanner.peek() == '\n') {
            // no embed tag on this block
            sourceScanner.advance()
            addLiteralToken(EMBED_PREAMBLE_NEWLINE)
        } else if (sourceScanner.eof()) {
            return
        } else {
            // we have an embed tag, let's scan it
            while (!sourceScanner.eof()
                && !(sourceScanner.peek() == delimChar && sourceScanner.peekNext() == delimChar)
                && sourceScanner.peek() != '\n') {
                sourceScanner.advance()
            }

            // extract our embed tag (note: may be empty, that's supported)
            val embedTagLexeme = sourceScanner.extractLexeme()
            addToken(
                EMBED_TAG, embedTagLexeme,
                // trim any trailing whitespace from the embed tag's value
                embedTagLexeme.text.trim()
            )

            // lex this premature embed end
            if (sourceScanner.peek() == delimChar && sourceScanner.peekNext() == delimChar) {
                sourceScanner.advance()
                sourceScanner.advance()
                addLiteralToken(EMBED_CLOSE_DELIM)
                return
            }

            // consume the newline from after this embed tag
            if (sourceScanner.peek() == '\n') {
                sourceScanner.advance()
                addLiteralToken(EMBED_PREAMBLE_NEWLINE)
            } else if (sourceScanner.eof()) {
                return
            }
        }

        // we use this var to track if we need to consume escapes in an embed block so that we only walk its text
        // trying to replace escapes if we know we need to
        var hasEscapedEmbedEnd = false

        // read embedded content until the closing delimChar pair (or EOF in the case of an unclosed block)
        while (
            !sourceScanner.eof()
            && !(sourceScanner.peek() == delimChar && sourceScanner.peekNext() == delimChar)
        ) {
            if (sourceScanner.peek() == delimChar && sourceScanner.peekNext() == '\\') {
                // if this is all slashes until "delimChar", we're looking at an escaped embed delimiter
                sourceScanner.advance()
                while (sourceScanner.peek() == '\\') {
                    sourceScanner.advance()
                }
                if (sourceScanner.peek() == delimChar) {
                    hasEscapedEmbedEnd = true
                    sourceScanner.advance()
                }
            } else {
                sourceScanner.advance()
            }
        }

        val embedBlockLexeme = sourceScanner.extractLexeme()

        val trimmedEmbedBlockContent = trimMinimumIndent(embedBlockLexeme.text)
        val embedTokenValue = if (hasEscapedEmbedEnd) {
            /**
             * Here we trim the escaping slash from escaped EMBED_DELIMs.  This is slightly novel/intricate,
             * so some here's some clarifying notes (explained in terms of `%%`, i.e. [EmbedDelim.Percent]
             * the default [EmbedDelim]. [EmbedDelim.Dollar] naturally works the same):
             *
             * - an escaped [TokenType.EMBED_CLOSE_DELIM] has its second percent char escaped: %\% yields %% inside of an embed.
             *   Note that this moves the escaping goalpost since we also need to allow %\% literally inside
             *   of embeds.  So: when evaluating escaped EMBED_DELIMs, we allow arbitrary `\`s before the second
             *   %, and consume one of them.  Then, %\\% gives %\% in the output, %\\\% gives %\\% in
             *   the output, etc
             *
             * - the regex for this ends up looking a bit crazy for a few reasons: \ needs to be double escaped in
             *   regex, so matching \ requires saying "\\\\".  Then we use the [\\\\]* to reinsert any additional
             *   slashes in the output, and we also need to escape the delimChar delimiter since it may be a regex
             *   special character, like '$' (EMBED_DELIM_ALT_CHAR), that needs special handling.
             */
            val literal = "$delimChar"
            val escaped = Regex.escape(literal)
            val pattern = "$escaped\\\\([\\\\]*)$escaped"
            val escapedReplacement = Regex.escapeReplacement(literal)
            trimmedEmbedBlockContent.replace(Regex(pattern), "$escapedReplacement\$1$escapedReplacement")
        } else {
            trimmedEmbedBlockContent
        }

        addToken(EMBED_CONTENT, embedBlockLexeme, embedTokenValue)

        /**
         * We scanned everything that wasn't an [TokenType.EMBED_CLOSE_DELIM] into our embed content,
         * so we're either at EOF or want to consume that [TokenType.EMBED_CLOSE_DELIM]
         */
        if (sourceScanner.eof()) {
            return
        } else {
            // process our closing delimChar pair
            sourceScanner.advance()
            sourceScanner.advance()
            addLiteralToken(EMBED_CLOSE_DELIM)
        }
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
        // since we're here, we know a number has been started with a minus sign or digit,
        // so lex the following chars generously (i.e. include alpha stuff if it's attached to this number)
        // as part of that number and look for problems in the number's structure later in the parser
        while (isAlphaNumeric(sourceScanner.peek())
            || sourceScanner.peek() == '+'
            || sourceScanner.peek() == '-'
            || sourceScanner.peek() == '.') sourceScanner.advance()
        addLiteralToken(NUMBER)
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
    private fun addToken(type: TokenType, lexeme: Lexeme, value: String): Location {

        val commentMetadata = commentMetadataForCurrentToken(type)

        tokens.add(Token(type, lexeme, value, commentMetadata.comments))

        for (commentLookaheadTokens in commentMetadata.lookaheadTokens) {
            tokens.add(commentLookaheadTokens)
        }
        return lexeme.location
    }

    /**
     * A [List] of comments for the [Token] currently being lexed, along with any [lookaheadTokens] extracted
     * in collecting those comments (for instance when collecting trailing comments) that should be added after
     * the [Token] currently being lexed
     */
    private data class CommentMetadata(val comments: List<String>, val lookaheadTokens: List<Token>)
    private fun commentMetadataForCurrentToken(currentTokenType: TokenType): CommentMetadata {
        // comments don't get associated with these types
        if (currentTokenType == COMMENT
            || currentTokenType == WHITESPACE
            || currentTokenType == EMBED_PREAMBLE_NEWLINE
            || currentTokenType == STRING
            || currentTokenType == STRING_ESCAPE
            || currentTokenType == STRING_UNICODE_ESCAPE
            || currentTokenType == STRING_ILLEGAL_CONTROL_CHARACTER
        ) {
            return CommentMetadata(emptyList(), emptyList())
        }

        val commentsForToken = currentCommentLines
        // reset our collection of seen comments to prepare to collect comments for the next token
        currentCommentLines = ArrayList()

        // these tokens open comment free constructs, so they cannot have trailing comments
        val acceptsTrailingComments = currentTokenType != STRING_OPEN_QUOTE
                && currentTokenType != EMBED_OPEN_DELIM

        // when appropriate, we lex ahead a bit looking for any trailing comments
        val trailingCommentTokens = ArrayList<Token>()
        if (acceptsTrailingComments) {
            // consume non-newline whitespace right after this token
            if (isInlineWhitespace(sourceScanner.peek())) {
                while (isInlineWhitespace(sourceScanner.peek())) {
                    sourceScanner.advance()
                }
                val whitespaceLexeme = sourceScanner.extractLexeme()
                trailingCommentTokens.add(
                    Token(
                        WHITESPACE,
                        whitespaceLexeme,
                        whitespaceLexeme.text,
                        emptyList()
                    )
                )
            }
            val trailingComment = if (sourceScanner.peek() == '#') {
                val commentToken = extractCommentToken()
                trailingCommentTokens.add(commentToken)
                commentToken.value
            } else {
                ""
            }

            if (trailingComment.isNotBlank()) {
                commentsForToken.add(trailingComment)
            }
        }
        return CommentMetadata(commentsForToken, trailingCommentTokens)
    }

    private fun isDigit(c: Char?): Boolean {
        return c in '0'..'9'
    }

    private fun isAlphaOrUnderscore(c: Char?): Boolean {
        return c in 'a'..'z' ||
                c in 'A'..'Z' || c == '_'
    }

    private fun isAlphaNumeric(c: Char?): Boolean {
        return isAlphaOrUnderscore(c) || isDigit(c)
    }
}

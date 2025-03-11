package org.kson.tools

import org.kson.parser.Lexer
import org.kson.parser.Token
import org.kson.parser.TokenType
import org.kson.parser.TokenType.*

/**
 * Fast and flexible [Token]-based indentation for Kson.  Note that the given Kson does not need to be valid
 * the formatter will still apply whatever appropriate indents it can (which is particularly useful for
 * finding a mismatched brace somewhere, for instance).
 *
 * Does not modify any other formatting or spacing aside from removing any leading empty lines and
 * ensuring there is no trailing whitespace aside from a newline at the end of non-empty files.
 *
 * @param indentType The [IndentType] to use for indentation
 */
class IndentFormatter(
    private val indentType: IndentType
) {
    /**
     * Indent the given source.  Uses the [indentType] this class was initialized with
     *
     * @param source The KSON source code to indent according to its nesting structures.  Note:
     *          Does not need to be valid kson.  Open and close delimiters will still be used to
     *          determine indentation
     * @return The indented KSON source code
     */
    fun indent(source: String): String {
        if (source.isBlank()) return ""
        val tokens = Lexer(source, gapFree = true).tokenize()
        val tokenLines = splitTokenLines(tokens)

        val result = StringBuilder()

        /**
         * A stack to track our construct nesting by remembering the [TokenType] that caused the nest
         */
        val nesting = ArrayDeque<TokenType>()

        /**
         * The list of [TokenType]s that require nesting starting on the next line
         */
        val nextNests = mutableListOf<TokenType>()

        /**
         * The count of how many [CLOSING_DELIMITERS] we saw that reduce the next line's level of nesting
         */
        var toBeClosedNestCount = 0

        for (line in tokenLines) {
            val lineContent = mutableListOf<String>()
            var tokenIndex = 0
            // true until we process the first non-whitespace token
            var isLeadingToken = true
            while (tokenIndex < line.size) {
                val token = line[tokenIndex]
                when (token.tokenType) {
                    /**
                     * Special handling for [EMBED_CONTENT], who's internal lines need to have their whitespace
                     * carefully preserved
                     */
                    EMBED_CONTENT -> {
                        // write out anything we've read before this embed block
                        result.append(prefixWithIndent(lineContent.joinToString(""), nesting.size))
                        // write out the lines of the embed content, indenting the whole block appropriately
                        result.append(indentEmbedContent(token, nesting.size))
                        tokenIndex++
                        // write the rest of the trailing content from this line
                        while (tokenIndex < line.size) {
                            result.append(line[tokenIndex].lexeme.text)
                            tokenIndex++
                        }

                        // we have written out this whole token line, so clear it and break to process the next line
                        lineContent.clear()
                        break
                    }
                    LIST_DASH -> {
                        if (isLeadingToken && (nesting.isEmpty() || nesting.last() != ANGLE_BRACKET_L)) {
                            // we're not nested in an explicitly delimited dash list, so each dash list element
                            // must provide its own indent
                            nesting.addLast(LIST_DASH)
                        }
                        lineContent.add(token.lexeme.text)
                    }
                    in OPENING_DELIMITERS -> {
                        // register the indent from this opening delim
                        nextNests.add(token.tokenType)
                        lineContent.add(token.lexeme.text)
                    }
                    in CLOSING_DELIMITERS -> {
                        if (line.subList(0, tokenIndex + 1).all {
                            CLOSING_DELIMITERS.contains(it.tokenType)
                                    || it.tokenType == WHITESPACE
                        }) {
                            // immediately apply the unindent from this close delimiter if it's part of a line
                            // of leading close delimiters
                            nesting.removeLastOrNull()
                        } else {
                            // else note we need to close some nests after this line is processed
                            toBeClosedNestCount++
                        }
                        lineContent.add(token.lexeme.text)
                    }
                    else -> {
                        lineContent.add(token.lexeme.text)
                    }
                }

                if (token.tokenType != WHITESPACE) {
                    // any tokens after this one are not leading tokens
                    isLeadingToken = false
                }
                tokenIndex++
            }

            if (lineContent.isNotEmpty()) {
                result.append(prefixWithIndent(lineContent.joinToString(""), nesting.size))
            }

            for (i in 1..toBeClosedNestCount) {
                if (nextNests.isNotEmpty()) {
                    nextNests.removeLast()
                } else if (nesting.isNotEmpty()) {
                    nesting.removeLast()
                }
            }
            toBeClosedNestCount = 0

            nesting.addAll(nextNests)
            nextNests.clear()

            if (nesting.lastOrNull() == LIST_DASH) {
                nesting.removeLast()
            }
        }

        return result.toString()
    }

    /**
     * Prefixes the given [content] with an indent computed from [nestingLevel]
     */
    private fun prefixWithIndent(content: String, nestingLevel: Int): String {
        return indentType.indentString.repeat(nestingLevel) + content
    }

    /**
     * Split the given [tokens] into lines of tokens corresponding to the lines of the original source that was tokenized.
     * Note: trims leading whitespace tokens/lines
     */
    private fun splitTokenLines(tokens: List<Token>): MutableList<List<Token>> {
        val lines = mutableListOf<List<Token>>()
        var currentLine = mutableListOf<Token>()

        // Skip any leading whitespace tokens
        var startIndex = 0
        while (startIndex < tokens.size && tokens[startIndex].tokenType == WHITESPACE) {
            startIndex++
        }

        for (token in tokens.subList(startIndex, tokens.size)) {
            if (token.tokenType == WHITESPACE && token.lexeme.text.contains('\n')) {
                currentLine.add(token.copy(lexeme = token.lexeme.copy(text = "\n")))
                lines.add(currentLine)

                var numAdditionalNewLines = token.lexeme.text.count { it == '\n' } - 1
                while (numAdditionalNewLines > 0) {
                    lines.add(mutableListOf(token.copy(lexeme = token.lexeme.copy(text = "\n"))))
                    numAdditionalNewLines--
                }
                currentLine = mutableListOf()
            } else {
                currentLine.add(token)
            }
        }

        lines.add(currentLine)

        return lines
    }

    /**
     * Prepend an appropriate indent to the lines in an [EMBED_CONTENT] token
     */
    private fun indentEmbedContent(
        token: Token,
        nestingLevel: Int
    ): String {
        val indent = indentType.indentString.repeat(nestingLevel + 1)
        val lines = token.lexeme.text.split('\n')

        return lines.joinToString("\n") { line ->
            indent + line
        }
    }
}

sealed class IndentType {
    abstract val indentString: String

    class Space(size: Int) : IndentType() {
        override val indentString = " ".repeat(size)
    }

    class Tab : IndentType() {
        override val indentString = "\t"
    }
}

private val OPENING_DELIMITERS = setOf(
    CURLY_BRACE_L,
    SQUARE_BRACKET_L,
    ANGLE_BRACKET_L
)

private val CLOSING_DELIMITERS = setOf(
    CURLY_BRACE_R,
    SQUARE_BRACKET_R,
    ANGLE_BRACKET_R
)

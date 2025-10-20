package org.kson.tools

import org.kson.*
import org.kson.KsonCore.parseToAst
import org.kson.parser.Lexer
import org.kson.parser.Token
import org.kson.parser.TokenType
import org.kson.ast.AstNode
import org.kson.parser.TokenType.*

/**
 * Format the given Kson source according to [formatterConfig]
 *
 * @param ksonSource the Kson to format
 * @param formatterConfig the [KsonFormatterConfig] to apply when formatting
 */
fun format(ksonSource: String, formatterConfig: KsonFormatterConfig = KsonFormatterConfig()): String {
    if (ksonSource.isBlank()) return ""
    return KsonParseResult(
        parseToAst(ksonSource, CoreCompileConfig(ignoreErrors = true)),
        CompileTarget.Kson(preserveComments = true, formatterConfig)
    ).kson ?: ksonSource
}

enum class FormattingStyle {
    PLAIN,
    DELIMITED,
    COMPACT,
    CLASSIC,
}

data class KsonFormatterConfig(
    val indentType: IndentType = IndentType.Space(2),
    val formattingStyle: FormattingStyle = FormattingStyle.PLAIN
)

/**
 * Fast and flexible [Token]-based indentation for Kson.  Note that the given Kson does not need to be valid
 * the formatter will still apply whatever appropriate indents it can (which is particularly useful for
 * finding a mismatched brace somewhere, for instance).
 *
 * Does not modify any other formatting or spacing aside from removing any leading empty lines and
 * ensuring there is no trailing whitespace aside from a newline at the end of non-empty files.
 * TODO: this formatter has been superseded by the new [AstNode]-based formatter and now only
 *  supports use cases using [getCurrentLineIndentLevel] to compute the line indent from limited
 *  context (i.e. for when a user presses enter in an edit).  This can likely be refactored and
 *  simplified to focus on serving that use case
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
     *   Does not need to be valid kson.  Open and close delimiters will still be used to
     *   determine indentation
     * @param snippetNestingLevel This param indicates that [source] is a snippet that we are indenting, and that we
     *   should assume the snippet itself is already indented nested to a level of [snippetNestingLevel].
     * @return The indented KSON source code
     */
    @Deprecated(
        "This formatter is no long a good general purpose formatter. " +
                "See the TODO in this class's doc for details"
    )
    private fun indent(source: String, snippetNestingLevel: Int? = null): String {
        if (source.isBlank() && snippetNestingLevel == null) return ""
        val tokens = Lexer(source, gapFree = true).tokenize()
        val tokenLines = splitTokenLines(tokens)

        val result = StringBuilder()

        /**
         * A stack to track our construct nesting by remembering the [TokenType] that caused the nest
         */
        val nesting = ArrayDeque<TokenType>()
        if (snippetNestingLevel != null) {
            for (i in 1..snippetNestingLevel) {
                /**
                 * We'll denote our snippet nests with the [WHITESPACE] token since they
                 * are "synthetic" nests represent indents to preserve in the input
                 */
                nesting.add(WHITESPACE)
            }
        }

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
            while (tokenIndex < line.size) {
                val token = line[tokenIndex]
                when (token.tokenType) {
                    /**
                     * Special handling for [EMBED_CONTENT], who's internal lines need to have their whitespace
                     * carefully preserved
                     */
                    EMBED_CONTENT -> {
                        val embedContentIndent = if (line.subList(0, tokenIndex + 1).any {
                                it.tokenType == COLON
                            }) {
                            /**
                             * if our embed preamble is on the same line as an object key, we'll indent the embed
                             * content on the next line
                             */
                            nesting.size + 1
                        } else {
                            nesting.size
                        }

                        // write out anything we've read before this embed block
                        result.append(prefixWithIndent(lineContent.joinToString(""), nesting.size))
                        // write out the lines of the embed content, indenting the whole block appropriately
                        result.append(prefixWithIndent(token.value, embedContentIndent, true))
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

                    COLON -> {
                        // if we're currently nested in a COLON, consider that nest closed on this line
                        // since we're starting a new object property
                        if (nextNests.isEmpty() && nesting.lastOrNull() == COLON) {
                            nesting.removeLastOrNull()
                        }

                        // if everything after this object colon is whitespace, we want to nest the value
                        // that starts on the next line
                        if (line.subList(tokenIndex + 1, line.lastIndex).all { it.tokenType == WHITESPACE }) {
                            nextNests.add(COLON)
                        }
                        lineContent.add(token.lexeme.text)
                    }

                    in OPENING_DELIMITERS -> {
                        // register the indent from this opening delim
                        nextNests.add(token.tokenType)
                        lineContent.add(token.lexeme.text)
                    }

                    in CLOSING_DELIMITERS -> {
                        /**
                         * [CLOSING_DELIMITERS] that are part of a leading line of leading close delimiters
                         * on a line may trigger an immediate un-nest
                         */
                        if (line.subList(0, tokenIndex + 1).all {
                                CLOSING_DELIMITERS.contains(it.tokenType)
                                        || it.tokenType == WHITESPACE
                            }) {
                            if (nesting.lastOrNull() == COLON) {
                                /**
                                 * If we spot a [COLON] nest as we're closing things, that must be closed too:
                                 * a [COLON] nest is only valid as long as it has a sub-nest
                                 */
                                nesting.removeLast()
                            }
                            // unnest this leading close delimiter immediately if it matches the current open nest
                            if (nesting.lastOrNull() == CLOSE_TO_OPEN_MAP[token.tokenType]) {
                                nesting.removeLastOrNull()
                            }
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
        }

        return result.toString()
    }

    fun getCurrentLineIndentLevel(prevLine: String, currentLine: String = ""): Int {
        if (prevLine.isEmpty()) return 0

        val sourceLines = prevLine.split('\n')
        val lastLineIndentLevel = if (sourceLines.isNotEmpty()) {
            val lastLine = sourceLines.last()
            countIndentLevels(lastLine)
        } else 0

        val indentedResult = indent(prevLine + "\n" + currentLine, lastLineIndentLevel)
        val resultLines = indentedResult.split('\n')
        return countIndentLevels(resultLines.last())
    }

    /**
     * Counts how many indent levels are at the start of the given line
     */
    private fun countIndentLevels(line: String): Int {
        val indentSize = indentType.indentString.length
        var leadingWhitespace = 0
        for (char in line) {
            if (!char.isWhitespace()) break
            leadingWhitespace++
        }
        return leadingWhitespace / indentSize
    }

    /**
     * Prefixes the given [content] with an indent computed from [nestingLevel]
     */
    private fun prefixWithIndent(content: String, nestingLevel: Int, keepTrailingIndent: Boolean = false): String {
        val indent = indentType.indentString.repeat(nestingLevel)
        val lines = content.split('\n')

        return lines.mapIndexed { index, line ->
            /**
             * If [content] ends in a newline, the next line does not belong to it,
             * so don't indent it unless our caller demands it
             */
            if (!keepTrailingIndent && index == lines.lastIndex && line.isEmpty() && content.endsWith('\n')) {
                line
            } else {
                indent + line
            }
        }.joinToString("\n")
    }

    /**
     * Split the given [tokens] into lines of tokens corresponding to the lines of the original source that was tokenized.
     * Note: trims leading whitespace tokens/lines, and may gather multiple lines of underlying source in one logical
     *   "token line" when appropriate
     */
    private fun splitTokenLines(tokens: List<Token>): MutableList<List<Token>> {
        val tokenLines = mutableListOf<List<Token>>()
        var currentLine = mutableListOf<Token>()

        // Skip any leading whitespace tokens
        var startIndex = 0
        while (startIndex < tokens.size && tokens[startIndex].tokenType == WHITESPACE) {
            startIndex++
        }

        var index = startIndex
        while (index < tokens.size) {
            val token = tokens[index]

            if (token.tokenType == WHITESPACE && token.lexeme.text.contains('\n')) {
                currentLine.add(token.copy(lexeme = token.lexeme.copy(text = "\n")))
                tokenLines.add(currentLine)

                var numAdditionalNewLines = token.lexeme.text.count { it == '\n' } - 1
                while (numAdditionalNewLines > 0) {
                    tokenLines.add(mutableListOf(token.copy(lexeme = token.lexeme.copy(text = "\n"))))
                    numAdditionalNewLines--
                }
                currentLine = mutableListOf()

                /**
                 * If we see a comment starting a newline, we gather all its lines and prefix the next
                 * "tokenLine" with it so it gets consistently indented with the content it precedes/comments
                 */
                val nextToken = tokens.getOrNull(index + 1)
                if (nextToken?.tokenType == COMMENT) {
                    var commentToken: Token = nextToken
                    while (commentToken.tokenType == COMMENT
                        || (commentToken.tokenType == WHITESPACE && commentToken.lexeme.text.contains('\n'))
                    ) {
                        index++
                        if (commentToken.tokenType == WHITESPACE) {
                            val numNewlines = commentToken.lexeme.text.count { it == '\n' }
                            repeat(numNewlines) {
                                currentLine.add(commentToken.copy(lexeme = commentToken.lexeme.copy(text = "\n")))
                            }
                        } else {
                            currentLine.add(commentToken)
                        }
                        commentToken = tokens.getOrNull(index + 1) ?: break
                    }
                }
            } else {
                currentLine.add(token)
            }

            index++
        }

        tokenLines.add(currentLine)

        return tokenLines
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

private val CLOSE_TO_OPEN_MAP = mapOf(
    CURLY_BRACE_R to CURLY_BRACE_L,
    SQUARE_BRACKET_R to SQUARE_BRACKET_L,
    ANGLE_BRACKET_R to ANGLE_BRACKET_L
)

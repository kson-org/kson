package org.kson.parser

import org.kson.ast.*

/**
 * Defines the Kson parser, implemented as a recursive descent parser which directly implements
 * the following grammar, one method per grammar rule:
 *
 * ```
 * kson -> (objectInternals | value) EOF ;
 * objectInternals -> ( keyword value )* ;
 * value -> objectDefinition
 *        | list
 *        | literal
 *        | embedBlock ;
 * objectDefinition -> ( objectName | "" ) "{" objectInternals "}" ;
 * list -> "[" "]"
 *       | "[" value "]"
 *       | "[" (value ",")* value? "]"
 * keyword -> ( IDENTIFIER | STRING ) ":" ;
 * literal -> STRING, NUMBER, "true", "false", "null" ;
 * embeddedBlock -> "```" (embedTag) NEWLINE CONTENT "```" ;
 * ```
 *
 * See [section 5.1 here](https://craftinginterpreters.com/representing-code.html#context-free-grammars)
 * for details on this grammar notation.
 *
 * See [section 6.2 here](https://craftinginterpreters.com/parsing-expressions.html#recursive-descent-parsing)
 * for guidance on the implementation approach.
 */
class Parser(tokens: List<Token>) {
    private val tokenScanner = TokenScanner(tokens)

    /**
     * kson -> (objectInternals | value) EOF ;
     */
    fun parse(): KsonRoot {
        val objectInternals = objectInternals()
        if (objectInternals != null) {
            return KsonRoot(ObjectDefinitionNode(internalsNode = objectInternals))
        }

        return KsonRoot(value() ?: TODO("make this a user-friendly parse error"))
        // parser todo validate we're at EOF to ensure we've parsed everything
    }

    /**
     * objectInternals -> ( keyword value )* ;
     * parser todo need to support an optional comma here
     */
    private fun objectInternals(): ObjectInternalsNode? {
        val properties = ArrayList<PropertyNode>()

        // parse
        while (true) {
            val keywordNode = keyword() ?: break
            val valueNode = value() ?: TODO("make this a user-friendly parse error")
            properties.add(
                PropertyNode(
                    keywordNode,
                    valueNode
                )
            )
        }

        if (properties.isEmpty()) {
            // found no properties, not looking at an objectInternals
            return null
        }

        return ObjectInternalsNode(properties)
    }

    /**
     * value -> objectDefinition
     *        | list
     *        | literal
     *        | embedBlock ;
     */
    private fun value(): ValueNode? {
        return objectDefinition() ?: list() ?: literal() ?: embedBlock()
    }

    /**
     * objectDefinition -> ( objectName | "" ) "{" objectInternals "}" ;
     */
    private fun objectDefinition(): ObjectDefinitionNode? {
        if (tokenScanner.peek() == TokenType.BRACE_L
            || tokenScanner.peek() == TokenType.IDENTIFIER && tokenScanner.peekNext() == TokenType.BRACE_L
        ) {
            val objectName = if (tokenScanner.peek() == TokenType.IDENTIFIER) {
                // parser todo shore up our casting
                tokenScanner.advance().value as String
            } else {
                ""
            }

            // parser todo syntax error if no brace/malformed object
            // drop the BRACE_R
            tokenScanner.drop()
            val objectInternals = objectInternals()
            return ObjectDefinitionNode(objectName, objectInternals)
        } else {
            // not an objectDefinition
            return null
        }
    }

    /**
     * list -> "[" "]"
     *       | "[" value "]"
     *       | "[" (value ",")* value? "]"
     */
    private fun list(): ListNode? {
        if (tokenScanner.peek() == TokenType.BRACKET_L) {
            // drop the BRACKET_L
            tokenScanner.drop()

            val values = ArrayList<ValueNode>()
            while (tokenScanner.peek() != TokenType.BRACKET_R) {
                val value = value()
                    ?:
                    if (tokenScanner.peek() == TokenType.COMMA) {
                        // drop the COMMA
                        tokenScanner.drop()
                        continue
                    } else {
                        // no more values, list is done
                        break
                    }
                values.add(value)
            }

            // parser todo syntax error if no bracket/malformed list
            // drop the BRACKET_R
            tokenScanner.drop()
            return ListNode(values)
        } else {
            // not a list
            return null
        }
    }

    /**
     * keyword -> ( IDENTIFIER | STRING ) ":" ;
     */
    private fun keyword(): KeywordNode? {
        if ((tokenScanner.peek() == TokenType.IDENTIFIER || tokenScanner.peek() == TokenType.STRING)
            && tokenScanner.peekNext() == TokenType.COLON
        ) {
            val keywordToken = tokenScanner.advance()
            // parser todo shore up our casting
            val keywordNode = if (keywordToken.tokenType == TokenType.STRING) {
                StringNode(keywordToken.value as String)
            } else {
                IdentifierNode(keywordToken.value as String)
            }
            // drop the COLON
            tokenScanner.drop()
            return keywordNode
        } else {
            // not a keyword
            return null
        }
    }

    /**
     * literal -> STRING, NUMBER, "true", "false", "null" ;
     */
    private fun literal(): ValueNode? {
        if (setOf(
                TokenType.STRING,
                TokenType.IDENTIFIER,
                TokenType.NUMBER,
                TokenType.TRUE,
                TokenType.FALSE,
                TokenType.NULL
            ).any { it == tokenScanner.peek() }
        ) {
            val token = tokenScanner.advance()
            // parser todo shore up our casting
            return when (token.tokenType) {
                TokenType.STRING -> StringNode(token.value as String)
                TokenType.IDENTIFIER -> IdentifierNode(token.value as String)
                TokenType.NUMBER -> NumberNode(token.value as Number)
                TokenType.TRUE -> TrueNode()
                TokenType.FALSE -> FalseNode()
                TokenType.NULL -> NullNode()
                else -> throw RuntimeException("should not get here... we peek()'ed to make sure we had one of these tokens")
            }
        } else {
            return null
        }
    }

    /**
     * embeddedBlock -> "```" (embedTag) NEWLINE CONTENT "```" ;
     */
    private fun embedBlock(): ValueNode? {
        if (tokenScanner.peek() == TokenType.EMBED_START) {
            // drop the EMBED_START
            tokenScanner.drop()
            val embedTag = if (tokenScanner.peek() == TokenType.EMBED_TAG) {
                // parser todo shore up our casting
                tokenScanner.advance().value as String
            } else {
                ""
            }

            val embedBlockNode = if (tokenScanner.peek() == TokenType.EMBED_END) {
                EmbedBlockNode(embedTag, "")
            } else {
                // parser todo shore up our casting
                EmbedBlockNode(embedTag, tokenScanner.advance().value as String)
            }

            // drop the EMBED_END
            tokenScanner.drop()
            return embedBlockNode
        } else {
            // not an embedBlock
            return null
        }
    }
}

/**
 * scanner todo this functions near identically to [SourceScanner], but rather than scanning [Char]s, it scans [Token]s.
 *             See if it makes sense to refactor duplication into into a base "[StreamScanner]"
 */
private class TokenScanner(private val source: List<Token>) {
    private var selectionStartOffset = 0
    private var selectionEndOffset = 0

    // scanner todo: refactor musing: we want TokenType here for peak peek convenience, not Token.
    //               Perhaps had we prematurely refactored into a base "[StreamScanner]"
    //               this optimization would have been missed
    fun peek(): TokenType {
        return if (selectionEndOffset >= source.size) TokenType.EOF else source[selectionEndOffset].tokenType
    }

    fun peekNext(): TokenType {
        return if (selectionEndOffset + 1 >= source.size) TokenType.EOF else source[selectionEndOffset + 1].tokenType
    }

    fun advance(): Token {
        return source[selectionEndOffset++]
    }

    fun drop(): Location {
        val droppedLexemeLocation = currentLocation()
        selectionEndOffset++
        selectionStartOffset = selectionEndOffset
        return droppedLexemeLocation
    }

    private fun currentLocation(): Location {
        val startTokenLocation = source[selectionStartOffset].lexeme.location
        val endTokenLocation = source[selectionEndOffset].lexeme.location
        return Location(
            startTokenLocation.firstLine,
            startTokenLocation.firstColumn,
            endTokenLocation.lastLine,
            endTokenLocation.lastColumn
        )
    }
}

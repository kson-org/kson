package org.kson

import org.kson.ast.KsonRoot
import org.kson.parser.*
import org.kson.parser.messages.MessageType

/**
 * A Json document specifying just `true` is the "trivial" schema that matches everything,
 * and so is equivalent to not having a schema.  See https://json-schema.org/draft/2020-12/json-schema-core#section-4.3.2
 * for more detail
 */
private const val NO_SCHEMA = "true"

class Kson {
    companion object {
        fun parse(source: String, maxNestingLevel: Int = DEFAULT_MAX_NESTING_LEVEL): ParseResult {
            return parse(source, NO_SCHEMA, maxNestingLevel)
        }

        fun parse(source: String, schemaJson: String = NO_SCHEMA, maxNestingLevel: Int = DEFAULT_MAX_NESTING_LEVEL): ParseResult {
            val messageSink = MessageSink()
            val tokens = Lexer(source).tokenize()
            if (tokens[0].tokenType == TokenType.EOF) {
                messageSink.error(tokens[0].lexeme.location, MessageType.BLANK_SOURCE.create())
                return ParseResult(null, tokens, messageSink)
            }
            val ast =  if (messageSink.hasErrors()) {
                // parsing failed at the lexing stage
                return ParseResult(null, tokens, messageSink)
            } else {
                val builder = KsonBuilder(tokens)
                Parser(builder, maxNestingLevel).parse()
                builder.buildTree(messageSink)
            }

            if (schemaJson == NO_SCHEMA) {
                return ParseResult(ast, tokens, messageSink)
            } else {
                TODO("Json Schema support for Kson not yet implemented")
            }
        }
    }
}

/**
 * @param ast is the parsed AST, or null if the source was invalid kson (in which cases [messages] will contain errors)
 * @param lexedTokens
 * @param messageSink the messages logged during parsing to return to the user
 */
data class ParseResult(
    /**
     * The parsed AST, or null if the source was invalid kson (in which cases [hasErrors] will be true)
     */
    val ast: KsonRoot?,

    /**
     * The tokens lexed from the input source, provided for debug purposes
     */
    val lexedTokens: List<Token>,

    /**
     * The message sink used during parsing, exposed by this class in [messages] and [hasErrors]
     */
    private val messageSink: MessageSink) {

    /**
     * The user-facing messages logged during this parse
     */
    val messages = messageSink.loggedMessages()

    /**
     * True if the input source could not be parsed.  [messages] will contain errors in this case.
     */
    fun hasErrors(): Boolean {
        return messageSink.hasErrors()
    }
}
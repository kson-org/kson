package org.kson

import org.kson.ast.KsonRoot
import org.kson.parser.*

class Kson {
    companion object {
        fun parse(source: String, maxNestingLevel: Int = DEFAULT_MAX_NESTING_LEVEL): ParseResult {
            val messageSink = MessageSink()
            val tokens = Lexer(source, messageSink).tokenize()
            val ast =  if (messageSink.hasErrors()) {
                // parsing failed at the lexing stage
                return ParseResult(null, tokens, messageSink)
            } else {
                val builder = KsonBuilder(tokens)
                Parser(builder, maxNestingLevel).parse()
                builder.buildTree(messageSink)
            }

            return ParseResult(ast, tokens, messageSink)
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
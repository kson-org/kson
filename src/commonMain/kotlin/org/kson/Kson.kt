package org.kson

import org.kson.ast.KsonRoot
import org.kson.parser.*

class Kson {
    companion object {
        fun parse(source: String): ParseResult {
            val messageSink = MessageSink()
            val tokens = Lexer(source, messageSink).tokenize()
            val ast = Parser(tokens).parse()
            return ParseResult(ast, tokens, messageSink.loggedMessages())
        }
    }
}

data class ParseResult(val ast: KsonRoot, val lexedTokens: List<Token>, val messages: List<LoggedMessage>)
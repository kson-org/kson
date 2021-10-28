package org.kson

import org.kson.ast.KsonRoot
import org.kson.parser.Lexer
import org.kson.parser.MessageSink
import org.kson.parser.Parser

class Kson {
    fun parse(source: String): KsonRoot {
        val messageSink = MessageSink()
        val tokens = Lexer(source, messageSink).tokenize()
        return Parser(tokens).parse()
    }
}
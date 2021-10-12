package org.kson.parser

class MessageSink {
    private val messages = mutableListOf<String>()

    fun error(line: Int, message:String) {
        messages.add(format(line, message))
    }

    fun hasErrors(): Boolean {
        return messages.isNotEmpty()
    }

    fun print(): String {
        return messages.joinToString("\n")
    }

    private fun format(line: Int, message: String): String {
        return "Line: $line, $message"
    }
}
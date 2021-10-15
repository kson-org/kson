package org.kson.parser

/**
 * todo improve errors:
 *         - need to centralize message definitions, assign types for testing ease
 *         - need to have test coverage for known error cases
 */
class MessageSink {
    private val messages = mutableListOf<String>()

    fun error(lexeme: Lexeme, message:String) {
        messages.add(format(lexeme.location, message))
    }

    fun hasErrors(): Boolean {
        return messages.isNotEmpty()
    }

    fun print(): String {
        return messages.joinToString("\n")
    }

    private fun format(location: Location, message: String): String {
        return "Error:${location.firstLine}.${location.firstColumn}\u2013${location.lastLine}.${location.lastColumn}, $message"
    }
}
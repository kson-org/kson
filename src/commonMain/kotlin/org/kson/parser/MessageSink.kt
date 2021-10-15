package org.kson.parser

/**
 * todo improve errors:
 *         - need char ranges for proper error highlights
 *         - need to centralize message definitions, assign types for testing ease
 *         - need to have test coverage for known error cases
 */
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
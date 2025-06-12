package org.kson.parser

import org.kson.stdlibx.collections.toImmutableList
import org.kson.parser.messages.Message

data class LoggedMessage(
    val location: Location,
    val message: Message
) {
    companion object {
        /**
         * Print a user-friendly version of a [List] of [LoggedMessage].  Note: locations
         * are output as base-1 indexed firstLine/firstColumn/lastLine/lastColumn numbers
         * following [the gnu standard](https://www.gnu.org/prep/standards/html_node/Errors.html)
         * for this sort of output
         */
        fun print(loggedMessages: List<LoggedMessage>): String {
            return loggedMessages.joinToString("\n") { loggedMessage ->
                val location = loggedMessage.location
                "Error:${location.firstLine + 1}.${location.firstColumn + 1}" +
                        " - ${location.lastLine + 1}.${location.lastColumn + 1}, ${
                            loggedMessage.message
                        }"
            }
        }
    }
}

/**
 * todo currently assumes everything is an error.  We'll refactor if/when we support WARN/INFO/etc.
 */
class MessageSink {
    private val messages = mutableListOf<LoggedMessage>()

    fun error(location: Location, message: Message) {
        messages.add(LoggedMessage(location, message))
    }

    fun hasErrors(): Boolean {
        return messages.isNotEmpty()
    }

    /**
     * Return the list of all [LoggedMessage]s sent to this [MessageSink],
     * in the order they were logged
     */
    fun loggedMessages(): List<LoggedMessage> {
        return messages.toImmutableList()
    }
}
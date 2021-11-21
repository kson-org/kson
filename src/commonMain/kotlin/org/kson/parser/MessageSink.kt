package org.kson.parser

import org.kson.collections.ImmutableList
import org.kson.collections.toImmutableList
import org.kson.parser.messages.Message

data class LoggedMessage(
    val location: Location,
    val message: Message,
    val args: Array<out String?>
) {
    companion object {
        /**
         * Print a user-friendly version of a [List] of [LoggedMessage]
         */
        fun print(messages: List<LoggedMessage>): String {
            return messages.map {
                val location = it.location
                "Error:${location.firstLine}.${location.firstColumn}" +
                        "\u2013${location.lastLine}.${location.lastColumn}, ${
                            it.message.format(
                                *it.args
                            )
                        }"
            }.joinToString("\n")
        }
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as LoggedMessage

        if (location != other.location) return false
        if (message != other.message) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = location.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }
}

/**
 * todo currently assumes everything is an error.  We'll refactor if/when we support WARN/INFO/etc.
 */
class MessageSink {
    private val messages = mutableListOf<LoggedMessage>()

    fun error(location: Location, message: Message, vararg args: String?) {
        messages.add(LoggedMessage(location, message, args))
    }

    fun hasErrors(): Boolean {
        return messages.isNotEmpty()
    }

    /**
     * Return the list of all [LoggedMessage]s sent to this [MessageSink],
     * in the order they were logged
     */
    fun loggedMessages(): ImmutableList<LoggedMessage> {
        return messages.toImmutableList()
    }
}
package org.kson.parser.messages

/**
 * Instances of [Message] are created with [MessageType.create]
 */
interface Message {
    val type: MessageType
    override fun toString(): String
}

/**
 * Enum for all our user-facing messages.
 *
 * This keep things organized for if/when we want to localize,
 * and also facilitates easy/robust testing against [MessageType] types (rather than for instance brittle string
 * matches on error message content)
 */
enum class MessageType {
    EMBED_BLOCK_DANGLING_DELIM {
        override fun expectedArgs(): List<String> {
            return listOf("Embed delimiter character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val embedDelimChar = parsedArgs.getArg("Embed delimiter character")
            return "Dangling embed delimiter.  Did you mean \"$embedDelimChar$embedDelimChar\"?"
        }
    },
    EMBED_BLOCK_BAD_START {
        override fun expectedArgs(): List<String> {
            return listOf("Embed Tag Name", "Embed delimiter character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val embedTag = parsedArgs.getArg("Embed Tag Name")
            val embedDelimChar = parsedArgs.getArg("Embed delimiter character")
            return "This Embedded Block's content must start on the line after the opening '$embedDelimChar$embedDelimChar${embedTag ?: ""}"
        }
    },
    EMBED_BLOCK_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return listOf("Embed delimiter")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val embedDelimiter = parsedArgs.getArg("Embed delimiter")
            return "Unclosed \"$embedDelimiter\""
        }
    },
    UNEXPECTED_CHAR {
        override fun expectedArgs(): List<String> {
            return listOf("Unexpected Character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val unexpectedCharacter = parsedArgs.getArg("Unexpected Character")
            return "Unexpected character: $unexpectedCharacter"
        }
    },
    EOF_NOT_REACHED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unexpected trailing content.  The previous content parsed as a complete Kson document."
        }
    },
    LIST_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unclosed list"
        }
    },
    OBJECT_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unclosed object"
        }
    },
    STRING_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unterminated string"
        }
    },
    DANGLING_EXP_INDICATOR {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Dangling exponent indicator"
        }
    },
    ILLEGAL_MINUS_SIGN {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "A dash `-` must be followed by a space (to make a list element), or a number (to make a negative number)"
        }
    },
    DANGLING_LIST_DASH {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "A list dash `- ` must be followed by a value"
        }
    };

    /**
     * Create a [Message] instance from this [MessageType].  The [args] expected here are defined in this
     * [MessageType]'s [expectedArgs]
     */
    fun create(vararg args: String?): Message {
        val givenArgs = ArrayList<String>()
        for ((index, value) in args.withIndex()) {
            if (value == null) {
                throw IllegalArgumentException(
                    "Illegal `null` arg at position $index of the given `args`.  Message arguments must not be `null`."
                )
            }
            givenArgs.add(value)
        }
        val expectedArgs = expectedArgs()
        val numExpectedArgs = expectedArgs.size
        if (givenArgs.size != numExpectedArgs) {
            throw RuntimeException(
                "`${this::class.simpleName}.${this::create.name}` requires $numExpectedArgs arg(s) for: ${
                    renderArgList(expectedArgs, "`")
                }, but got ${givenArgs.size}: " + renderArgList(givenArgs)
            )
        }

        val messageType = this
        return object: Message {
            override val type = messageType
            private val renderedMessage = type.doFormat(ParsedErrorArgs(messageType, givenArgs))

            override fun toString(): String {
                return renderedMessage
            }
        }
    }

    /**
     * The list of arguments this [MessageType] expects/requires to [create] a [Message], in the order they
     * are expected to be passed to [create]
     */
    protected abstract fun expectedArgs(): List<String>

    /**
     * Members must implement this to format themselves as [String]s, given arguments [parsedArgs].
     *
     * [parsedArgs] maps the arg names given by [expectedArgs] to the arg values passed to [create]
     */
    protected abstract fun doFormat(parsedArgs: ParsedErrorArgs): String

    /**
     * Lookup wrapper for [MessageType.create] arguments to protect against typo'ed lookups
     */
    protected class ParsedErrorArgs(private val messageType: MessageType, args: List<String>) {
        private val parsedArgs: Map<String, String>

        init {
            // zip the given args up with corresponding argName for lookups
            parsedArgs = messageType.expectedArgs().zip(args).toMap()
        }

        /**
         * Get an arg by name, or error loudly if no such arg exists
         */
        fun getArg(argName: String): String? {
            if (parsedArgs[argName] != null) {
                return parsedArgs[argName]
            } else {
                // someone's asking for an invalid or typo'ed arg name
                throw RuntimeException(
                    "Invalid arg name \"" + argName + "\" given for " + messageType::class.simpleName
                            + ".  \"" + argName + "\" is not defined in " + messageType::expectedArgs.name
                            + ": " + renderArgList(messageType.expectedArgs())
                )
            }
        }
    }

}

private fun renderArgList(args: List<String>, quote: String = "\"") = if (args.isEmpty()) {
    "[]"
} else {
    args.joinToString("$quote, $quote", "[ $quote", "$quote ]")
}

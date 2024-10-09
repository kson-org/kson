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
    BLANK_SOURCE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unable to parse a blank file.  A Kson document must describe a value."
        }
    },
    EMBED_BLOCK_DANGLING_DELIM {
        override fun expectedArgs(): List<String> {
            return listOf("Embed delimiter character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val embedDelimChar = parsedArgs.getArg("Embed delimiter character")
            return "Incomplete embed delimiter.  Did you mean \"$embedDelimChar$embedDelimChar\"?"
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
    LIST_NO_OPEN {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "This must close a list, but this is not a list"
        }
    },
    LIST_INVALID_ELEM {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unable to parse this list element as a legal Kson value"
        }
    },
    LIST_STRAY_COLON {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Colons may only be used inside an object to denote a keyword.  This is a list."
        }
    },
    OBJECT_BAD_INTERNALS {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Object properties must be `key: value` pairs"
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
    OBJECT_NO_OPEN {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "This must close an object, but this is not an object"
        }
    },
    OBJECT_KEY_NO_VALUE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "This object key must be followed by a value"
        }
    },
    STRING_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unclosed string"
        }
    },
    STRING_CONTROL_CHARACTER {
        override fun expectedArgs(): List<String> {
            return listOf("Control Character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val badControlCharArg = parsedArgs.getArg("Control Character")
            if (badControlCharArg?.length != 1) {
                throw RuntimeException("Expected arg to be a single control character")
            }
            val badControlChar = badControlCharArg[0]

            return "Non-whitespace control characters must not be embedded directly in strings. " +
                    "Please use the Unicode escape for this character instead: \"\\u${badControlChar.code.toString().padStart(4, '0')}\""
        }
    },
    STRING_BAD_UNICODE_ESCAPE {
        override fun expectedArgs(): List<String> {
            return listOf("Unicode `\\uXXXX` Escape")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val badUnicodeEscape = parsedArgs.getArg("Unicode `\\uXXXX` Escape")
            return "Invalid Unicode code point: $badUnicodeEscape.  Must be a 4 digit hex number"
        }
    },
    STRING_BAD_ESCAPE {
        override fun expectedArgs(): List<String> {
            return listOf("\\-prefixed String Escape")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val badStringEscape = parsedArgs.getArg("\\-prefixed String Escape")
            return "Invalid string escape: $badStringEscape"
        }
    },
    INVALID_DIGITS {
        override fun expectedArgs(): List<String> {
            return listOf("Unexpected Character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val unexpectedCharacter = parsedArgs.getArg("Unexpected Character")
            return "Invalid character `$unexpectedCharacter` found in this number"
        }
    },
    DANGLING_EXP_INDICATOR {
        override fun expectedArgs(): List<String> {
            return listOf("Exponent character: E or e")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val exponentCharacter = parsedArgs.getArg("Exponent character: E or e")
            return "Dangling exponent error: `$exponentCharacter` must be followed by an exponent"
        }
    },

    /**
     * Catch-all for characters we don't recognize as legal Kson that don't (yet?) have a more specific and
     * helpful error such as [OBJECT_NO_OPEN] or [DANGLING_LIST_DASH]
     */
    ILLEGAL_CHARACTERS {
        override fun expectedArgs(): List<String> {
            return listOf("The Illegal Characters")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val illegalCharacter = parsedArgs.getArg("The Illegal Characters")
            return "Kson does not allow \"$illegalCharacter\" here"
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
    DANGLING_DECIMAL {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "A decimal must be followed by digits"
        }
    },
    DANGLING_LIST_DASH {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "A list dash `- ` must be followed by a value"
        }
    },
    EMPTY_COMMAS {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Redundant comma found. A comma must delimit a value, one comma per value"
        }
    },
    MAX_NESTING_LEVEL_EXCEEDED {
        override fun expectedArgs(): List<String> {
            return listOf("Max Nesting Level")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maxNestingLevel = parsedArgs.getArg("Max Nesting Level")
            return "The nesting of objects and/or lists in this Kson " +
                    "exceeds the configured maximum supported nesting level of $maxNestingLevel"
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

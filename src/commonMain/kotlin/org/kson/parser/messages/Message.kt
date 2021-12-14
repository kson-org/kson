package org.kson.parser.messages

/**
 * Enum for all our user-facing messages.
 *
 * This keep things organized for if/when we want to localize,
 * and also facilitates easy/robust testing against [Message] types (rather than for instance brittle string
 * matches on error message content)
 */
enum class Message {
    EMBED_BLOCK_DANGLING_TICK {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            return "Dangling backtick.  Did you mean \"```\"?"
        }
    },
    EMBED_BLOCK_DANGLING_DOUBLETICK {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            return "Dangling backticks.  Did you mean \"```\"?"
        }
    },
    EMBED_BLOCK_BAD_START {
        override fun expectedArgs(): List<String> {
            return listOf("Embed Tag Name")
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            val embedTag = parsedArgs["Embed Tag Name"]
            return "This Embedded Block's content must start on the line after the opening '```${embedTag ?: ""}'"
        }
    },
    EMBED_BLOCK_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            return "Unclosed \"```\""
        }
    },
    UNEXPECTED_CHAR {
        override fun expectedArgs(): List<String> {
            return listOf("Unexpected Character")
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            val unexpectedCharacter = parsedArgs["Unexpected Character"]
            return "Unexpected character: $unexpectedCharacter"
        }
    },
    EOF_NOT_REACHED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            return "Unexpected trailing content.  The previous content parsed as a complete Kson document."
        }
    },
    LIST_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            return "Unclosed list"
        }
    },
    OBJECT_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            return "Unclosed object"
        }
    },
    STRING_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            return "Unterminated string"
        }
    },
    DANGLING_EXP_INDICATOR {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: Map<String, String?>): String {
            return "Dangling exponent indicator"
        }
    };

    /**
     * The list of arguments this [Message] expects, in the order they are expected to be
     * passed to [format]
     */
    protected abstract fun expectedArgs(): List<String>

    /**
     * Members must implement this to format themselves as [String]s, given arguments [parsedArgs].
     *
     * [parsedArgs] maps the arg names given by [expectedArgs] to the arg values passed to [format]
     */
    protected abstract fun doFormat(parsedArgs: Map<String, String?>): String

    /**
     * Format this [Message] for the given [args].
     *
     * Note: expected [args] are described by the list defined in [expectedArgs, and an arg for each [expectedArgs]
     * must be provided, even if it is `null`
     */
    fun format(vararg args: String?): String {
        val numExpectedArgs = expectedArgs().size
        if (args.size != numExpectedArgs) {
            throw RuntimeException(
                "This Message requires $numExpectedArgs arguments: ${expectedArgs().joinToString(", ")},\n" +
                        "but got ${args.size} args: ${args.joinToString(",")}"
            )
        }

        val parsedArgs = expectedArgs().zip(args).toMap()
        return doFormat(parsedArgs)
    }
}


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
    EMBED_BLOCK_NO_CLOSE {
        override fun expectedArgs(): List<String> {
            return listOf("Embed delimiter")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val embedDelimiter = parsedArgs.getArg("Embed delimiter")
            return "Unclosed \"$embedDelimiter\""
        }
    },
    EMBED_BLOCK_NO_NEWLINE {
        override fun expectedArgs(): List<String> {
            return listOf("Embed delimiter", "Embed tag")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val embedDelimiter = parsedArgs.getArg("Embed delimiter")
            val embedTag = parsedArgs.getArg("Embed tag")
            return "Embedded content starts on the first line after the \"$embedDelimiter<embed tag>\" " +
                    "construct, so this \"$embedDelimiter\" cannot be on be on the same line as the opening " +
                    "\"$embedDelimiter$embedTag\""
        }
    },
    EOF_NOT_REACHED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unexpected trailing content. The previous content parsed as a complete Kson document."
        }
    },
    ONLY_UNEXPECTED_CONTENT {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Unexpected content. No content can be parsed as a Kson document."
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
    OBJECT_KEYWORD_RESERVED_WORD {
        override fun expectedArgs(): List<String> {
            return listOf("Reserved Word")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val reservedWord = parsedArgs.getArg("Reserved Word")
            return "`$reservedWord` cannot be used as an object key"
        }
    },
    IGNORED_OBJECT_END_DOT {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "This end-dot is ignored because this object is `{}`-delimited. " +
                    "End-dots only effect non-delimited objects"
        }
    },
    IGNORED_DASH_LIST_END_DASH {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "This end-dash is ignored because this list is `<>`-delimited. " +
                    "End-dashes only effect non-delimited dashed lists"
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
    },
    INTEGER_OVERFLOW {
        override fun expectedArgs(): List<String> {
            return listOf("Overflow Number")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val overflowNumber = parsedArgs.getArg("Overflow Number")
            return "The integer \"$overflowNumber\" is too large and cannot be represented."
        }
    },
    SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Additional items are not allowed"
        }
    },
    SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Additional properties are not allowed"
        }
    },
    SCHEMA_ALL_OF_VALIDATION_FAILED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value must match all of the specified schemas"
        }
    },
    SCHEMA_ANY_OF_VALIDATION_FAILED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value must match at least one of the specified schemas"
        }
    },
    SCHEMA_ARRAY_REQUIRED {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be an array"
        }
    },
    SCHEMA_BOOLEAN_REQUIRED {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be true or false"
        }
    },
    SCHEMA_CONTAINS_VALIDATION_FAILED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Array must contain at least one item that matches the contains schema"
        }
    },
    SCHEMA_DEPENDENCIES_ARRAY_STRING_REQUIRED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Property names in a \"dependencies\" list must be strings"
        }
    },
    SCHEMA_EMPTY_SCHEMA {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema must not be empty"
        }
    },
    SCHEMA_ENUM_VALUE_NOT_ALLOWED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value is not one of the allowed enum values"
        }
    },
    SCHEMA_FALSE_SCHEMA_ERROR {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema always fails"
        }
    },
    SCHEMA_INTEGER_REQUIRED {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be an integer"
        }
    },
    SCHEMA_NOT_VALIDATION_FAILED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value must not match the specified schema"
        }
    },
    SCHEMA_NUMBER_REQUIRED {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be a number"
        }
    },
    SCHEMA_OBJECT_OR_BOOLEAN {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema must be an object or boolean"
        }
    },
    SCHEMA_OBJECT_REQUIRED {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be a number"
        }
    },
    SCHEMA_ONE_OF_VALIDATION_FAILED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value must match exactly one of the specified schemas"
        }
    },
    SCHEMA_REQUIRED_PROPERTY_MISSING {
        override fun expectedArgs(): List<String> {
            return listOf("Missing Properties")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val missingProperties = parsedArgs.getArg("Missing Properties")
            return "Missing required properties: $missingProperties"
        }
    },
    SCHEMA_STRING_ARRAY_ENTRY_ERROR {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema \"$schemaPropertyName\" array entries must be a strings"
        }
    },
    SCHEMA_STRING_REQUIRED {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be a string"
        }
    },
    SCHEMA_TYPE_ARRAY_ENTRY_ERROR {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema \"type\" array entries must be a strings"
        }
    },
    SCHEMA_TYPE_TYPE_ERROR {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema \"type\" must be a string or array of strings"
        }
    },
    SCHEMA_VALUE_MUST_BE_MULTIPLE_OF {
        override fun expectedArgs(): List<String> {
            return listOf("Multiple Of Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val multipleOfValue = parsedArgs.getArg("Multiple Of Value")
            return "Value must be multiple of $multipleOfValue"
        }
    },
    SCHEMA_STRING_LENGTH_TOO_SHORT {
        override fun expectedArgs(): List<String> {
            return listOf("Minimum Length")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val minLength = parsedArgs.getArg("Minimum Length")
            return "String length must be >= $minLength"
        }
    },
    SCHEMA_STRING_LENGTH_TOO_LONG {
        override fun expectedArgs(): List<String> {
            return listOf("Maximum Length")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maxLength = parsedArgs.getArg("Maximum Length")
            return "String length must be <= $maxLength"
        }
    },
    SCHEMA_VALUE_TYPE_MISMATCH {
        override fun expectedArgs(): List<String> {
            return listOf("Expected Types", "Actual Type")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val expectedTypes = parsedArgs.getArg("Expected Types")
            val actualType = parsedArgs.getArg("Actual Type")
            return "Expected one of: $expectedTypes, but got: $actualType"
        }
    },
    SCHEMA_ARRAY_ITEMS_NOT_UNIQUE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Items in this array must be unique"
        }
    },
    SCHEMA_VALUE_TOO_LARGE {
        override fun expectedArgs(): List<String> {
            return listOf("Maximum Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maximum = parsedArgs.getArg("Maximum Value")
            return "Value must be <= $maximum"
        }
    },
    SCHEMA_VALUE_TOO_LARGE_EXCLUSIVE {
        override fun expectedArgs(): List<String> {
            return listOf("Exclusive Maximum Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val exclusiveMaximum = parsedArgs.getArg("Exclusive Maximum Value")
            return "Value must be < $exclusiveMaximum"
        }
    },
    SCHEMA_VALUE_TOO_SMALL {
        override fun expectedArgs(): List<String> {
            return listOf("Minimum Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val minimum = parsedArgs.getArg("Minimum Value")
            return "Value must be >= $minimum"
        }
    },
    SCHEMA_VALUE_TOO_SMALL_EXCLUSIVE {
        override fun expectedArgs(): List<String> {
            return listOf("Exclusive Minimum Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val exclusiveMinimum = parsedArgs.getArg("Exclusive Minimum Value")
            return "Value must be > $exclusiveMinimum"
        }
    },
    SCHEMA_VALUE_NOT_EQUAL_TO_CONST {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value must be exactly equal to const value"
        }
    },
    SCHEMA_ARRAY_TOO_LONG {
        override fun expectedArgs(): List<String> {
            return listOf("Maximum Items")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maxItems = parsedArgs.getArg("Maximum Items")
            return "Array length must be <= $maxItems"
        }
    },
    SCHEMA_ARRAY_TOO_SHORT {
        override fun expectedArgs(): List<String> {
            return listOf("Minimum Items")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val minItems = parsedArgs.getArg("Minimum Items")
            return "Array length must be >= $minItems"
        }
    },
    SCHEMA_OBJECT_TOO_MANY_PROPERTIES {
        override fun expectedArgs(): List<String> {
            return listOf("Maximum Properties")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maxProperties = parsedArgs.getArg("Maximum Properties")
            return "Object must have <= $maxProperties properties"
        }
    },
    SCHEMA_OBJECT_TOO_FEW_PROPERTIES {
        override fun expectedArgs(): List<String> {
            return listOf("Minimum Properties")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val minProperties = parsedArgs.getArg("Minimum Properties")
            return "Object must have >= $minProperties properties"
        }
    },
    SCHEMA_STRING_PATTERN_MISMATCH {
        override fun expectedArgs(): List<String> {
            return listOf("Pattern")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val pattern = parsedArgs.getArg("Pattern")
            return "String must match pattern: $pattern"
        }
    },
    SCHEMA_MISSING_REQUIRED_DEPENDENCIES {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Missing required dependencies"
        }
    },
    OBJECT_PROPERTIES_MISALIGNED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Deceptive indentation. This property's indentation must properly reflect its nesting. " +
                    "Reformat or fix nesting with end-dots `.` or delimiters `{}`"
        }
    },
    DASH_LIST_ITEMS_MISALIGNED {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Deceptive indentation. This list item's indentation must properly reflect its nesting. " +
                    "Reformat or fix nesting with end-dashes `=` or delimiters `<>`"
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
        // zip the given args up with corresponding argName for lookups
        private val parsedArgs: Map<String, String> = messageType.expectedArgs().zip(args).toMap()

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

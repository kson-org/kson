package org.kson.parser.messages

/**
 * Severity levels for messages
 */
enum class MessageSeverity {
    ERROR,
    WARNING
}

/**
 * Instances of [Message] are created with [MessageType.create]. [Message]s can be created during Parsing or
 * post-processing. Post-processing messages are created by any of the validators, for example
 * [org.kson.validation.IndentValidator] or [org.kson.schema.JsonSchemaValidator].
 *
 * Core parse messages (created during lexing/parsing) are wrapped in [CoreParseMessage] when they pass through
 * [org.kson.parser.KsonMarker.error]. All other messages from validators and post-processors remain unwrapped.
 */
interface Message {
    val type: MessageType

    /**
     * Ensure [Message] classes implement equals and hashcode so that messages behave well in collections
     * (being able to de-dupe in a Set for instance is important)
     */
    override operator fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    override fun toString(): String

    companion object {
        /**
         * Returns true if this message results in a fatal parse error. If so it can be marked during
         * [org.kson.parser.AstMarker.error], otherwise the message is annotated in
         * [org.kson.jetbrains.parser.KsonValidationAnnotator.apply].
         */
        fun isFatalParseError(message: Message): Boolean =
            message is CoreParseMessage && message.type.severity == MessageSeverity.ERROR
    }
}

/**
 * A [Message] wrapper that delegates all behavior to the underlying message
 * but marks it as having been created during core parsing (lexing/parsing phase).
 *
 * This is only instantiated when messages pass through the parser,
 * indicating they were created before or during the parsing phase.
 */
class CoreParseMessage(private val delegate: Message) : Message by delegate


/**
 * Enum for all our user-facing messages.
 *
 * This keep things organized for if/when we want to localize,
 * and also facilitates easy/robust testing against [MessageType] types (rather than for instance brittle string
 * matches on error message content)
 */
enum class MessageType(
    /**
     * The severity of this message type
     */
    val severity: MessageSeverity = MessageSeverity.ERROR
) {
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
                throw IllegalArgumentException("Expected arg to be a single control character")
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
    DANGLING_LIST_DASH(MessageSeverity.ERROR) {
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
    SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Additional items are not allowed"
        }
    },
    SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Additional properties are not allowed"
        }
    },
    SCHEMA_ANY_OF_VALIDATION_FAILED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value must match at least one sub-schema"
        }
    },
    SCHEMA_SUB_SCHEMA_ERRORS(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Sub-schema error summary")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val subSchemaErrors = parsedArgs.getArg("Sub-schema error summary")
            return "All sub-schemas reported validation errors. $subSchemaErrors"
        }
    },
    SCHEMA_ARRAY_REQUIRED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be an array"
        }
    },
    SCHEMA_BOOLEAN_REQUIRED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be true or false"
        }
    },
    SCHEMA_CONTAINS_VALIDATION_FAILED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Array must contain at least one item that matches the contains schema"
        }
    },
    SCHEMA_DEPENDENCIES_ARRAY_STRING_REQUIRED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Property names in a \"dependencies\" list must be strings"
        }
    },
    SCHEMA_EMPTY_SCHEMA(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema must not be empty"
        }
    },
    SCHEMA_ENUM_VALUE_NOT_ALLOWED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value is not one of the allowed enum values"
        }
    },
    SCHEMA_FALSE_SCHEMA_ERROR(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema always fails"
        }
    },
    SCHEMA_INTEGER_REQUIRED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be an integer"
        }
    },
    SCHEMA_NOT_VALIDATION_FAILED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value must not match the specified schema"
        }
    },
    SCHEMA_NUMBER_REQUIRED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be a number"
        }
    },
    SCHEMA_OBJECT_OR_BOOLEAN(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema must be an object or boolean"
        }
    },
    SCHEMA_OBJECT_REQUIRED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be an object"
        }
    },
    SCHEMA_ONE_OF_VALIDATION_FAILED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Value must match exactly one of the specified schemas"
        }
    },
    SCHEMA_REQUIRED_PROPERTY_MISSING(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Missing Properties")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val missingProperties = parsedArgs.getArg("Missing Properties")
            return "Missing required properties: $missingProperties"
        }
    },
    SCHEMA_STRING_ARRAY_ENTRY_ERROR(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema \"$schemaPropertyName\" array entries must be a strings"
        }
    },
    SCHEMA_STRING_REQUIRED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Schema Property Name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val schemaPropertyName = parsedArgs.getArg("Schema Property Name")
            return "Schema property \"$schemaPropertyName\" must be a string"
        }
    },
    SCHEMA_TYPE_ARRAY_ENTRY_ERROR(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema \"type\" array entries must be a strings"
        }
    },
    SCHEMA_TYPE_TYPE_ERROR(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Schema \"type\" must be a string or array of strings"
        }
    },
    SCHEMA_VALUE_MUST_BE_MULTIPLE_OF(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Multiple Of Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val multipleOfValue = parsedArgs.getArg("Multiple Of Value")
            return "Value must be multiple of $multipleOfValue"
        }
    },
    SCHEMA_STRING_LENGTH_TOO_SHORT(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Minimum Length")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val minLength = parsedArgs.getArg("Minimum Length")
            return "String length must be >= $minLength"
        }
    },
    SCHEMA_STRING_LENGTH_TOO_LONG(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Maximum Length")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maxLength = parsedArgs.getArg("Maximum Length")
            return "String length must be <= $maxLength"
        }
    },
    SCHEMA_VALUE_TYPE_MISMATCH(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Expected Types", "Actual Type")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val expectedTypes = parsedArgs.getArg("Expected Types")
            val actualType = parsedArgs.getArg("Actual Type")
            return "Expected one of: $expectedTypes, but got: $actualType"
        }
    },
    SCHEMA_ARRAY_ITEMS_NOT_UNIQUE(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Items in this array must be unique"
        }
    },
    SCHEMA_VALUE_TOO_LARGE(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Maximum Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maximum = parsedArgs.getArg("Maximum Value")
            return "Value must be <= $maximum"
        }
    },
    SCHEMA_VALUE_TOO_LARGE_EXCLUSIVE(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Exclusive Maximum Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val exclusiveMaximum = parsedArgs.getArg("Exclusive Maximum Value")
            return "Value must be < $exclusiveMaximum"
        }
    },
    SCHEMA_VALUE_TOO_SMALL(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Minimum Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val minimum = parsedArgs.getArg("Minimum Value")
            return "Value must be >= $minimum"
        }
    },
    SCHEMA_VALUE_TOO_SMALL_EXCLUSIVE(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Exclusive Minimum Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val exclusiveMinimum = parsedArgs.getArg("Exclusive Minimum Value")
            return "Value must be > $exclusiveMinimum"
        }
    },
    SCHEMA_VALUE_NOT_EQUAL_TO_CONST(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Required Value")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val requiredValue = parsedArgs.getArg("Required Value")
            return "Value must be exactly equal to '$requiredValue'"
        }
    },
    SCHEMA_ARRAY_TOO_LONG(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Maximum Items")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maxItems = parsedArgs.getArg("Maximum Items")
            return "Array length must be <= $maxItems"
        }
    },
    SCHEMA_ARRAY_TOO_SHORT(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Minimum Items")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val minItems = parsedArgs.getArg("Minimum Items")
            return "Array length must be >= $minItems"
        }
    },
    SCHEMA_OBJECT_TOO_MANY_PROPERTIES(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Maximum Properties")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val maxProperties = parsedArgs.getArg("Maximum Properties")
            return "Object must have <= $maxProperties properties"
        }
    },
    SCHEMA_OBJECT_TOO_FEW_PROPERTIES(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Minimum Properties")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val minProperties = parsedArgs.getArg("Minimum Properties")
            return "Object must have >= $minProperties properties"
        }
    },
    SCHEMA_STRING_PATTERN_MISMATCH(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Pattern")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val pattern = parsedArgs.getArg("Pattern")
            return "String must match pattern: $pattern"
        }
    },
    SCHEMA_MISSING_REQUIRED_DEPENDENCIES(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Required by", "Missing property")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val requiredBy = parsedArgs.getArg("Required by")
            val missingProperty = parsedArgs.getArg("Missing property")
            return "Property `$missingProperty' is not provided, but it is required by '$requiredBy'"
        }
    },
    OBJECT_PROPERTIES_MISALIGNED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Deceptive indentation. This property should be aligned with the other leading properties in this object." +
                    "Reformat or fix nesting with end-dots `.` or delimiters `{}`"
        }
    },
    DASH_LIST_ITEMS_MISALIGNED(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Deceptive indentation. This element should be aligned with the other leading elements in this list." +
                    "Reformat or fix nesting with end-dashes `=` or delimiters `<>`"
        }
    },
    OBJECT_PROPERTY_NESTING_ISSUE(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Deceptive indentation. This value should be nested deeper than the object that contains it."
        }
    },
    DASH_LIST_ITEMS_NESTING_ISSUE(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Deceptive indentation. This value should nested deeper than the list that contains it."
        }
    },
    OBJECT_DUPLICATE_KEY(MessageSeverity.WARNING) {
        override fun expectedArgs(): List<String> {
            return listOf("Key name")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val keyName = parsedArgs.getArg("Key name")
            return "Duplicate key \"$keyName\" in object"
        }
    },
    JSON_POINTER_BAD_START {
        override fun expectedArgs(): List<String> {
            return listOf("Bad Start Character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val badStartChar = parsedArgs.getArg("Bad Start Character")
            return "JSON Pointer must start with '/' but found '$badStartChar' at position 0"
        }
    },
    JSON_POINTER_INVALID_CHARACTER {
        override fun expectedArgs(): List<String> {
            return listOf("Invalid Character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val invalidChar = parsedArgs.getArg("Invalid Character")
            return "Invalid character in reference token: '$invalidChar'"
        }
    },
    JSON_POINTER_INVALID_ESCAPE {
        override fun expectedArgs(): List<String> {
            return listOf("Invalid Escape Character")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val invalidEscapeChar = parsedArgs.getArg("Invalid Escape Character")
            return "Invalid escape sequence: '~$invalidEscapeChar'. " +
                    "Valid escape sequences are '~0' for '~' and '~1' for '/'"
        }
    },
    JSON_POINTER_INCOMPLETE_ESCAPE {
        override fun expectedArgs(): List<String> {
            return emptyList()
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            return "Incomplete escape sequence '~' at end of token. Must be '~0' or '~1'. "
        }
    },
    SCHEMA_REF_RESOLUTION_FAILED {
        override fun expectedArgs(): List<String> {
            return listOf("Reference")
        }

        override fun doFormat(parsedArgs: ParsedErrorArgs): String {
            val reference = parsedArgs.getArg("Reference")
            return "Failed to resolve schema reference: $reference"
        }
    };

    /**
     * Create a [Message] instance from this [MessageType].  The [args] expected here are defined in this
     * [MessageType]'s [expectedArgs].
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

        return MessageImpl(this, this.doFormat(ParsedErrorArgs(this, givenArgs)))
    }

    /**
     * Data class for messages ensures that
     */
    data class MessageImpl(override val type: MessageType,
                           private val renderedMessage: String) : Message {
        override fun toString(): String {
            return renderedMessage
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
                throw IllegalArgumentException(
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

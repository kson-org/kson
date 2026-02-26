package org.kson.api

interface KsonService {
    /**
     * Formats Kson source with the specified formatting options.
     *
     * @param kson The Kson source to format
     * @param formatOptions The formatting options to apply
     * @return The formatted Kson source
     */
    fun format(kson: String, formatOptions: FormatOptions = FormatOptions()): String

    /**
     * Converts Kson to Json.
     *
     * @param kson The Kson source to convert
     * @param options Options for the JSON transpilation
     * @return A Result containing either the Json output or error messages
     */
    fun toJson(kson: String, options: TranspileOptions.Json = TranspileOptions.Json()): Result

    /**
     * Converts Kson to Yaml, preserving comments
     *
     * @param kson The Kson source to convert
     * @param options Options for the YAML transpilation
     * @return A Result containing either the Yaml output or error messages
     */
    fun toYaml(kson: String, options: TranspileOptions.Yaml = TranspileOptions.Yaml()): Result

    /**
     * Statically analyze the given Kson and return an [Analysis] object containing any messages generated along with a
     * tokenized version of the source.  Useful for tooling/editor support.
     * @param kson The Kson source to analyze
     * @param filepath Filepath of the document being analyzed
     */
    fun analyze(kson: String, filepath: String? = null): Analysis

    /**
     * Parses a Kson schema definition and returns a validator for that schema.
     *
     * @param schemaKson The Kson source defining a Json Schema
     * @return A SchemaValidator that can validate Kson documents against the schema
     */
    fun parseSchema(schemaKson: String): SchemaResult
}

/**
 * Result of a Kson conversion operation
 */
sealed class Result {
    class Success(val output: String) : Result()
    class Failure(val errors: List<Message>) : Result()
}

/**
 * A [KsonService.parseSchema] result
 */
sealed class SchemaResult {
    class Success(val schemaValidator: SchemaValidatorService) : SchemaResult()
    class Failure(val errors: List<Message>) : SchemaResult()
}

/**
 * A validator that can check if Kson source conforms to a schema.
 */
interface SchemaValidatorService {
    /**
     * Validates the given Kson source against this validator's schema.
     * @param kson The Kson source to validate
     * @param filepath Optional filepath of the document being validated, used by validators to determine which rules to apply
     *
     * @return A list of validation error messages, or empty list if valid
     */
    fun validate(kson: String, filepath: String? = null): List<Message>
}

/**
 * Represents a message logged during Kson processing
 */
class Message(val message: String, val severity: MessageSeverity, val start: Position, val end: Position)

/**
 * Represents the severity of a [Message]
 */
enum class MessageSeverity {
    ERROR,
    WARNING,
}

/**
 * A zero-based line/column position in a document
 *
 * @param line The line number where the error occurred (0-based)
 * @param column The column number where the error occurred (0-based)
 */
class Position(val line: Int, val column: Int)

class SourceContext(val filepath: String?)

/**
 * The result of statically analyzing a Kson document
 */
class Analysis(
    val errors: List<Message>,
    val tokens: List<Token>,
    val ksonValue: KsonValue?
)

/**
 * [Token] produced by the lexing phase of a Kson parse
 */
class Token(
    val tokenType: TokenType,
    val text: String,
    val start: Position,
    val end: Position)

enum class TokenType {
    CURLY_BRACE_L,
    CURLY_BRACE_R,
    SQUARE_BRACKET_L,
    SQUARE_BRACKET_R,
    ANGLE_BRACKET_L,
    ANGLE_BRACKET_R,
    COLON,
    DOT,
    END_DASH,
    COMMA,
    COMMENT,
    EMBED_OPEN_DELIM,
    EMBED_CLOSE_DELIM,
    EMBED_TAG,
    EMBED_PREAMBLE_NEWLINE,
    EMBED_CONTENT,
    FALSE,
    UNQUOTED_STRING,
    ILLEGAL_CHAR,
    LIST_DASH,
    NULL,
    NUMBER,
    STRING_OPEN_QUOTE,
    STRING_CLOSE_QUOTE,
    STRING_CONTENT,
    TRUE,
    WHITESPACE,
    EOF
}

/**
 * Type discriminator for KsonValue subclasses
 */
enum class KsonValueType {
    OBJECT,
    ARRAY,
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    NULL,
    EMBED
}

/**
 * Represents a parsed KSON document
 */
sealed class KsonValue(val start: Position, val end: Position) {
    /**
     * Type discriminator for easier type checking in TypeScript/JavaScript
     */
    abstract val type: KsonValueType
    /**
     * A Kson object with key-value pairs
     */
    data class KsonObject(
        val properties: Map<String, KsonValue>,
        val propertyKeys: Map<String, KsonString>,
        private val internalStart: Position,
        private val internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.OBJECT
    }

    /**
     * A Kson array with elements
     */
    class KsonArray(
        val elements: List<KsonValue>,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.ARRAY
    }

    /**
     * A Kson string value
     */
    class KsonString(
        val value: String,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.STRING
    }

    /**
     * A Kson number value.
     */
    sealed class KsonNumber(start: Position, end: Position) : KsonValue(start, end) {

        class Integer(
            val value: Int,
            val internalStart: Position,
            val internalEnd: Position
        ) : KsonNumber(internalStart, internalEnd){
            override val type = KsonValueType.INTEGER
        }

        class Decimal(
            val value: Double,
            internalStart: Position,
            internalEnd: Position
        ) : KsonNumber(internalStart, internalEnd) {
            override val type = KsonValueType.DECIMAL
        }
    }


    /**
     * A Kson boolean value
     */
    class KsonBoolean(
        val value: Boolean,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.BOOLEAN
    }

    /**
     * A Kson null value
     */
    class KsonNull(
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.NULL
    }

    /**
     * A Kson embed block
     */
    class KsonEmbed(
        val tag: String?,
        val content: String,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.EMBED
    }
}

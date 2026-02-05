@file:OptIn(kotlin.js.ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.Kson.parseSchema
import org.kson.Kson.publishMessages
import org.kson.parser.*
import org.kson.parser.messages.MessageSeverity as InternalMessageSeverity
import org.kson.schema.JsonSchema
import org.kson.tools.InternalEmbedRule
import org.kson.tools.FormattingStyle as InternalFormattingStyle
import org.kson.tools.IndentType as InternalIndentType
import org.kson.tools.KsonFormatterConfig
import org.kson.value.navigation.json_pointer.ExperimentalJsonPointerGlobLanguage
import org.kson.value.navigation.json_pointer.JsonPointerGlob
import org.kson.parser.TokenType as InternalTokenType
import org.kson.parser.Token as InternalToken
import org.kson.validation.SourceContext
import org.kson.value.KsonValue as InternalKsonValue
import org.kson.value.KsonObject as InternalKsonObject
import org.kson.value.KsonList as InternalKsonList
import org.kson.value.KsonString as InternalKsonString
import org.kson.value.KsonNumber as InternalKsonNumber
import org.kson.value.KsonBoolean as InternalKsonBoolean
import org.kson.value.KsonNull as InternalKsonNull
import org.kson.value.EmbedBlock as InternalEmbedBlock
import kotlin.js.JsExport

/**
 * The [Kson](https://kson.org) language
 */
object Kson {
    /**
     * Formats Kson source with the specified formatting options.
     *
     * @param kson The Kson source to format
     * @param formatOptions The formatting options to apply
     * @return The formatted Kson source
     */
    fun format(kson: String, formatOptions: FormatOptions = FormatOptions()): String {
        return org.kson.tools.format(kson, formatOptions.toInternal())
    }

    /**
     * Converts Kson to Json.
     *
     * @param kson The Kson source to convert
     * @param options Options for the JSON transpilation
     * @return A Result containing either the Json output or error messages
     */
    fun toJson(kson: String, options: TranspileOptions.Json = TranspileOptions.Json()): Result {
        val compileConfig = Json(
            retainEmbedTags = options.retainEmbedTags,
        )
        val jsonParseResult = KsonCore.parseToJson(kson, compileConfig)
        return if (jsonParseResult.hasErrors()) {
            Result.Failure(publishMessages(jsonParseResult.messages))
        } else {
            Result.Success(jsonParseResult.json!!)
        }
    }

    /**
     * Converts Kson to Yaml, preserving comments
     *
     * @param kson The Kson source to convert
     * @param options Options for the YAML transpilation
     * @return A Result containing either the Yaml output or error messages
     */
    fun toYaml(kson: String, options: TranspileOptions.Yaml = TranspileOptions.Yaml()): Result {
        val compileConfig = CompileTarget.Yaml(
            retainEmbedTags = options.retainEmbedTags,
        )
        val yamlParseResult = KsonCore.parseToYaml(kson, compileConfig)
        return if (yamlParseResult.hasErrors()) {
            Result.Failure(publishMessages(yamlParseResult.messages))
        } else {
            Result.Success(yamlParseResult.yaml!!)
        }
    }

    /**
     * Statically analyze the given Kson and return an [Analysis] object containing any messages generated along with a
     * tokenized version of the source.  Useful for tooling/editor support.
     * @param kson The Kson source to analyze
     * @param filepath Filepath of the document being analyzed
     */
    fun analyze(kson: String, filepath: String? = null) : Analysis {
        val parseResult = KsonCore.parseToAst(
            kson,
            CoreCompileConfig(sourceContext = SourceContext(filepath))
        )
        val tokens = convertTokens(parseResult.lexedTokens)
        val messages = publishMessages(parseResult.messages)
        val value = parseResult.ksonValue?.let { convertValue(it) }
        return Analysis(messages, tokens, value)
    }

    /**
     * Parses a Kson schema definition and returns a validator for that schema.
     *
     * @param schemaKson The Kson source defining a Json Schema
     * @return A SchemaValidator that can validate Kson documents against the schema
     */
    fun parseSchema(schemaKson: String): SchemaResult {
        val schemaParseResult = KsonCore.parseSchema(schemaKson)
        val messages = publishMessages(schemaParseResult.messages)
        val jsonSchema = schemaParseResult.jsonSchema
            ?: return SchemaResult.Failure(messages)

        if (messages.isNotEmpty()) {
            return SchemaResult.Failure(messages)
        }

        return SchemaResult.Success(SchemaValidator(jsonSchema))
    }

    /**
     * "Publish" our internal [LoggedMessage]s to list of public-facing [Message] objects
     */
    internal fun publishMessages(loggedMessages: List<LoggedMessage>): List<Message> {
        return loggedMessages.map {
            val severity = when(it.message.type.severity) {
                InternalMessageSeverity.ERROR -> MessageSeverity.ERROR
                InternalMessageSeverity.WARNING -> MessageSeverity.WARNING
            }

            Message(
                message = it.message.toString(),
                severity = severity,
                start = Position(it.location.start),
                end = Position(it.location.end)
            )
        }
    }
}


/**
 * Result of a Kson conversion operation
 */
sealed class Result {
    class Success(val output: String) : Result()
    class Failure(val errors: List<Message>) : Result()
}

/**
 * A [parseSchema] result
 */
sealed class SchemaResult {
    class Success(val schemaValidator: SchemaValidator) : SchemaResult()
    class Failure(val errors: List<Message>) : SchemaResult()
}

/**
 * A validator that can check if Kson source conforms to a schema.
 */
class SchemaValidator internal constructor(private val schema: JsonSchema) {
    /**
     * Validates the given Kson source against this validator's schema.
     * @param kson The Kson source to validate
     * @param filepath Optional filepath of the document being validated, used by validators to determine which rules to apply
     *
     * @return A list of validation error messages, or empty list if valid
     */
    fun validate(kson: String, filepath: String? = null): List<Message> {
        val astParseResult = KsonCore.parseToAst(
            kson,
            CoreCompileConfig(sourceContext = SourceContext(filepath))
        )
        if (astParseResult.hasErrors()) {
            return publishMessages(astParseResult.messages)
        }

        val messageSink = MessageSink()
        val ksonValue = astParseResult.ksonValue
        if (ksonValue != null) {
            schema.validate(ksonValue, messageSink, SourceContext(filepath))
        }

        return publishMessages(messageSink.loggedMessages())
    }
}

/**
 * A rule for formatting string values at specific paths as embed blocks.
 *
 * When formatting KSON, strings at paths matching [pathPattern] will be rendered
 * as embed blocks instead of regular strings.
 *
 * **Warning:** JsonPointerGlob syntax is experimental and may change in future versions.
 *
 * @param pathPattern A JsonPointerGlob pattern (e.g., "/scripts/ *", "/queries/ **")
 * @param tag Optional embed tag to include (e.g., "yaml", "sql", "bash")
 * @throws IllegalArgumentException if [pathPattern] is not a valid JsonPointerGlob
 *
 * Example:
 * ```kotlin
 * EmbedRule("/scripts/ *", tag = "bash")  // Match all values under "scripts"
 * EmbedRule("/config/description")       // Match exact path, no tag
 * ```
 */
class EmbedRule(
    val pathPattern: String,
    val tag: String? = null
) {
    @OptIn(ExperimentalJsonPointerGlobLanguage::class)
    internal val parsedPathPattern: JsonPointerGlob = JsonPointerGlob(pathPattern)
}

/**
 * Options for formatting Kson output.
 *
 * @param indentType The type of indentation to use (spaces or tabs)
 * @param formattingStyle The formatting style (PLAIN, DELIMITED, COMPACT, CLASSIC)
 * @param embedBlockRules Rules for formatting specific paths as embed blocks
 */
class FormatOptions(
    val indentType: IndentType = IndentType.Spaces(2),
    val formattingStyle: FormattingStyle = FormattingStyle.PLAIN,
    val embedBlockRules: List<EmbedRule> = emptyList()
) {
    /**
     * Map [FormatOptions] to [KsonFormatterConfig] that is used internally to format a Kson document.
     */
    internal fun toInternal(): KsonFormatterConfig {
        val indentType = when (indentType) {
            is IndentType.Spaces -> InternalIndentType.Space(indentType.size)
            is IndentType.Tabs -> InternalIndentType.Tab()
        }

        val formattingStyle = when (formattingStyle){
            FormattingStyle.PLAIN -> InternalFormattingStyle.PLAIN
            FormattingStyle.DELIMITED -> InternalFormattingStyle.DELIMITED
            FormattingStyle.COMPACT -> InternalFormattingStyle.COMPACT
            FormattingStyle.CLASSIC -> InternalFormattingStyle.CLASSIC
        }

        val internalEmbedRules = embedBlockRules.map { rule ->
            InternalEmbedRule(
                pathPattern = rule.parsedPathPattern,
                tag = rule.tag
            )
        }

        return KsonFormatterConfig(
            indentType = indentType,
            formattingStyle = formattingStyle,
            embedBlockRules = internalEmbedRules
        )
    }
}

/**
 * Core interface for transpilation options shared across all output formats.
 */
sealed class TranspileOptions {
    abstract val retainEmbedTags: Boolean

    /**
     * Options for transpiling Kson to JSON.
     */
    class Json(
        override val retainEmbedTags: Boolean = true
    ) : TranspileOptions()

    /**
     * Options for transpiling Kson to YAML.
     */
    class Yaml(
        override val retainEmbedTags: Boolean = true
    ) : TranspileOptions()
}

/**
 * [FormattingStyle] options for Kson Output
 */
enum class FormattingStyle{
    /**
     * These values map to [InternalFormattingStyle]
     */
    PLAIN,
    DELIMITED,
    COMPACT,
    CLASSIC
}

/**
 * Options for indenting Kson Output
 */
sealed class IndentType {
    /** Use spaces for indentation with the specified count */
    class Spaces(val size: Int = 2) : IndentType()

    /** Use tabs for indentation */
    data object Tabs : IndentType()
}

/**
 * The result of statically analyzing a Kson document
 */
class Analysis internal constructor(
    val errors: List<Message>,
    val tokens: List<Token>,
    val ksonValue: KsonValue?
)

/**
 * [Token] produced by the lexing phase of a Kson parse
 */
class Token internal constructor(
    val tokenType: TokenType,
    val text: String,
    val start: Position,
    val end: Position)

enum class TokenType {
    /**
     * See [convertTokens] for the mapping from our [org.kson.parser.TokenType]/[InternalTokenType] tokens
     */
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
 * Represents a message logged during Kson processing
 */
class Message internal constructor(val message: String, val severity: MessageSeverity, val start: Position, val end: Position)

/**
 * Represents the severity of a [Message]
 */
enum class MessageSeverity{
    ERROR,
    WARNING,
}

/**
 * A zero-based line/column position in a document
 *
 * @param line The line number where the error occurred (0-based)
 * @param column The column number where the error occurred (0-based)
 */
class Position internal constructor(val line: Int, val column: Int) {
    internal constructor(coordinates: Coordinates) : this(coordinates.line, coordinates.column)
}

/**
 * Convert a list of internal tokens to public tokens
 */
private fun convertTokens(internalTokens: List<InternalToken>): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0

    while (i < internalTokens.size) {
        val currentToken = internalTokens[i]

        /**
         * Map internal tokens to external representations.  This positions us to refactor internals underneath this.
         * The mapping may appear to be 1-to-1, but it has not always been and this setup allowed us to refactor in
         * a fully backwards compatible manner
         */
        when (currentToken.tokenType) {
            InternalTokenType.STRING_OPEN_QUOTE -> {
                tokens.add(createPublicToken(TokenType.STRING_OPEN_QUOTE, currentToken))
            }
            InternalTokenType.STRING_CONTENT -> {
                tokens.add(createPublicToken(TokenType.STRING_CONTENT, currentToken))
            }
            InternalTokenType.STRING_CLOSE_QUOTE -> {
                tokens.add(createPublicToken(TokenType.STRING_CLOSE_QUOTE, currentToken))
            }
            InternalTokenType.CURLY_BRACE_L -> {
                tokens.add(createPublicToken(TokenType.CURLY_BRACE_L, currentToken))
            }
            InternalTokenType.CURLY_BRACE_R -> {
                tokens.add(createPublicToken(TokenType.CURLY_BRACE_R, currentToken))
            }
            InternalTokenType.SQUARE_BRACKET_L -> {
                tokens.add(createPublicToken(TokenType.SQUARE_BRACKET_L, currentToken))
            }
            InternalTokenType.SQUARE_BRACKET_R -> {
                tokens.add(createPublicToken(TokenType.SQUARE_BRACKET_R, currentToken))
            }
            InternalTokenType.ANGLE_BRACKET_L -> {
                tokens.add(createPublicToken(TokenType.ANGLE_BRACKET_L, currentToken))
            }
            InternalTokenType.ANGLE_BRACKET_R -> {
                tokens.add(createPublicToken(TokenType.ANGLE_BRACKET_R, currentToken))
            }
            InternalTokenType.COLON -> {
                tokens.add(createPublicToken(TokenType.COLON, currentToken))
            }
            InternalTokenType.DOT -> {
                tokens.add(createPublicToken(TokenType.DOT, currentToken))
            }
            InternalTokenType.END_DASH -> {
                tokens.add(createPublicToken(TokenType.END_DASH, currentToken))
            }
            InternalTokenType.COMMA -> {
                tokens.add(createPublicToken(TokenType.COMMA, currentToken))
            }
            InternalTokenType.COMMENT -> {
                tokens.add(createPublicToken(TokenType.COMMENT, currentToken))
            }
            InternalTokenType.EMBED_OPEN_DELIM -> {
                tokens.add(createPublicToken(TokenType.EMBED_OPEN_DELIM, currentToken))
            }
            InternalTokenType.EMBED_CLOSE_DELIM -> {
                tokens.add(createPublicToken(TokenType.EMBED_CLOSE_DELIM, currentToken))
            }
            InternalTokenType.EMBED_TAG -> {
                tokens.add(createPublicToken(TokenType.EMBED_TAG, currentToken))
            }
            InternalTokenType.EMBED_PREAMBLE_NEWLINE -> {
                tokens.add(createPublicToken(TokenType.EMBED_PREAMBLE_NEWLINE, currentToken))
            }
            InternalTokenType.EMBED_CONTENT -> {
                tokens.add(createPublicToken(TokenType.EMBED_CONTENT, currentToken))
            }
            InternalTokenType.FALSE -> {
                tokens.add(createPublicToken(TokenType.FALSE, currentToken))
            }
            InternalTokenType.UNQUOTED_STRING -> {
                tokens.add(createPublicToken(TokenType.UNQUOTED_STRING, currentToken))
            }
            InternalTokenType.ILLEGAL_CHAR -> {
                tokens.add(createPublicToken(TokenType.ILLEGAL_CHAR, currentToken))
            }
            InternalTokenType.LIST_DASH -> {
                tokens.add(createPublicToken(TokenType.LIST_DASH, currentToken))
            }
            InternalTokenType.NULL -> {
                tokens.add(createPublicToken(TokenType.NULL, currentToken))
            }
            InternalTokenType.NUMBER -> {
                tokens.add(createPublicToken(TokenType.NUMBER, currentToken))
            }
            InternalTokenType.TRUE -> {
                tokens.add(createPublicToken(TokenType.TRUE, currentToken))
            }
            InternalTokenType.WHITESPACE -> {
                tokens.add(createPublicToken(TokenType.WHITESPACE, currentToken))
            }
            InternalTokenType.EOF -> {
                tokens.add(createPublicToken(TokenType.EOF, currentToken))
            }
        }
        i++
    }

    return tokens
}

/**
 * Helper function to create a public [Token] from an [org.kson.parser.Token]/[InternalToken]
 */
private fun createPublicToken(publicTokenType: TokenType, internalToken: InternalToken): Token {
    return Token(
        publicTokenType,
        internalToken.lexeme.text,
        Position(internalToken.lexeme.location.start),
        Position(internalToken.lexeme.location.end)
    )
}

/**
 * Convert internal KsonValue types to public Value types
 */
internal fun convertValue(ksonValue: InternalKsonValue): KsonValue {
    return when (ksonValue) {
        is InternalKsonObject -> {
            KsonValue.KsonObject(
                properties = ksonValue.propertyMap.map {
                    (convertValue(it.value.propName) as KsonValue.KsonString).value to convertValue(
                        it.value.propValue
                    )
                }.toMap(),
                propertyKeys = ksonValue.propertyMap.map {
                    (convertValue(it.value.propName) as KsonValue.KsonString).value to (convertValue(
                        it.value.propName
                    ) as KsonValue.KsonString)
                }.toMap(),
                internalStart = Position(ksonValue.location.start),
                internalEnd = Position(ksonValue.location.end)
            )
        }
        is InternalKsonList -> {
            KsonValue.KsonArray(
                elements = ksonValue.elements.map { convertValue(it) },
                internalStart = Position(ksonValue.location.start),
                internalEnd = Position(ksonValue.location.end)
            )
        }
        is InternalKsonString -> {
            KsonValue.KsonString(
                value = ksonValue.value,
                internalStart = Position(ksonValue.location.start),
                internalEnd = Position(ksonValue.location.end)
            )
        }
        is InternalKsonNumber -> {
            val isInteger = ksonValue.value is NumberParser.ParsedNumber.Integer
            if (isInteger) {
                KsonValue.KsonNumber.Integer(
                    value = ksonValue.value.asString.toInt(),
                    internalStart = Position(ksonValue.location.start),
                    internalEnd = Position(ksonValue.location.end)
                )
            } else {
                KsonValue.KsonNumber.Decimal(
                    value = ksonValue.value.asString.toDouble(),
                    internalStart = Position(ksonValue.location.start),
                    internalEnd = Position(ksonValue.location.end)
                )
            }
        }
        is InternalKsonBoolean -> {
            KsonValue.KsonBoolean(
                value = ksonValue.value,
                internalStart = Position(ksonValue.location.start),
                internalEnd = Position(ksonValue.location.end)
            )
        }
        is InternalKsonNull -> {
            KsonValue.KsonNull(
                internalStart = Position(ksonValue.location.start),
                internalEnd = Position(ksonValue.location.end)
            )
        }
        is InternalEmbedBlock -> {
            KsonValue.KsonEmbed(
                tag = ksonValue.embedTag?.value,
                content = ksonValue.embedContent.value,
                internalStart = Position(ksonValue.location.start),
                internalEnd = Position(ksonValue.location.end)
            )
        }
    }
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
 * Represents a parsed [InternalKsonValue] in the public API
 */
sealed class KsonValue(val start: Position, val end: Position) {
    /**
     * Type discriminator for easier type checking in TypeScript/JavaScript
     */
    abstract val type: KsonValueType
    /**
     * A Kson object with key-value pairs
     */
    @ConsistentCopyVisibility
    data class KsonObject internal constructor(
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
    class KsonArray internal constructor(
        val elements: List<KsonValue>,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.ARRAY
    }

    /**
     * A Kson string value
     */
    class KsonString internal constructor(
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

          class Integer internal constructor(
              val value: Int,
              val internalStart: Position,
              val internalEnd: Position
          ) : KsonNumber(internalStart, internalEnd){
              override val type = KsonValueType.INTEGER
          }

        class Decimal internal constructor(
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
    class KsonBoolean internal constructor(
        val value: Boolean,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.BOOLEAN
    }

    /**
     * A Kson null value
     */
    class KsonNull internal constructor(
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.NULL
    }

    /**
     * A Kson embed block
     */
    class KsonEmbed internal constructor(
        val tag: String?,
        val content: String,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.EMBED
    }
}

@file:OptIn(kotlin.js.ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.Kson.parseSchema
import org.kson.Kson.publishMessages
import org.kson.api.*
import org.kson.parser.*
import org.kson.parser.messages.MessageSeverity as InternalMessageSeverity
import org.kson.schema.JsonSchema
import org.kson.tools.IndentType as InternalIndentType
import org.kson.tools.FormattingStyle as InternalFormattingStyle
import org.kson.tools.InternalEmbedRule
import org.kson.tools.KsonFormatterConfig
import org.kson.value.navigation.json_pointer.ExperimentalJsonPointerGlobLanguage
import org.kson.value.navigation.json_pointer.JsonPointerGlob
import org.kson.parser.TokenType as InternalTokenType
import org.kson.parser.Token as InternalToken
import org.kson.validation.SourceContext as InternalSourceContext
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
object Kson : KsonService {
    /**
     * Formats Kson source with the specified formatting options.
     *
     * @param kson The Kson source to format
     * @param formatOptions The formatting options to apply
     * @return The formatted Kson source
     */
    override fun format(kson: String, formatOptions: FormatOptions): String {
        return org.kson.tools.format(kson, formatOptions.toInternal())
    }

    /**
     * Converts Kson to Json.
     *
     * @param kson The Kson source to convert
     * @param options Options for the JSON transpilation
     * @return A Result containing either the Json output or error messages
     */
    override fun toJson(kson: String, options: TranspileOptions.Json): Result {
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
    override fun toYaml(kson: String, options: TranspileOptions.Yaml): Result {
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
    override fun analyze(kson: String, filepath: String?) : Analysis {
        val parseResult = KsonCore.parseToAst(
            kson,
            CoreCompileConfig(sourceContext = InternalSourceContext(filepath))
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
    override fun parseSchema(schemaKson: String): SchemaResult {
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
                start = it.location.start.toPosition(),
                end = it.location.end.toPosition()
            )
        }
    }
}

/**
 * A validator that can check if Kson source conforms to a schema.
 */
class SchemaValidator internal constructor(private val schema: JsonSchema) : SchemaValidatorService {
    /**
     * Validates the given Kson source against this validator's schema.
     * @param kson The Kson source to validate
     * @param filepath Optional filepath of the document being validated, used by validators to determine which rules to apply
     *
     * @return A list of validation error messages, or empty list if valid
     */
    override fun validate(kson: String, filepath: String?): List<Message> {
        val astParseResult = KsonCore.parseToAst(
            kson,
            CoreCompileConfig(sourceContext = InternalSourceContext(filepath))
        )
        if (astParseResult.hasErrors()) {
            return publishMessages(astParseResult.messages)
        }

        val messageSink = MessageSink()
        val ksonValue = astParseResult.ksonValue
        if (ksonValue != null) {
            schema.validate(ksonValue, messageSink, InternalSourceContext(filepath))
        }

        return publishMessages(messageSink.loggedMessages())
    }
}

/**
 * Map [FormatOptions] to [org.kson.tools.KsonFormatterConfig] that is used internally to format a Kson document.
 *
 * @throws IllegalArgumentException when one or more embed rules have an invalid [JsonPointerGlob]
 */
@OptIn(ExperimentalJsonPointerGlobLanguage::class)
internal fun FormatOptions.toInternal(): KsonFormatterConfig {
    val indentType = when (indentType) {
        is IndentType.Spaces -> InternalIndentType.Space((indentType as IndentType.Spaces).size)
        is IndentType.Tabs -> InternalIndentType.Tab()
    }

    val formattingStyle = when (formattingStyle){
        FormattingStyle.PLAIN -> InternalFormattingStyle.PLAIN
        FormattingStyle.DELIMITED -> InternalFormattingStyle.DELIMITED
        FormattingStyle.COMPACT -> InternalFormattingStyle.COMPACT
        FormattingStyle.CLASSIC -> InternalFormattingStyle.CLASSIC
    }

    val internalEmbedRules = embedBlockRules.mapNotNull { rule ->
        val pathPattern = try {
            JsonPointerGlob(rule.pathPattern)
        } catch (e: IllegalArgumentException) {
            // Discard invalid `jsonPointerGlob`
            return@mapNotNull null
        }
        InternalEmbedRule(
            pathPattern = pathPattern,
            tag = rule.tag
        )
    }

    return KsonFormatterConfig(
        indentType = indentType,
        formattingStyle = formattingStyle,
        embedBlockRules = internalEmbedRules
    )
}

internal fun Coordinates.toPosition(): Position {
    return Position(line, column)
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
        internalToken.lexeme.location.start.toPosition(),
        internalToken.lexeme.location.end.toPosition()
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
                internalStart = ksonValue.location.start.toPosition(),
                internalEnd = ksonValue.location.end.toPosition()
            )
        }
        is InternalKsonList -> {
            KsonValue.KsonArray(
                elements = ksonValue.elements.map { convertValue(it) },
                internalStart = ksonValue.location.start.toPosition(),
                internalEnd = ksonValue.location.end.toPosition()
            )
        }
        is InternalKsonString -> {
            KsonValue.KsonString(
                value = ksonValue.value,
                internalStart = ksonValue.location.start.toPosition(),
                internalEnd = ksonValue.location.end.toPosition()
            )
        }
        is InternalKsonNumber -> {
            val isInteger = ksonValue.value is NumberParser.ParsedNumber.Integer
            if (isInteger) {
                KsonValue.KsonNumber.Integer(
                    value = ksonValue.value.asString.toInt(),
                    internalStart = ksonValue.location.start.toPosition(),
                    internalEnd = ksonValue.location.end.toPosition()
                )
            } else {
                KsonValue.KsonNumber.Decimal(
                    value = ksonValue.value.asString.toDouble(),
                    internalStart = ksonValue.location.start.toPosition(),
                    internalEnd = ksonValue.location.end.toPosition()
                )
            }
        }
        is InternalKsonBoolean -> {
            KsonValue.KsonBoolean(
                value = ksonValue.value,
                internalStart = ksonValue.location.start.toPosition(),
                internalEnd = ksonValue.location.end.toPosition()
            )
        }
        is InternalKsonNull -> {
            KsonValue.KsonNull(
                internalStart = ksonValue.location.start.toPosition(),
                internalEnd = ksonValue.location.end.toPosition()
            )
        }
        is InternalEmbedBlock -> {
            KsonValue.KsonEmbed(
                tag = ksonValue.embedTag?.value,
                content = ksonValue.embedContent.value,
                internalStart = ksonValue.location.start.toPosition(),
                internalEnd = ksonValue.location.end.toPosition()
            )
        }
    }
}

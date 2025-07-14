@file:OptIn(kotlin.js.ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.Kson.parseSchema
import org.kson.Kson.publishMessages
import org.kson.ast.KsonValue
import org.kson.parser.*
import org.kson.schema.JsonSchema
import org.kson.tools.IndentType
import org.kson.tools.KsonFormatterConfig
import org.kson.parser.TokenType as InternalTokenType
import org.kson.parser.Token as InternalToken
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
    fun format(kson: String, formatOptions: FormatOptions = FormatOptions.DEFAULT): String {
        val indentType = when (formatOptions) {
            is FormatOptions.Spaces -> IndentType.Space(formatOptions.size)
            is FormatOptions.Tabs -> IndentType.Tab()
        }
        
        val config = KsonFormatterConfig(indentType = indentType)
        return org.kson.tools.format(kson, config)
    }

    /**
     * Converts Kson to Json.
     *
     * @param kson The Kson source to convert
     * @return A Result containing either the Json output or error messages
     */
    fun toJson(kson: String): Result {
        val jsonParseResult = KsonCore.parseToJson(kson)
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
     * @return A Result containing either the Yaml output or error messages
     */
    fun toYaml(kson: String): Result {
        val yamlParseResult = KsonCore.parseToYaml(kson)
        return if (yamlParseResult.hasErrors()) {
            Result.Failure(publishMessages(yamlParseResult.messages))
        } else {
            Result.Success(yamlParseResult.yaml!!)
        }
    }

    /**
     * Statically analyze the given Kson and return an [Analysis] object containing any messages generated along with a
     * tokenized version of the source.  Useful for tooling/editor support.
     */
    fun analyze(kson: String) : Analysis {
        val parseResult = KsonCore.parseToAst(kson)
        val tokens = convertTokens(parseResult.lexedTokens)
        val messages = parseResult.messages.map {
            Message(it.message.toString(), Position(it.location.start), Position(it.location.end))
        }
        return Analysis(messages, tokens)
    }

    /**
     * Parses a Kson schema definition and returns a validator for that schema.
     *
     * @param schemaKson The Kson source defining a Json Schema
     * @return A SchemaValidator that can validate Kson documents against the schema
     */
    fun parseSchema(schemaKson: String): SchemaResult {
        val schemaParseResult = KsonCore.parseSchema(schemaKson)
        val jsonSchema = schemaParseResult.jsonSchema
            ?: return SchemaResult.Failure(publishMessages(schemaParseResult.messages))

        return SchemaResult.Success(SchemaValidator(jsonSchema))
    }

    /**
     * "Publish" our internal [LoggedMessage]s to list of public-facing [Message] objects
     */
    internal fun publishMessages(loggedMessages: List<LoggedMessage>): List<Message> {
        return loggedMessages.map {
            Message(
                message = it.message.toString(),
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
    data class Success(val output: String) : Result()
    data class Failure(val errors: List<Message>) : Result()
}

/**
 * A [parseSchema] result
 */
sealed class SchemaResult {
    data class Success(val schemaValidator: SchemaValidator) : SchemaResult()
    data class Failure(val errors: List<Message>) : SchemaResult()
}

/**
 * A validator that can check if Kson source conforms to a schema.
 */
class SchemaValidator(private val schema: JsonSchema) {
    /**
     * Validates the given Kson source against this validator's schema.
     * @param kson The Kson source to validate
     *
     * @return A list of validation error messages, or empty list if valid
     */
    fun validate(kson: String): List<Message> {
        val astParseResult = KsonCore.parseToAst(kson)
        if (astParseResult.hasErrors()) {
            return publishMessages(astParseResult.messages)
        }

        val messageSink = MessageSink()
        val ksonApi = astParseResult.api
        if (ksonApi != null) {
            schema.validate(ksonApi as KsonValue, messageSink)
        }

        return publishMessages(messageSink.loggedMessages())
    }
}

/**
 * Options for formatting Kson output.
 */
sealed class FormatOptions {
    /** Use spaces for indentation with the specified count */
    data class Spaces(val size: Int = 2) : FormatOptions()

    /** Use tabs for indentation */
    data object Tabs : FormatOptions()

    companion object {
        /** Default formatting: 2 spaces */
        val DEFAULT = Spaces(2)
    }
}

/**
 * The result of statically analyzing a Kson document
 */
data class Analysis internal constructor(val errors: List<Message>, val tokens: List<Token>)

/**
 * [Token] produced by the lexing phase of a Kson parse
 */
data class Token internal constructor(
    val tokenType: TokenType,
    val value: String,
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
data class Message internal constructor(val message: String, val start: Position, val end: Position)

/**
 * A 1-based line/column position in a document
 *
 * @param line The line number where the error occurred (1-based)
 * @param column The column number where the error occurred (1-based)
 */
class Position internal constructor(val line: Int, val column: Int) {
    internal constructor(coordinates: Coordinates) : this(coordinates.line + 1, coordinates.column + 1)
}

/**
 * Convert a list of internal tokens to public tokens
 */
private fun convertTokens(internalTokens: List<InternalToken>): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0

    while (i < internalTokens.size) {
        val currentToken = internalTokens[i]

        when (currentToken.tokenType) {
            InternalTokenType.STRING_OPEN_QUOTE -> {
                // we collapse all string content tokens into one for the public API (our internals track more
                // refined string content tokens to produce better errors, but those refined tokens are
                // not needed by outside clients)
                val contentBuilder = StringBuilder()
                var contentStart: Coordinates? = null
                var contentEnd: Coordinates? = null

                while (i++ < internalTokens.size &&
                    internalTokens[i].tokenType != InternalTokenType.STRING_CLOSE_QUOTE) {
                    val contentToken = internalTokens[i]
                    if (contentStart == null) {
                        contentStart = contentToken.lexeme.location.start
                    }
                    contentEnd = contentToken.lexeme.location.end
                    contentBuilder.append(contentToken.value)
                }

                // Add the open quote token
                tokens.add(createPublicToken(TokenType.STRING_OPEN_QUOTE, currentToken))

                // Add consolidated string content if any
                if (contentBuilder.isNotEmpty() && contentStart != null && contentEnd != null) {
                    tokens.add(Token(
                        TokenType.STRING_CONTENT,
                        contentBuilder.toString(),
                        Position(contentStart),
                        Position(contentEnd)
                    ))
                }

                // Add the close quote token if present
                if (i < internalTokens.size) {
                    if (internalTokens[i].tokenType == InternalTokenType.STRING_CLOSE_QUOTE) {
                        val closeQuoteToken = internalTokens[i]
                        tokens.add(createPublicToken(TokenType.STRING_CLOSE_QUOTE, closeQuoteToken))
                    } else {
                        throw IllegalStateException("Bug: a string must end with a closing quote token or EOF")
                    }
                }

            }
            // String content tokens are handled above in STRING_OPEN_QUOTE case
            InternalTokenType.STRING_CONTENT,
            InternalTokenType.STRING_CLOSE_QUOTE,
            InternalTokenType.STRING_ILLEGAL_CONTROL_CHARACTER,
            InternalTokenType.STRING_UNICODE_ESCAPE,
            InternalTokenType.STRING_ESCAPE -> {
                throw IllegalStateException("String content tokens should be handled in STRING_OPEN_QUOTE case")
            }
            // Regular token conversions - direct mapping
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
        internalToken.value,
        Position(internalToken.lexeme.location.start),
        Position(internalToken.lexeme.location.end)
    )
}

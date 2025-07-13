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
        val tokens = parseResult.lexedTokens.map {
            Token(it.tokenType.name,
                it.value,
                Position(it.lexeme.location.start),
                Position(it.lexeme.location.end))
        }
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
    val tokenType: String,
    val value: String,
    val start: Position,
    val end: Position
)

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

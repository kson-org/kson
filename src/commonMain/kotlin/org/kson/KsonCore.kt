package org.kson

import org.kson.CompileTarget.*
import org.kson.CompileTarget.Kson
import org.kson.ast.*
import org.kson.parser.*
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.SCHEMA_EMPTY_SCHEMA
import org.kson.schema.JsonBooleanSchema
import org.kson.schema.JsonSchema
import org.kson.schema.SchemaParser
import org.kson.stdlibx.exceptions.FatalParseException
import org.kson.tools.FormattingStyle
import org.kson.validation.DuplicateKeyValidator
import org.kson.validation.IndentValidator
import org.kson.tools.KsonFormatterConfig
import org.kson.value.KsonValue
import org.kson.value.toKsonValue

/**
 * Top-level entry point for "core" Kson implementation. This is the key interface for all the services provided by
 * Kson.
 */
object KsonCore {
    /**
     * Parse the given Kson [source] to an [AstParseResult]. This is the base parse for all the [CompileTarget]s
     * we support, and may be used as a standalone parse to validate a [Kson] document or obtain a [KsonValue]
     * from [AstParseResult.ksonValue]
     *
     * @param source The Kson source to parse
     * @param coreCompileConfig the [CoreCompileConfig] for this parse
     * @return An [AstParseResult]
     */
    fun parseToAst(source: String, coreCompileConfig: CoreCompileConfig = CoreCompileConfig()): AstParseResult {
        val messageSink = MessageSink()
        val tokens = Lexer(
            source,
            // we tokenize gapFree when we are errorTolerant so that error nodes can reconstruct their whitespace
            gapFree = coreCompileConfig.ignoreErrors
        ).tokenize()

        var initialTokenIndex = 0
        // if our tokens are gapFree we may have an "empty" file with some comments or whitespace in it
        while (initialTokenIndex < tokens.size &&
            (tokens[initialTokenIndex].tokenType == TokenType.WHITESPACE ||
                    tokens[initialTokenIndex].tokenType == TokenType.COMMENT)
        ) {
            initialTokenIndex++
        }
        if (tokens[initialTokenIndex].tokenType == TokenType.EOF) {
            messageSink.error(tokens[0].lexeme.location, MessageType.BLANK_SOURCE.create())
            return AstParseResult(null, tokens, messageSink)
        }

        val builder = KsonBuilder(tokens, coreCompileConfig.ignoreErrors)

        val ast: KsonRoot?
        try {
            Parser(builder, coreCompileConfig.maxNestingLevel).parse()

            /**
             * Construct an [AstNode] tree from the [KsonMarker] made with the `builder`, or if we fail, return an
             * [AstParseResult] immediately.
             */
            ast = builder.buildTree(messageSink)

        } catch (ex: FatalParseException) {
            println("Fatal parsing error: ${ex.message}")
            return AstParseResult(null, tokens, messageSink)
        }

        // If we are not interested in errors we don't have to run extra validations.
        if (!coreCompileConfig.ignoreErrors) {
            IndentValidator().validate(ast, messageSink)
            DuplicateKeyValidator().validate(ast, messageSink)
        }

        val jsonSchema = coreCompileConfig.schemaJson
        if (jsonSchema != NO_SCHEMA && !coreCompileConfig.ignoreErrors && !messageSink.hasErrors()) {
            // validate against our schema, logging any errors to our message sink
            jsonSchema.validate(ast.toKsonValue(), messageSink)
        }
        return AstParseResult(ast, tokens, messageSink)
    }

    /**
     * Parse the give Kson [source] as a Json Schema declaration
     *
     * @param source The Kson source to parse into a Json Schema
     * @return A [SchemaParseResult]
     */
    fun parseSchema(source: String): SchemaParseResult {
        val astParseResult = parseToAst(source)
        val firstToken = astParseResult.lexedTokens[0]
        if (firstToken.tokenType == TokenType.EOF) {
            return SchemaParseResult(
                null,
                listOf(LoggedMessage(firstToken.lexeme.location, SCHEMA_EMPTY_SCHEMA.create()))
            )
        }
        val ksonValue = astParseResult.ksonValue
        if (ksonValue == null || astParseResult.hasErrors()) {
            return SchemaParseResult(null, astParseResult.messages)
        }

        val messageSink = MessageSink()
        val jsonSchema = SchemaParser.parseSchemaRoot(ksonValue, messageSink)
        return SchemaParseResult(jsonSchema, messageSink.loggedMessages())
    }

    /**
     * Parse the given Kson [source] and compile it to Yaml
     *
     * @param source The Kson source to parse
     * @param compileConfig a [CompileTarget.Yaml] object with this compilation's config
     * @return A [YamlParseResult]
     */
    fun parseToYaml(source: String, compileConfig: Yaml = Yaml()): YamlParseResult {
        return YamlParseResult(parseToAst(source, compileConfig.coreConfig), compileConfig)
    }

    /**
     * Parse the given Kson [source] and compile it to Json
     *
     * @param source The Kson source to parse
     * @param compileConfig a [Json] object with this compilation's config
     * @return A [JsonParseResult]
     */
    fun parseToJson(source: String, compileConfig: Json = Json()): JsonParseResult {
        return JsonParseResult(parseToAst(source, compileConfig.coreConfig), compileConfig)
    }

    /**
     * Parse the given Kson [source] and re-compile it out to Kson.  Useful for testing and transformations
     * like re-writing Json into Kson (the Json is itself Kson since Kson is a superset of Json, whereas the
     * compiled Kson output is in more canonical Kson syntax)
     *
     * @param source The Kson source to parse
     * @param compileConfig a [CompileTarget.Kson] object with this compilation's config
     * @return A [KsonParseResult]
     */
    fun parseToKson(source: String, compileConfig: Kson = Kson()): KsonParseResult {
        return KsonParseResult(parseToAst(source, compileConfig.coreConfig), compileConfig)
    }
}

/**
 * The type generated by our [Kson] parser
 */
interface ParseResult {
    /**
     * The parsed AST, or null if the source was invalid kson (in which cases [hasErrors] will be true)
     */
    val ast: KsonRoot?

    /**
     * The tokens lexed from the input source, provided for debug purposes
     */
    val lexedTokens: List<Token>

    /**
     * The user-facing messages logged during this parse
     */
    val messages: List<LoggedMessage>

    /**
     * True if there are [messages] with severity [org.kson.parser.messages.MessageSeverity.ERROR]. Meaning that the
     * input source could not be parsed.
     */
    fun hasErrors(): Boolean
}

/**
 * Core [ParseResult] produced by the [Kson] parser attempting to create a Kson abstract syntax tree ([ast])
 * from some Kson source
 */
data class AstParseResult(
    override val ast: KsonRoot?,
    override val lexedTokens: List<Token>,
    private val messageSink: MessageSink
) : ParseResult {
    override val messages = messageSink.loggedMessages()

    /**
     * A [KsonValue] on the AST constructed here, or null if there were errors trying to parse
     * (consult [messageSink] for information on any errors)
     */
    val ksonValue: KsonValue? by lazy {
        if (ast == null || hasErrors()) {
            null
        } else {
            ast.toKsonValue()
        }
    }

    override fun hasErrors(): Boolean {
        return messageSink.hasErrors()
    }
}

data class SchemaParseResult(
    val jsonSchema: JsonSchema?,
    val messages: List<LoggedMessage>
)


class KsonParseResult(
    private val astParseResult: AstParseResult,
    compileConfig: Kson
) : ParseResult by astParseResult {
    /**
     * The Kson compiled from some Kson source, or null if there were errors trying to parse
     * (consult [astParseResult] for information on any errors)
     */
    val kson: String? = astParseResult.ast?.toSource(
        AstNode.Indent(compileConfig.formatConfig.indentType),
        compileConfig
    )
}

class YamlParseResult(
    private val astParseResult: AstParseResult,
    compileConfig: Yaml
) : ParseResult by astParseResult {
    /**
     * The Yaml compiled from some Kson source, or null if there were errors trying to parse
     * (consult [astParseResult] for information on any errors)
     */
    val yaml: String? = astParseResult.ast?.toSource(AstNode.Indent(), compileConfig)
}

class JsonParseResult(
    private val astParseResult: AstParseResult,
    compileConfig: Json
) : ParseResult by astParseResult {
    /**
     * The Json compiled from some Kson source, or null if there were errors trying to parse
     * (consult [astParseResult] for information on any errors)
     */
    val json: String? = astParseResult.ast?.toSource(AstNode.Indent(), compileConfig)
}


/**
 * Type to denote a supported Kson compilation target and hold the compilation's configuration
 */
sealed class CompileTarget(val coreConfig: CoreCompileConfig) {
    /**
     * Whether this compilation should preserve comments from the input [Kson] source in the compiled output
     */
    abstract val preserveComments: Boolean

    /**
     * Compile target for serializing a Kson AST out to Kson source
     *
     * @param formatConfig the settings for formatting the compiler Kson output
     * @param coreCompileConfig the [CoreCompileConfig] for this compile
     */
    open class Kson(
        override val preserveComments: Boolean = true,
        val formatConfig: KsonFormatterConfig = KsonFormatterConfig(),
        coreCompileConfig: CoreCompileConfig = CoreCompileConfig()
    ) : CompileTarget(coreCompileConfig)

    /**
     * Compile target for Yaml transpilation
     *
     * @param retainEmbedTags If true, embed blocks will be compiled to objects containing both tag and content
     * @param coreCompileConfig the [CoreCompileConfig] for this compile
     */
    class Yaml(
        override val preserveComments: Boolean = true,
        val retainEmbedTags: Boolean = true,
        coreCompileConfig: CoreCompileConfig = CoreCompileConfig()
    ) : CompileTarget(coreCompileConfig)

}

/**
 * Compile target for Json transpilation. Transpiling to JSON is the same as formatting KSON with [FormattingStyle.CLASSIC].
 *
 * @param retainEmbedTags If true, embed blocks will be compiled to objects containing both tag and content
 * @param coreCompileConfig the [CoreCompileConfig] for this compile
 */
class Json(
    val retainEmbedTags: Boolean = true,
    coreCompileConfig: CoreCompileConfig = CoreCompileConfig()
) : Kson(
    formatConfig = KsonFormatterConfig(formattingStyle = FormattingStyle.CLASSIC),
    coreCompileConfig = coreCompileConfig,
    // Json does not support comments
    preserveComments = false
)

/**
 * Configuration applicable to all compile targets
 */
data class CoreCompileConfig(
    /**
     * The [JSON Schema](https://json-schema.org/) to enforce in this compilation
     */
    val schemaJson: JsonSchema = NO_SCHEMA,
    /**
     * Whether we do the extra work to build an AST patched with [AstNodeError]'s. This could be set to false when
     * formatting for example, since we're only interested in collecting the error nodes and running validators.
     */
    val ignoreErrors: Boolean = false,
    /**
     * The deep object/list nesting to allow in the parsed document.  See [DEFAULT_MAX_NESTING_LEVEL] for more details.
     */
    val maxNestingLevel: Int = DEFAULT_MAX_NESTING_LEVEL
)

/**
 * A [JsonBooleanSchema] specifying just `true` is the "trivial" schema that matches everything,
 * and so is equivalent to not having a schema.  See https://json-schema.org/draft/2020-12/json-schema-core#section-4.3.2
 * for more detail
 */
private val NO_SCHEMA = JsonBooleanSchema(true)

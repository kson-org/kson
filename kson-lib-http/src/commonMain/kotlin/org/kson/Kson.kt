package org.kson

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

object Kson {
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }
    private var baseUrl = "http://localhost:8080"

    fun setPort(port: Int) {
        baseUrl = "http://localhost:$port"
    }

    fun format(kson: String, formatOptions: FormatOptions = FormatOptions()): String {
        val request = buildJsonObject {
            put("command", "format")
            put("kson", kson)
            put("formatOptions", formatOptions.toJson())
        }
        val response = post(request)
        return response["output"]!!.jsonPrimitive.content
    }


    fun toJson(kson: String, options: TranspileOptions.Json = TranspileOptions.Json()): Result {
        val request = buildJsonObject {
            put("command", "toJson")
            put("kson", kson)
            put("retainEmbedTags", options.retainEmbedTags)
        }
        return parseTranspileResult(post(request))
    }


    fun toYaml(kson: String, options: TranspileOptions.Yaml = TranspileOptions.Yaml()): Result {
        val request = buildJsonObject {
            put("command", "toYaml")
            put("kson", kson)
            put("retainEmbedTags", options.retainEmbedTags)
        }
        return parseTranspileResult(post(request))
    }


    fun analyze(kson: String, filepath: String? = null): Analysis {
        val request = buildJsonObject {
            put("command", "analyze")
            put("kson", kson)
            filepath?.let { put("filepath", it) }
        }
        val response = post(request)
        val errors = parseMessages(response["errors"]!!.jsonArray)
        val tokens = parseTokens(response["tokens"]!!.jsonArray)
        val ksonValue = response["ksonValue"]?.let {
            if (it is JsonNull) null else parseKsonValue(it.jsonObject)
        }
        return Analysis(errors, tokens, ksonValue)
    }


    fun parseSchema(schemaKson: String): SchemaResult {
        val request = buildJsonObject {
            put("command", "parseSchema")
            put("schemaKson", schemaKson)
        }
        val response = post(request)
        val success = response["success"]!!.jsonPrimitive.boolean
        return if (success) {
            SchemaResult.Success(SchemaValidator(schemaKson))
        } else {
            SchemaResult.Failure(parseMessages(response["errors"]!!.jsonArray))
        }
    }

    internal fun post(body: JsonObject): JsonObject = runBlocking {
        val response = client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private fun parseTranspileResult(response: JsonObject): Result {
        val success = response["success"]!!.jsonPrimitive.boolean
        return if (success) {
            Result.Success(response["output"]!!.jsonPrimitive.content)
        } else {
            Result.Failure(parseMessages(response["errors"]!!.jsonArray))
        }
    }
}


sealed class Result {
    class Success(val output: String) : Result()
    class Failure(val errors: List<Message>) : Result()
}


sealed class SchemaResult {
    class Success(val schemaValidator: SchemaValidator) : SchemaResult()
    class Failure(val errors: List<Message>) : SchemaResult()
}

class SchemaValidator internal constructor(private val schemaKson: String) {

    fun validate(kson: String, filepath: String? = null): List<Message> {
        val request = buildJsonObject {
            put("command", "validate")
            put("schemaKson", schemaKson)
            put("kson", kson)
            filepath?.let { put("filepath", it) }
        }
        val response = Kson.post(request)
        return parseMessages(response["errors"]!!.jsonArray)
    }
}


class EmbedRule private constructor(
    val pathPattern: String,
    val tag: String? = null
) {
    companion object {
        fun fromPathPattern(pathPattern: String, tag: String? = null): EmbedRuleResult {
            val request = buildJsonObject {
                put("command", "validateEmbedRule")
                put("embedBlockRule", buildJsonObject {
                    put("pathPattern", pathPattern)
                    tag?.let { put("tag", it) }
                })
            }
            val response = Kson.post(request)
            val success = response["success"]?.jsonPrimitive?.boolean == true
            return if (success) {
                EmbedRuleResult.Success(EmbedRule(pathPattern, tag))
            } else {
                val error = response["error"]!!.jsonPrimitive.content
                EmbedRuleResult.Failure(error)
            }
        }
    }
}

sealed class EmbedRuleResult {
    data class Success(val embedRule: EmbedRule) : EmbedRuleResult()
    data class Failure(val message: String) : EmbedRuleResult()
}


class FormatOptions(
    val indentType: IndentType = IndentType.Spaces(2),
    val formattingStyle: FormattingStyle = FormattingStyle.PLAIN,
    val embedBlockRules: List<EmbedRule> = emptyList()
) {
    internal fun toJson(): JsonObject = buildJsonObject {
        put("indentType", when (indentType) {
            is IndentType.Spaces -> buildJsonObject {
                put("type", "spaces")
                put("size", indentType.size)
            }
            is IndentType.Tabs -> buildJsonObject {
                put("type", "tabs")
            }
        })
        put("formattingStyle", formattingStyle.name)
        put("embedBlockRules", buildJsonArray {
            for (rule in embedBlockRules) {
                add(buildJsonObject {
                    put("pathPattern", rule.pathPattern)
                    rule.tag?.let { put("tag", it) }
                })
            }
        })
    }
}


sealed class TranspileOptions {
    abstract val retainEmbedTags: Boolean


    class Json(
        override val retainEmbedTags: Boolean = true
    ) : TranspileOptions()


    class Yaml(
        override val retainEmbedTags: Boolean = true
    ) : TranspileOptions()
}


enum class FormattingStyle {
    PLAIN,
    DELIMITED,
    COMPACT,
    CLASSIC
}

sealed class IndentType {

    class Spaces(val size: Int = 2) : IndentType()


    data object Tabs : IndentType()
}


class Analysis internal constructor(
    val errors: List<Message>,
    val tokens: List<Token>,
    val ksonValue: KsonValue?
)


class Token internal constructor(
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


class Message internal constructor(val message: String, val severity: MessageSeverity, val start: Position, val end: Position)


enum class MessageSeverity {
    ERROR,
    WARNING,
}


class Position internal constructor(val line: Int, val column: Int)


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

sealed class KsonValue(val start: Position, val end: Position) {

    abstract val type: KsonValueType


    @ConsistentCopyVisibility
    data class KsonObject internal constructor(
        val properties: Map<String, KsonValue>,
        val propertyKeys: Map<String, KsonString>,
        private val internalStart: Position,
        private val internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.OBJECT
    }


    class KsonArray internal constructor(
        val elements: List<KsonValue>,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.ARRAY
    }


    class KsonString internal constructor(
        val value: String,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.STRING
    }


    sealed class KsonNumber(start: Position, end: Position) : KsonValue(start, end) {

        class Integer internal constructor(
            val value: Int,
            val internalStart: Position,
            val internalEnd: Position
        ) : KsonNumber(internalStart, internalEnd) {
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


    class KsonBoolean internal constructor(
        val value: Boolean,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.BOOLEAN
    }


    class KsonNull internal constructor(
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.NULL
    }


    class KsonEmbed internal constructor(
        val tag: String?,
        val content: String,
        internalStart: Position,
        internalEnd: Position
    ) : KsonValue(internalStart, internalEnd) {
        override val type = KsonValueType.EMBED
    }
}

// --- JSON response parsing helpers ---

internal fun parsePosition(obj: JsonObject): Position {
    return Position(
        line = obj["line"]!!.jsonPrimitive.int,
        column = obj["column"]!!.jsonPrimitive.int
    )
}

internal fun parseMessages(array: JsonArray): List<Message> {
    return array.map { element ->
        val obj = element.jsonObject
        Message(
            message = obj["message"]!!.jsonPrimitive.content,
            severity = MessageSeverity.valueOf(obj["severity"]!!.jsonPrimitive.content),
            start = parsePosition(obj["start"]!!.jsonObject),
            end = parsePosition(obj["end"]!!.jsonObject)
        )
    }
}

internal fun parseTokens(array: JsonArray): List<Token> {
    return array.map { element ->
        val obj = element.jsonObject
        Token(
            tokenType = TokenType.valueOf(obj["tokenType"]!!.jsonPrimitive.content),
            text = obj["text"]!!.jsonPrimitive.content,
            start = parsePosition(obj["start"]!!.jsonObject),
            end = parsePosition(obj["end"]!!.jsonObject)
        )
    }
}

internal fun parseKsonValue(obj: JsonObject): KsonValue {
    val start = parsePosition(obj["start"]!!.jsonObject)
    val end = parsePosition(obj["end"]!!.jsonObject)

    return when (val type = obj["type"]!!.jsonPrimitive.content) {
        "OBJECT" -> {
            val properties = obj["properties"]!!.jsonObject.mapValues { (_, v) -> parseKsonValue(v.jsonObject) }
            val propertyKeys = obj["propertyKeys"]!!.jsonObject.mapValues { (_, v) ->
                val keyObj = v.jsonObject
                KsonValue.KsonString(
                    value = keyObj["value"]!!.jsonPrimitive.content,
                    internalStart = parsePosition(keyObj["start"]!!.jsonObject),
                    internalEnd = parsePosition(keyObj["end"]!!.jsonObject)
                )
            }
            KsonValue.KsonObject(properties, propertyKeys, start, end)
        }
        "ARRAY" -> {
            val elements = obj["elements"]!!.jsonArray.map { parseKsonValue(it.jsonObject) }
            KsonValue.KsonArray(elements, start, end)
        }
        "STRING" -> KsonValue.KsonString(obj["value"]!!.jsonPrimitive.content, start, end)
        "INTEGER" -> KsonValue.KsonNumber.Integer(obj["value"]!!.jsonPrimitive.int, start, end)
        "DECIMAL" -> KsonValue.KsonNumber.Decimal(obj["value"]!!.jsonPrimitive.double, start, end)
        "BOOLEAN" -> KsonValue.KsonBoolean(obj["value"]!!.jsonPrimitive.boolean, start, end)
        "NULL" -> KsonValue.KsonNull(start, end)
        "EMBED" -> KsonValue.KsonEmbed(
            tag = obj["tag"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content },
            content = obj["content"]!!.jsonPrimitive.content,
            internalStart = start,
            internalEnd = end
        )
        else -> throw IllegalArgumentException("Unknown KsonValue type: $type")
    }
}

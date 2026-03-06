package org.kson

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.kson.api.*

object Kson : KsonService {
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }
    private var baseUrl = "http://localhost:8080"

    fun setPort(port: Int) {
        baseUrl = "http://localhost:$port"
    }

    override fun format(kson: String, formatOptions: FormatOptions): String {
        val request = buildJsonObject {
            put("command", "format")
            put("kson", kson)
            put("formatOptions", formatOptions.toJson())
        }
        val response = post(request)
        return response["output"]!!.jsonPrimitive.content
    }


    override fun toJson(kson: String, options: TranspileOptions.Json): Result {
        val request = buildJsonObject {
            put("command", "toJson")
            put("kson", kson)
            put("retainEmbedTags", options.retainEmbedTags)
        }
        return parseTranspileResult(post(request))
    }


    override fun toYaml(kson: String, options: TranspileOptions.Yaml): Result {
        val request = buildJsonObject {
            put("command", "toYaml")
            put("kson", kson)
            put("retainEmbedTags", options.retainEmbedTags)
        }
        return parseTranspileResult(post(request))
    }


    override fun analyze(kson: String, filepath: String?): Analysis {
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

    override fun parseSchema(schemaKson: String): SchemaResult {
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

class SchemaValidator internal constructor(private val schemaKson: String) : SchemaValidatorService {

    override fun validate(kson: String, filepath: String?): List<Message> {
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

internal fun FormatOptions.toJson(): JsonObject = buildJsonObject {
    put("indentType", when (indentType) {
        is IndentType.Spaces -> buildJsonObject {
            put("type", "spaces")
            put("size", (indentType as IndentType.Spaces).size)
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

package org.kson.schema
import org.kson.value.KsonNumber
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.NumberParser
import org.kson.parser.messages.MessageType
import org.kson.schema.validators.TypeValidator

/**
 * Base [JsonSchema] type that [KsonValue]s may be validated against
 */
sealed interface JsonSchema {
  /**
   * A guaranteed non-null description for this schema that may be used in user-facing messages.  Should be defaulted
   * to something reasonable (if not as helpful) when the schema itself provides no description
   */
  fun descriptionWithDefault(): String
  fun validate(ksonValue: KsonValue, messageSink: MessageSink)

  fun isValid(ksonValue: KsonValue, messageSink: MessageSink): Boolean {
    val numErrors = messageSink.loggedMessages().size
    validate(ksonValue, messageSink)
    return messageSink.loggedMessages().size == numErrors
  }
}

/**
 * The main [JsonSchema] object representation
 */
class JsonObjectSchema(
    val title: String?,
    val description: String?,
    val default: KsonValue?,
    val definitions: Map<KsonString, JsonSchema?>?,

    private val typeValidator: TypeValidator?,
    private val schemaValidators: List<JsonSchemaValidator>
) : JsonSchema {

  override fun descriptionWithDefault(): String {
    return description ?: "JSON Object Schema"
  }

  /**
   * Validates a [KsonValue] against this schema, logging any validation errors to the [messageSink]
   */
  override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
    if (typeValidator != null) {
      if (!typeValidator.validate(ksonValue, messageSink)) {
        // we're not the right type for this document, validation cannot continue
        return
      }
    }

    // no `type` violations, run all other validators configured for this schema
    schemaValidators.forEach { validator ->
      validator.validate(ksonValue, messageSink)
    }
  }
}

/**
 * The most basic valid JsonSchema: `true` accepts all Json, `false` accepts none.
 */
class JsonBooleanSchema(val valid: Boolean) : JsonSchema {
  override fun descriptionWithDefault() = if (valid) "This schema accepts all JSON as valid" else "This schema rejects all JSON as invalid"
  override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
    if (valid) {
      return
    } else {
      messageSink.error(ksonValue.location, MessageType.SCHEMA_FALSE_SCHEMA_ERROR.create())
    }
  }
}

/**
 * Converts the given `KsonNumber` to its corresponding integer representation, if applicable.
 *
 * This function checks if the `KsonNumber` represents an integer or a decimal number that can
 * safely be interpreted as an integer according to JSON Schema rules. If the value is a decimal
 * but matches a pattern of all zeros after the decimal point (e.g., "1.0"), it is converted to
 * a long integer. Otherwise, it returns `null`.
 *
 * @param ksonNumber The `KsonNumber` instance to be converted to a long integer
 * @return The integer value of the `KsonNumber` if it represents an integer or a decimal that
 *         can be safely interpreted as an integer, otherwise, returns `null`
 */
fun asSchemaInteger(ksonNumber: KsonNumber): Long? {
  return when (ksonNumber.value) {
    is NumberParser.ParsedNumber.Integer -> ksonNumber.value.value
    is NumberParser.ParsedNumber.Decimal -> {
      if (ksonNumber.value.asString.matches(allZerosDecimalRegex)) {
        // 1.0-type numbers are considered integers by JsonSchema, and it's safe to `toInt` it
        ksonNumber.value.value.toLong()
      } else {
        null
      }
    }
  }
}

// cached regex for testing if all the digits after the decimal are zero in a decimal string
private val allZerosDecimalRegex = Regex(".*\\.0*")

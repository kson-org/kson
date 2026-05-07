package org.kson.schema
import org.kson.value.KsonNumber
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.NumberParser
import org.kson.parser.messages.MessageType
import org.kson.schema.validators.AllOfValidator
import org.kson.schema.validators.AnyOfValidator
import org.kson.schema.validators.OneOfValidator
import org.kson.schema.validators.RefValidator
import org.kson.schema.validators.TypeValidator
import org.kson.validation.SourceContext
import org.kson.validation.Validator

/** Fallback description used when a schema has no title, description, or recognizable structure to describe. */
private const val GENERIC_OBJECT_SCHEMA_DESCRIPTION = "JSON Object Schema"

/**
 * Base [JsonSchema] type that [KsonValue]s may be validated against
 */
sealed interface JsonSchema: Validator {
  /**
   * A guaranteed non-null description for this schema that may be used in user-facing messages.  Should be defaulted
   * to something reasonable (if not as helpful) when the schema provides neither a description nor a title
   */
  fun descriptionWithDefault(): String
  override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext)

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
    val comment: String?,
    val default: KsonValue?,
    val definitions: Map<KsonString, JsonSchema?>?,

    private val typeValidator: TypeValidator?,
    private val schemaValidators: List<JsonSchemaValidator>
) : JsonSchema {

  override fun descriptionWithDefault(): String {
    return description ?: title ?: synthesizeDescription() ?: GENERIC_OBJECT_SCHEMA_DESCRIPTION
  }

  /**
   * Derive a human-friendly description from the schema's structure when no `title` or `description` is
   * declared. Recognizes two common shapes:
   *   - a lone `$ref` whose target can be named (via [RefValidator.refShortName])
   *   - a lone `oneOf` / `anyOf` / `allOf` whose branches can each be named
   *
   * Returns `null` when the structure is too generic to describe meaningfully (e.g. an anonymous object
   * with no combinators, or a combinator with any anonymous branch), leaving the caller to fall
   * back to [GENERIC_OBJECT_SCHEMA_DESCRIPTION].
   */
  private fun synthesizeDescription(): String? {
    val sole = schemaValidators.singleOrNull() ?: return null
    return when (sole) {
      is RefValidator -> sole.refShortName()
      is OneOfValidator -> combinatorDescription("one of", sole.oneOf)
      is AnyOfValidator -> combinatorDescription("any of", sole.anyOf)
      is AllOfValidator -> combinatorDescription("all of", sole.allOf)
      else -> null
    }
  }

  /**
   * Joins each branch's [branchName] under [prefix].  Strict all-or-nothing: returns `null` as soon
   * as any branch is anonymous (no title, no nameable `$ref`), so that we never emit a partial list
   * that invites the reader to assume those are the only allowed shapes.
   */
  private fun combinatorDescription(prefix: String, branches: List<JsonSchema>): String? {
    if (branches.isEmpty()) return null
    val names = branches.map { branchName(it) ?: return null }
    return "$prefix: ${names.joinToString(", ")}"
  }

  /**
   * A short, human-recognizable name for a combinator [branch], or `null` if the branch is anonymous.
   *
   * Preference order:
   *   1. an explicit `title` on the branch
   *   2. [RefValidator.refShortName] for a branch whose sole validator is a `$ref`
   *
   * Returns `null` for anything that doesn't fit this shape, so that [combinatorDescription] can bail
   * to its generic fallback rather than emit a misleading partial list.
   */
  private fun branchName(branch: JsonSchema): String? {
    if (branch !is JsonObjectSchema) return null
    branch.title?.let { return it }
    val sole = branch.schemaValidators.singleOrNull() as? RefValidator ?: return null
    return sole.refShortName()
  }

  /**
   * Validates a [KsonValue] against this schema, logging any validation errors to the [messageSink]
   */
  override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
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
  override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
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

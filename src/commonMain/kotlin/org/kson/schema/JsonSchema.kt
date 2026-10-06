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
import org.kson.schema.validators.PropertiesValidator
import org.kson.schema.validators.RefValidator
import org.kson.schema.validators.RequiredValidator
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

  fun isValid(
    ksonValue: KsonValue,
    messageSink: MessageSink,
    sourceContext: SourceContext = SourceContext()
  ): Boolean {
    val numErrors = messageSink.loggedMessages().size
    validate(ksonValue, messageSink, sourceContext)
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

  /** A description from the structure of a lone `$ref` or combinator, or `null` when too generic to name. */
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

  /** `null` as soon as any branch is anonymous: a partial list would read as the only allowed shapes. */
  private fun combinatorDescription(prefix: String, branches: List<JsonSchema>): String? {
    if (branches.isEmpty()) return null
    val names = branches.map { branchName(it) ?: return null }
    return "$prefix: ${names.joinToString(", ")}"
  }

  private fun branchName(branch: JsonSchema): String? {
    if (branch !is JsonObjectSchema) return null
    branch.title?.let { return it }
    return branch.soleRefValidator()?.refShortName()
  }

  private fun soleRefValidator(): RefValidator? = schemaValidators.singleOrNull() as? RefValidator

  /**
   * This schema plus the target of a lone `$ref` and each `allOf` member, transitively: they all
   * constrain the same document, so the readers below take their declarations as one.  Property
   * schemas are never followed, so a self-referential `child: { $ref: node }` is not a path here.
   */
  private fun compositionSources(): Set<JsonObjectSchema> {
    val sources = mutableSetOf<JsonObjectSchema>()
    val pending = ArrayDeque<JsonObjectSchema>(listOf(this))
    while (pending.isNotEmpty()) {
      val schema = pending.removeFirst()
      if (!sources.add(schema)) continue
      (schema.soleRefValidator()?.resolvedSchema() as? JsonObjectSchema)?.let { pending.add(it) }
      schema.schemaValidators.filterIsInstance<AllOfValidator>()
        .flatMapTo(pending) { it.allOf.filterIsInstance<JsonObjectSchema>() }
    }
    return sources
  }

  /**
   * The properties `const` or `enum` fixes to a value set, across every [compositionSources] schema.
   * A property several of them pin is pinned to the intersection, which may be empty.
   */
  internal fun pinnedProperties(): Map<String, Set<KsonValue>> {
    val pins = mutableMapOf<String, Set<KsonValue>>()
    compositionSources().forEach { source ->
      source.ownPinnedProperties().forEach { (property, values) ->
        pins[property] = pins[property]?.intersect(values) ?: values
      }
    }
    return pins
  }

  /** The properties required or declared across every [compositionSources] schema. */
  internal fun knownProperties(): Set<String> =
    compositionSources().flatMapTo(mutableSetOf()) { source ->
      source.ownRequiredProperties() + source.ownPropertySchemas().keys.map { it.value }
    }

  private fun ownPinnedProperties(): Map<String, Set<KsonValue>> =
    ownPropertySchemas().mapNotNull { (name, propertySchema) ->
      val pinned = (propertySchema as? JsonObjectSchema)?.schemaValidators?.singleOrNull()?.pinnedValues()
      pinned?.let { name.value to it }
    }.toMap()

  private fun ownRequiredProperties(): Set<String> =
    schemaValidators.filterIsInstance<RequiredValidator>()
      .firstOrNull()
      ?.required
      ?.mapTo(mutableSetOf()) { it.value } ?: emptySet()

  private fun ownPropertySchemas(): Map<KsonString, JsonSchema?> =
    schemaValidators.filterIsInstance<PropertiesValidator>()
      .firstOrNull()
      ?.propertySchemas ?: emptyMap()

  override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
    if (typeValidator != null) {
      if (!typeValidator.validate(ksonValue, messageSink)) {
        // the wrong type: nothing else can meaningfully be checked
        return
      }
    }

    schemaValidators.forEach { validator ->
      validator.validate(ksonValue, messageSink, sourceContext)
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

/** The integer [ksonNumber] denotes, or `null`: JSON Schema counts a decimal like `1.0` as an integer. */
fun asSchemaInteger(ksonNumber: KsonNumber): Long? {
  return when (ksonNumber.value) {
    is NumberParser.ParsedNumber.Integer -> ksonNumber.value.value
    is NumberParser.ParsedNumber.Decimal -> {
      if (ksonNumber.value.asString.matches(allZerosDecimalRegex)) {
        ksonNumber.value.value.toLong()
      } else {
        null
      }
    }
  }
}

private val allZerosDecimalRegex = Regex(".*\\.0*")

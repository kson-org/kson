package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType.SCHEMA_ENUM_VALUE_NOT_ALLOWED
import org.kson.parser.messages.MessageType.SCHEMA_ONE_OF_MULTIPLE_MATCHES
import org.kson.parser.messages.MessageType.SCHEMA_ONE_OF_VALIDATION_FAILED
import org.kson.schema.JsonObjectSchema
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext

class OneOfValidator(internal val oneOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        val matchAttemptMessageSinks: MutableList<LabelledMessageSink> = mutableListOf()
        val matchedSchemas: MutableList<JsonSchema> = mutableListOf()

        // Unlike anyOf (which can short-circuit on the first match), oneOf must evaluate all
        // sub-schemas to detect the multiple-match case
        oneOf.forEach {
            val oneOfMessageSink = MessageSink()
            it.validate(ksonValue, oneOfMessageSink)
            matchAttemptMessageSinks.add(LabelledMessageSink(it.descriptionWithDefault(), oneOfMessageSink))
            if (!oneOfMessageSink.hasMessages()) {
                matchedSchemas.add(it)
            }
        }

        when {
            matchedSchemas.size == 1 -> { /* success */ }

            matchedSchemas.isEmpty() -> {
                // Prefer a focused error against the discriminator-selected branch; fall back to
                // dumping every branch's errors only when this isn't a discriminated union.
                if (!reportDiscriminatedUnionError(ksonValue, messageSink, sourceContext)) {
                    reportNoSubSchemaMatchErrors(ksonValue, messageSink, matchAttemptMessageSinks, SCHEMA_ONE_OF_VALIDATION_FAILED.create())
                }
            }

            else -> {
                val matchedDescriptions = matchedSchemas.joinToString(", ") { it.descriptionWithDefault() }
                messageSink.error(ksonValue.location.trimToFirstLine(), SCHEMA_ONE_OF_MULTIPLE_MATCHES.create(matchedDescriptions))
            }
        }
    }

    /**
     * When this `oneOf` is a discriminated union — some shared property is pinned to a distinct
     * `const` by (most of) the branches — report against the branch the document's discriminator
     * value selects, rather than dumping every branch's errors:
     *
     *  - the value matches one branch's `const`: report only that branch's errors (the real failure
     *    lives deeper in the document, e.g. a missing required property)
     *  - the value matches no branch, and *every* branch pins the discriminator (a closed union):
     *    report one [SCHEMA_ENUM_VALUE_NOT_ALLOWED] at the value, listing the allowed `const`s
     *  - the value matches no branch, but a wildcard/negative branch leaves the union open: we
     *    can't prove the value invalid, so fall back to the dump
     *
     * Returns `true` when it handled reporting (so the caller skips the generic dump), `false` when
     * this `oneOf` is not a discriminated union or the document lacks the discriminator property.
     */
    private fun reportDiscriminatedUnionError(
        ksonValue: KsonValue,
        messageSink: MessageSink,
        sourceContext: SourceContext
    ): Boolean {
        if (ksonValue !is KsonObject) return false
        val discriminator = detectDiscriminator() ?: return false
        val discriminatorValue = ksonValue.propertyLookup[discriminator.property] ?: return false

        val selectedBranch = discriminator.branchesByConst[discriminatorValue]
        when {
            // The document picks exactly one branch; report only that branch's (deeper) errors.
            selectedBranch != null -> selectedBranch.validate(ksonValue, messageSink, sourceContext)

            // No branch matches and every branch pins the discriminator, so the value itself is wrong.
            discriminator.allBranchesPinned -> {
                val allowedValues = discriminator.branchesByConst.keys.joinToString(", ") { it.toDisplayString() }
                messageSink.error(discriminatorValue.location, SCHEMA_ENUM_VALUE_NOT_ALLOWED.create(allowedValues))
            }

            // No branch matches but a wildcard/negative branch might legitimately accept this value,
            // so we can't claim "must be one of …" — fall back to the full dump.
            else -> return false
        }
        return true
    }

    /**
     * Detects a discriminator: a property pinned to a *distinct* `const` by at least two branches.
     * Branches that don't pin the property (wildcard / negative / non-object branches) are tolerated
     * and simply left out of the const→branch map. When several properties qualify, the one pinned by
     * the most branches wins (ties broken by declaration order in the first branch that pins it).
     * Returns `null` when no property qualifies.
     */
    private fun detectDiscriminator(): Discriminator? {
        // Per branch, the properties it pins to a single const (empty for wildcard / non-object branches).
        val branchConsts = oneOf.map { (it as? JsonObjectSchema)?.constPinnedProperties() ?: emptyMap() }

        // Candidate names: every pinned property, ordered by the first branch that pins it.
        val candidateProperties = LinkedHashSet<String>().apply { branchConsts.forEach { addAll(it.keys) } }

        var best: Discriminator? = null
        for (property in candidateProperties) {
            val pinnedBranches = oneOf.indices.mapNotNull { i ->
                branchConsts[i][property]?.let { const -> const to oneOf[i] }
            }
            if (pinnedBranches.size < 2) continue
            val consts = pinnedBranches.map { it.first }
            if (consts.toSet().size != consts.size) continue // a discriminator's consts must be distinct
            if (pinnedBranches.size > (best?.branchesByConst?.size ?: 0)) {
                best = Discriminator(property, pinnedBranches.toMap(), pinnedBranches.size == oneOf.size)
            }
        }
        return best
    }

    /**
     * A discriminator [property], with the branch each pinned `const` value selects. [allBranchesPinned]
     * is `true` only when *every* branch pins it (no wildcard branches), which is what licenses a closed
     * "must be one of …" enum error when the document's value matches none of them.
     */
    private data class Discriminator(
        val property: String,
        val branchesByConst: Map<KsonValue, JsonSchema>,
        val allBranchesPinned: Boolean
    )
}

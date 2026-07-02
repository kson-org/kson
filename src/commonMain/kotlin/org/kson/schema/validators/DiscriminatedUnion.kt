package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType.SCHEMA_ENUM_VALUE_NOT_ALLOWED
import org.kson.schema.JsonObjectSchema
import org.kson.schema.JsonSchema
import org.kson.validation.SourceContext

/**
 * When [branches] form a discriminated union — some shared property is pinned to *pairwise-disjoint*
 * value sets (via `const` or `enum`) by at least two branches — report against the branch the
 * document's discriminator value selects, rather than dumping every branch's errors:
 *
 *  - the value matches one branch's pinned set: report only that branch's errors (the real failure
 *    lives deeper in the document, e.g. a missing required property)
 *  - the value matches no branch, and *every* branch pins the discriminator (a closed union):
 *    report one [SCHEMA_ENUM_VALUE_NOT_ALLOWED] at the value, listing the allowed values
 *  - the value matches no branch, but a wildcard/negative branch leaves the union open: we can't
 *    prove the value invalid, so fall back to the caller's dump
 *
 * Returns `true` when it handled reporting (so the caller skips the generic dump), `false` when
 * [branches] are not a discriminated union or the document lacks the discriminator property.
 */
internal fun reportDiscriminatedUnionError(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    messageSink: MessageSink,
    sourceContext: SourceContext
): Boolean {
    if (ksonValue !is KsonObject) return false
    val discriminator = detectDiscriminator(branches) ?: return false
    val discriminatorValue = ksonValue.propertyLookup[discriminator.property] ?: return false

    val selectedBranch = discriminator.branchByValue[discriminatorValue]
    when {
        // The document picks exactly one branch; report only that branch's (deeper) errors.
        selectedBranch != null -> selectedBranch.validate(ksonValue, messageSink, sourceContext)

        // No branch matches and every branch pins the discriminator, so the value itself is wrong.
        discriminator.allBranchesPinned -> {
            val allowedValues = discriminator.branchByValue.keys
                .joinToString(", ") { it.toDisplayString() }
            messageSink.error(discriminatorValue.location, SCHEMA_ENUM_VALUE_NOT_ALLOWED.create(allowedValues))
        }

        // No branch matches but a wildcard/negative branch might legitimately accept this value,
        // so we can't claim "must be one of …" — fall back to the caller's dump.
        else -> return false
    }
    return true
}

/**
 * A presence-based fallback for unions that carry no value discriminator: narrow to the branch(es)
 * the document's properties point at, then dump only those.  Each branch's *known* property names are
 * the properties it declares (via `properties`) unioned with those it requires — so a branch recognizes
 * a property it declares even as *optional*, not only ones it requires.  A property is *distinguishing*
 * when it's a known property of at least one branch but not of every branch; a branch matches when the
 * document carries at least one distinguishing property that branch knows.
 *
 * Reports via [reportNoSubSchemaMatchErrors] with the caller's already-collected [matchAttemptMessageSinks]
 * filtered to the matched branches — only when the matched set is a non-empty *strict* subset of the
 * branches, i.e. presence genuinely narrowed the union.  The output is whatever the full dump would
 * produce for that subset: a multi-branch match keeps the dump shape with just fewer, more relevant
 * bullets, while a single-branch match collapses to that branch's bare messages — no [noMatchMessage]
 * header and no nested sub-schema-errors bullets.  Returns `false` (letting the caller run the full dump)
 * when nothing matches or every branch matches.  Only [JsonObjectSchema] branches carry known
 * properties; others never match.
 */
internal fun reportPresenceBasedUnionError(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    messageSink: MessageSink,
    matchAttemptMessageSinks: List<LabelledMessageSink>,
    noMatchMessage: Message
): Boolean {
    if (ksonValue !is KsonObject) return false

    val knownByBranch = branches.map { branch ->
        (branch as? JsonObjectSchema)?.let { it.declaredPropertyNames() + it.requiredProperties() } ?: emptySet()
    }
    // Distinguishing: a known property of some branch but not all — only these can tell branches apart.
    val distinguishing = knownByBranch.flatten().toSet()
        .filter { property -> knownByBranch.count { property in it } < branches.size }
        .toSet()

    val presentProperties = ksonValue.propertyLookup.keys
    val matchedIndices = knownByBranch.indices.filter { i ->
        knownByBranch[i].any { it in distinguishing && it in presentProperties }
    }

    // Only report when presence narrowed to a non-empty strict subset; otherwise let the caller dump.
    if (matchedIndices.isEmpty() || matchedIndices.size == branches.size) return false

    reportNoSubSchemaMatchErrors(
        ksonValue,
        messageSink,
        matchedIndices.map { matchAttemptMessageSinks[it] },
        noMatchMessage
    )
    return true
}

/**
 * Detects a discriminator: a property pinned to *pairwise-disjoint* value sets by at least two
 * branches.  Branches that don't pin the property (wildcard / negative / non-object branches) are
 * tolerated and simply left out of the value→branch map.  When several properties qualify, the one
 * pinned by the most branches wins (ties broken by declaration order — the first branch that pins it).
 * Returns `null` when no property qualifies.
 */
private fun detectDiscriminator(branches: List<JsonSchema>): Discriminator? {
    // Per branch, the properties it pins to a finite value set (empty for wildcard / non-object branches).
    val branchPins = branches.map { (it as? JsonObjectSchema)?.pinnedProperties() ?: emptyMap() }

    // Candidate names: every pinned property, ordered by the first branch that pins it.
    val candidateProperties = LinkedHashSet<String>().apply { branchPins.forEach { addAll(it.keys) } }

    var best: Discriminator? = null
    var bestBranchCount = 0
    for (property in candidateProperties) {
        val pinningBranches = branches.indices.mapNotNull { i ->
            branchPins[i][property]?.let { values -> values to branches[i] }
        }
        // need ≥2 pinning branches, and only a strictly larger union beats the incumbent (a tie keeps
        // the earlier-declared property)
        if (pinningBranches.size < 2 || pinningBranches.size <= bestBranchCount) continue

        // A discriminator's value sets must be pairwise disjoint, so each value selects exactly one
        // branch; `put` returning non-null means two branches share a value, disqualifying this property.
        val branchByValue = LinkedHashMap<KsonValue, JsonSchema>()
        val disjoint = pinningBranches.all { (values, branch) ->
            values.all { value -> branchByValue.put(value, branch) == null }
        }
        if (disjoint) {
            best = Discriminator(property, branchByValue, pinningBranches.size == branches.size)
            bestBranchCount = pinningBranches.size
        }
    }
    return best
}

/**
 * A discriminator [property], with the branch each pinned value selects.  [allBranchesPinned] is
 * `true` only when *every* branch pins it (no wildcard branches), which is what licenses a closed
 * "must be one of …" enum error when the document's value matches none of them.
 */
private data class Discriminator(
    val property: String,
    val branchByValue: Map<KsonValue, JsonSchema>,
    val allBranchesPinned: Boolean
)

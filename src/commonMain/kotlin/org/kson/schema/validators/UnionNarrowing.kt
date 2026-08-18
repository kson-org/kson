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
 * Reporting for a document that matched no branch of a `oneOf` / `anyOf` union: narrow the reported
 * errors to the branch(es) that actually matter, rather than dumping every branch's complaints.
 *
 * Vocabulary used here and by the [JsonObjectSchema] accessors this file reads:
 *  - *pin* — a property fixed to a finite value set by `const` or `enum`.  An *empty* pin (`enum: []`)
 *    admits nothing, so it discriminates nothing but eliminates anything.
 *  - *known property* — one a branch declares under `properties` (optional included) or lists in
 *    `required`, read across everything the branch composes (`$ref`, `allOf`).
 *  - *discriminator* — a property at least two branches pin to *pairwise-disjoint* sets, so a value
 *    picks at most one branch.  The union is *closed* when every branch pins it.
 *  - *distinguishing property* — one known to some branches but not all.
 */

/**
 * Narrows and reports, trying each strategy in order until one handles it:
 *
 *  1. [selectDiscriminatedBranch] — the document's discriminator value picks a branch (or proves
 *     itself out of range in a closed union).
 *  2. [narrowByShape] — drop branches the document contradicts, keep those it looks like.
 *  3. [reportNoSubSchemaMatchErrors] — nothing narrowed; dump every branch.
 *
 * Always emits at least one error: every strategy emits ≥1 message and the final dump is
 * unconditional.  Returns [Unit] rather than a handled flag so callers can't skip that dump.
 */
internal fun reportUnionMatchFailure(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    messageSink: MessageSink,
    matchAttemptMessageSinks: List<LabelledMessageSink>,
    noMatchMessage: Message,
    sourceContext: SourceContext
) {
    if (!selectDiscriminatedBranch(branches, ksonValue, messageSink, sourceContext) &&
        !narrowByShape(branches, ksonValue, messageSink, matchAttemptMessageSinks, noMatchMessage)) {
        reportNoSubSchemaMatchErrors(ksonValue, messageSink, matchAttemptMessageSinks, noMatchMessage)
    }
}

/**
 * Reports against the branch the document's discriminator selects:
 *
 *  - value matches one branch's pin: report only that branch's errors — the real failure is deeper
 *  - value matches none and the union is closed: one [SCHEMA_ENUM_VALUE_NOT_ALLOWED] listing the
 *    allowed values
 *  - value matches none but a wildcard branch leaves the union open: we can't call the value wrong,
 *    so decline
 *
 * Returns `true` when it reported, `false` when there is no discriminator or the document lacks it.
 */
private fun selectDiscriminatedBranch(
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
        selectedBranch != null -> selectedBranch.validate(ksonValue, messageSink, sourceContext)

        discriminator.allBranchesPinned -> {
            val allowedValues = discriminator.branchByValue.keys
                .joinToString(", ") { it.toDisplayString() }
            messageSink.error(discriminatorValue.location, SCHEMA_ENUM_VALUE_NOT_ALLOWED.create(allowedValues))
        }

        // a wildcard/negative branch might legitimately accept this value
        else -> return false
    }
    return true
}

/**
 * Narrows a union with no discriminator by combining [survivingBranches] (definitive: the document
 * contradicts a pin) with [presenceMatchedBranches] (heuristic: the document carries a property the
 * branch knows and others don't).
 *
 * Reports `S ∩ M` when that is a non-empty *strict* subset of the branches, else `S` when it is, else
 * declines.  Intersecting keeps presence from resurrecting a branch the document contradicts; the
 * strict-subset guard keeps us from reporting nothing, or everything by a longer route.
 */
private fun narrowByShape(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    messageSink: MessageSink,
    matchAttemptMessageSinks: List<LabelledMessageSink>,
    noMatchMessage: Message
): Boolean {
    if (ksonValue !is KsonObject) return false

    val survivors = survivingBranches(branches, ksonValue)
    val presenceMatched = presenceMatchedBranches(branches, ksonValue)

    val intersection = survivors.filter { it in presenceMatched }
    val chosen = when {
        intersection.isNonEmptyStrictSubsetOf(branches) -> intersection
        survivors.isNonEmptyStrictSubsetOf(branches) -> survivors
        else -> return false
    }

    reportNoSubSchemaMatchErrors(
        ksonValue,
        messageSink,
        chosen.map { matchAttemptMessageSinks[it] },
        noMatchMessage
    )
    return true
}

/**
 * The branches the document does *not* contradict: it carries no property the branch pins to a set
 * the document's value falls outside of.  Definitive — one contradicted pin is proof, needing neither
 * a second branch nor disjointness.  Empty pins count, hence [includeEmptyPins].
 */
private fun survivingBranches(branches: List<JsonSchema>, ksonValue: KsonObject): List<Int> =
    branches.indices.filter { i ->
        val pins = (branches[i] as? JsonObjectSchema)?.pinnedProperties(includeEmptyPins = true) ?: emptyMap()
        pins.none { (property, values) ->
            ksonValue.propertyLookup[property]?.let { it !in values } ?: false
        }
    }

/**
 * The branches the document *looks like*: it carries a distinguishing property they know.  A
 * heuristic — a shared property proves nothing, so only properties some branches know and others
 * don't are consulted.  Non-[JsonObjectSchema] branches know nothing and so never match.
 */
private fun presenceMatchedBranches(branches: List<JsonSchema>, ksonValue: KsonObject): List<Int> {
    val knownByBranch = branches.map { (it as? JsonObjectSchema)?.knownProperties() ?: emptySet() }
    val distinguishing = knownByBranch.flatten().toSet()
        .filterTo(mutableSetOf()) { property -> knownByBranch.count { property in it } < branches.size }
    val presentProperties = ksonValue.propertyLookup.keys
    return knownByBranch.indices.filter { i ->
        knownByBranch[i].any { it in distinguishing && it in presentProperties }
    }
}

/** A non-empty proper subset of [branches] — a genuine narrowing, neither empty nor the whole set. */
private fun List<Int>.isNonEmptyStrictSubsetOf(branches: List<JsonSchema>): Boolean =
    isNotEmpty() && size < branches.size

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

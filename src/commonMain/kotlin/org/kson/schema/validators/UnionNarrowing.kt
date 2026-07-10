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
 * Reports why a document failed to match any branch of a `oneOf` / `anyOf` union, narrowing the
 * reported errors to the branch(es) that actually matter before resorting to a full per-branch dump.
 * The strategies below run in priority order; the first to report wins:
 *
 *  1. [selectDiscriminatedBranch] — a *value discriminator* (a shared property pinned to
 *     pairwise-disjoint value sets) selects the single branch the document's value picks, or proves
 *     a closed union's value out of range with one [SCHEMA_ENUM_VALUE_NOT_ALLOWED].
 *  2. [narrowByElimination] — drop any branch whose pinned value the document contradicts, then narrow
 *     the survivors by which distinguishing properties the document carries, and dump only what remains.
 *  3. [reportNoSubSchemaMatchErrors] — nothing narrowed the union, so dump every branch.
 *
 * Always emits at least one error: each strategy that handles reporting emits ≥1 message, and the final
 * dump is unconditional.  Returns [Unit] rather than a handled/not-handled flag so callers can't
 * accidentally skip the dump — the safety invariant is enforced here, not at the call site.
 *
 * [sourceContext] is threaded to [selectDiscriminatedBranch]'s deep re-validation so the selected branch is re-checked
 * under the same context that already proved every branch fails (preserving the ≥1-error invariant).
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
        !narrowByElimination(branches, ksonValue, messageSink, matchAttemptMessageSinks, noMatchMessage)) {
        reportNoSubSchemaMatchErrors(ksonValue, messageSink, matchAttemptMessageSinks, noMatchMessage)
    }
}

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
 * Narrows a union with no value discriminator to the branch(es) worth reporting
 * by composing two signals over the already-failed branches.
 *
 *  - *Elimination* (definitive): a branch is provably dead when it pins a property `p` to a value set
 *    `V` and the document carries `p` with a value ∉ `V`.  No ≥2-branch gate and no disjointness
 *    requirement — a single contradicted pin suffices.  Empty pins (`enum: []`) reject any present
 *    value, so pins are read with [JsonObjectSchema.pinnedProperties] in `includeEmptyPins` mode.
 *  - *Presence* (heuristic): a branch matches when the document carries a *distinguishing* known
 *    property — one declared or required by some but not all branches.  A branch's *known* properties
 *    are those it declares (even as optional) unioned with those it requires.  This is the exact rule
 *    the presence-only strategy used, so a pin-free union degenerates to that behavior.
 *
 * Composition, writing `S` for the surviving (not-eliminated) branches and `M` for the presence-matched:
 * report `S ∩ M` when that is a non-empty *strict* subset of the branches, else `S` when *it* is, else
 * decline (let the caller dump every branch).  Intersecting with `S` keeps presence from resurrecting a
 * branch whose own pin the document contradicts; the non-empty-strict-subset guard keeps us from
 * reporting nothing or everything.  Reports the chosen branches via [reportNoSubSchemaMatchErrors] over
 * their already-collected [matchAttemptMessageSinks] — a single chosen branch collapses to its bare
 * messages (no [noMatchMessage] header), matching that helper's existing behavior.  Only
 * [JsonObjectSchema] branches carry pins or known properties; others neither eliminate nor match.
 */
private fun narrowByElimination(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    messageSink: MessageSink,
    matchAttemptMessageSinks: List<LabelledMessageSink>,
    noMatchMessage: Message
): Boolean {
    if (ksonValue !is KsonObject) return false

    // Elimination: keep a branch unless the document contradicts one of its pins (a present value
    // outside the pinned set — which an empty pin can never contain).
    val survivors = branches.indices.filter { i ->
        val pins = (branches[i] as? JsonObjectSchema)?.pinnedProperties(includeEmptyPins = true) ?: emptyMap()
        pins.none { (property, values) ->
            ksonValue.propertyLookup[property]?.let { it !in values } ?: false
        }
    }

    // Presence: a known property of some branch but not all is distinguishing; a branch matches when
    // the document carries one such property it knows.
    val knownByBranch = branches.map { branch ->
        (branch as? JsonObjectSchema)?.let { it.declaredPropertyNames() + it.requiredProperties() } ?: emptySet()
    }
    val distinguishing = knownByBranch.flatten().toSet()
        .filter { property -> knownByBranch.count { property in it } < branches.size }
        .toSet()
    val presentProperties = ksonValue.propertyLookup.keys
    val presenceMatched = knownByBranch.indices.filter { i ->
        knownByBranch[i].any { it in distinguishing && it in presentProperties }
    }

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

/** A non-empty proper subset of [branches] — a genuine narrowing, neither empty nor the whole set. */
private fun List<Int>.isNonEmptyStrictSubsetOf(branches: List<JsonSchema>): Boolean =
    isNotEmpty() && size < branches.size

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

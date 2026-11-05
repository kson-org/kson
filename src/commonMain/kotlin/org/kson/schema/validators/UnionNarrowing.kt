package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.Message
import org.kson.schema.JsonObjectSchema
import org.kson.schema.JsonSchema
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

/**
 * Reports a document that matched no branch of a `oneOf` / `anyOf`, narrowed to the branches that
 * matter.  A *pin* is a property a branch fixes by `const` or `enum`.  A *discriminator* is a pin at
 * least two branches share, or a lone pin its branch also requires.  A branch *knows* a property it
 * declares or requires.  All are read through `$ref` and `allOf` by [JsonObjectSchema.pinnedProperties],
 * [JsonObjectSchema.requiredProperties] and [JsonObjectSchema.knownProperties].
 *
 * The *narrowing pins* are the discriminators in [ValidationMode.FULL], as a lone optional pin is more
 * likely a setting than a choice of branch.  That is a heuristic: a shared optional setting qualifies
 * too.  In [ValidationMode.PARTIAL] they are all pins, which can move the report from the object
 * holding a half-typed value onto the value itself, where errors are forgiven.
 *
 * For an object document, a branch the document contradicts on a narrowing pin is dropped.  Then,
 * counting only narrowing pins:
 *  1. If no branch survives, each property that every branch pins and rejects gets one enum error
 *     listing the pinned values.  With no such property, or only one branch, every branch is reported.
 *  2. Otherwise, survivors pinning a property the document has that no other survivor pins are reported.
 *  3. If there are none, the survivors reported are those that know a property the document has and
 *     not every branch knows, or all of them if none do.
 *
 * A non-object document reports every branch.  At least one error is always emitted.
 */
internal fun reportUnionMatchFailure(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    messageSink: MessageSink,
    matchAttemptMessageSinks: List<LabelledMessageSink>,
    noMatchMessage: Message,
    sourceContext: SourceContext
) {
    fun report(reported: List<Int>) =
        reportNoSubSchemaMatchErrors(ksonValue, messageSink, reported.map { matchAttemptMessageSinks[it] }, noMatchMessage)

    if (ksonValue !is KsonObject) {
        report(branches.indices.toList())
        return
    }
    val document = ksonValue.propertyLookup
    val allPins = branches.map { (it as? JsonObjectSchema)?.pinnedProperties() ?: emptyMap() }
    val narrowingPins = if (sourceContext.mode == ValidationMode.FULL) discriminators(branches, allPins) else allPins

    val contradictedPropertiesByBranch = narrowingPins.map { branchPins ->
        branchPins.filter { (property, values) -> document[property]?.let { it !in values } ?: false }.keys
    }
    val survivors = branches.indices.filter { contradictedPropertiesByBranch[it].isEmpty() }

    if (survivors.isEmpty()) {
        // `reduce` needs a branch, and a lone branch keeps its own errors, which say more
        val rejectedEverywhere =
            if (branches.size > 1) contradictedPropertiesByBranch.reduce { shared, next -> shared intersect next } else emptySet()
        if (rejectedEverywhere.isEmpty()) {
            report(branches.indices.toList())
        }
        rejectedEverywhere.forEach { property ->
            val allowedValues = narrowingPins.flatMapTo(LinkedHashSet()) { it.getValue(property) }
            reportEnumValueNotAllowed(document.getValue(property), messageSink, allowedValues)
        }
        return
    }

    val selected = selectedBranches(narrowingPins, document.keys, survivors)
    if (selected.isNotEmpty()) {
        report(selected)
        return
    }

    val known = branches.map { (it as? JsonObjectSchema)?.knownProperties() ?: emptySet() }
    report(presenceMatchedBranches(known, document.keys).filter { it in survivors }.ifEmpty { survivors })
}

private fun discriminators(
    branches: List<JsonSchema>,
    pins: List<Map<String, Set<KsonValue>>>
): List<Map<String, Set<KsonValue>>> =
    branches.zip(pins) { branch, branchPins ->
        val required = (branch as? JsonObjectSchema)?.requiredProperties() ?: emptySet()
        branchPins.filterKeys { property -> property in required || pins.count { property in it } >= 2 }
    }

private fun selectedBranches(
    narrowingPins: List<Map<String, Set<KsonValue>>>,
    present: Set<String>,
    survivors: List<Int>
): List<Int> =
    survivors.filter { branchIndex ->
        narrowingPins[branchIndex].keys.any { property ->
            property in present && survivors.count { property in narrowingPins[it] } == 1
        }
    }

private fun presenceMatchedBranches(known: List<Set<String>>, present: Set<String>): List<Int> {
    val distinguishing = known.flatten()
        .filterTo(mutableSetOf()) { property -> known.count { property in it } < known.size }
    return known.indices.filter { i -> known[i].any { it in distinguishing && it in present } }
}

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
 * matter.  A *pin* is a property a branch fixes by `const` or `enum`; a branch *knows* a property it
 * declares or requires.  Both are read through `$ref` and `allOf` by [JsonObjectSchema.pinnedProperties]
 * and [JsonObjectSchema.knownProperties].
 *
 * For an object document, a branch one of whose pins rejects a document value is dropped.  Then:
 *  1. if no branch survives, a property every branch pins and none admits gets one enum error listing
 *     the pinned values (a lone branch keeps its own errors, which say more); else every branch;
 *  2. else a value only one surviving branch's pin admits selects that branch;
 *  3. else the survivors, preferring those that know a property the document carries and not every
 *     branch knows.
 *
 * A non-object document, or [ValidationMode.PARTIAL] mode (where a half-typed document shouldn't get
 * the enum error), reports every branch.  At least one error is always emitted: the union validators
 * emit nothing of their own on failure.
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

    if (ksonValue !is KsonObject || sourceContext.mode == ValidationMode.PARTIAL) {
        report(branches.indices.toList())
        return
    }
    val document = ksonValue.propertyLookup
    val pins = branches.map { (it as? JsonObjectSchema)?.pinnedProperties() ?: emptyMap() }

    val contradictedPropertiesByBranch = pins.map { branchPins ->
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
            val allowedValues = pins.flatMapTo(LinkedHashSet()) { it.getValue(property) }
            reportEnumValueNotAllowed(document.getValue(property), messageSink, allowedValues)
        }
        return
    }

    val selected = selectedBranches(pins, document, survivors)
    if (selected.isNotEmpty()) {
        report(selected)
        return
    }

    val known = branches.map { (it as? JsonObjectSchema)?.knownProperties() ?: emptySet() }
    report(presenceMatchedBranches(known, document.keys).filter { it in survivors }.ifEmpty { survivors })
}

/** The survivors with a pin admitting a document value that no other survivor's pin admits. */
private fun selectedBranches(
    pins: List<Map<String, Set<KsonValue>>>,
    document: Map<String, KsonValue>,
    survivors: List<Int>
): List<Int> =
    survivors.filter { branchIndex ->
        pins[branchIndex].any { (property, values) ->
            val value = document[property]
            value != null && value in values &&
                survivors.count { value in pins[it][property].orEmpty() } == 1
        }
    }

/** The branches that know a present property not every branch knows. */
private fun presenceMatchedBranches(known: List<Set<String>>, present: Set<String>): List<Int> {
    val distinguishing = known.flatten()
        .filterTo(mutableSetOf()) { property -> known.count { property in it } < known.size }
    return known.indices.filter { i -> known[i].any { it in distinguishing && it in present } }
}

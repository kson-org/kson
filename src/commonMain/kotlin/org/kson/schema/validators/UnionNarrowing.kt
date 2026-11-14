package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.SCHEMA_OTHER_SUB_SCHEMA_ERRORS
import org.kson.parser.messages.MessageType.SCHEMA_SUB_SCHEMA_ERRORS
import org.kson.schema.JsonObjectSchema
import org.kson.schema.JsonSchema
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

/**
 * Reports a document that matched no branch of a `oneOf` / `anyOf`, led by the branches that
 * matter.  A *pin* is a property a branch fixes by `const` or `enum`.  A *discriminator* is a pin at
 * least two branches share, or a lone pin its branch also requires.  A branch *knows* a property it
 * declares or requires.  All are read through `$ref` and `allOf` by [JsonObjectSchema.pinnedProperties],
 * [JsonObjectSchema.requiredProperties] and [JsonObjectSchema.knownProperties].
 *
 * The *narrowing pins* are the discriminators in [ValidationMode.FULL], as a lone optional pin is more
 * likely a setting than a choice of branch.  That is a heuristic: a shared optional setting qualifies
 * too.  In [ValidationMode.PARTIAL] they are all pins, which can move the report from the object
 * holding a half-typed value onto the value itself.
 *
 * For an object document, a branch the document contradicts on a narrowing pin can't lead.  Then,
 * counting only narrowing pins:
 *  1. If no branch survives, each property that every branch pins and rejects gets one enum error
 *     listing the pinned values, and these lead instead of any branch.  With no such property, or
 *     only one branch, every branch leads.
 *  2. Otherwise, survivors pinning a property the document has that no other survivor pins lead.
 *  3. If there are none, the survivors that lead are those that know a property the document has and
 *     not every branch knows, or all of them if none do.
 *
 * Every branch leads for a non-object document.
 *
 * Narrowing is a heuristic, so in [ValidationMode.FULL] it only orders the report: the leading
 * branches come first and every other branch is still listed.  A [ValidationMode.PARTIAL] report
 * stays narrow, keeping a half-typed value's error on the value.  At least one error is always
 * emitted.
 */
internal fun reportUnionMatchFailure(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    messageSink: MessageSink,
    matchAttemptMessageSinks: List<LabelledMessageSink>,
    noMatchMessage: Message,
    sourceContext: SourceContext
) {
    // Find the branches the document most likely meant, or an enum error when no branch accepts a
    // pinned value
    val enumErrors = MessageSink()
    val leading = leadingBranches(branches, ksonValue, sourceContext.mode, enumErrors)
        .map { matchAttemptMessageSinks[it] }

    // Errors all the likely branches agree on are reported once, right where they occur
    val reportedSeparately = enumErrors.loggedMessages().toSet() + errorsSharedBy(leading)
    reportedSeparately.forEach { messageSink.error(it.location, it.message) }

    val anchor = ksonValue.location.trimToFirstLine()
    fun listing(heading: MessageType, subSchemas: List<LabelledMessageSink>, omit: Set<LoggedMessage> = emptySet()) =
        messageSink.error(anchor, heading.create(subSchemaErrorSummary(subSchemas, omit)))

    // A draft report stays short
    if (sourceContext.mode == ValidationMode.PARTIAL) {
        if (reportedSeparately.isEmpty()) {
            messageSink.error(anchor, noMatchMessage)
            listing(SCHEMA_SUB_SCHEMA_ERRORS, leading)
        }
        return
    }

    // Then whatever else the likely branches complain about.  A union without branches only gets this.
    val leadingHaveUnreportedErrors = leading.any { (it.messageSink.loggedMessages() - reportedSeparately).isNotEmpty() }
    if (reportedSeparately.isEmpty() || leadingHaveUnreportedErrors) {
        messageSink.error(anchor, noMatchMessage)
        listing(SCHEMA_SUB_SCHEMA_ERRORS, leading, reportedSeparately)
    }
    // And finally the remaining branches, so nothing is hidden: narrowing only decides the order
    val others = matchAttemptMessageSinks - leading.toSet()
    if (others.isNotEmpty()) {
        val heading = if (leading.isEmpty()) SCHEMA_SUB_SCHEMA_ERRORS else SCHEMA_OTHER_SUB_SCHEMA_ERRORS
        listing(heading, others)
    }
}

private fun errorsSharedBy(subSchemas: List<LabelledMessageSink>): Set<LoggedMessage> =
    subSchemas.map { it.messageSink.loggedMessages().toSet() }
        .reduceOrNull { shared, next -> shared intersect next } ?: emptySet()

/**
 * The indices of the [branches] that lead the report, per [reportUnionMatchFailure]; the enum errors
 * that lead instead are logged to [enumErrors].
 */
private fun leadingBranches(
    branches: List<JsonSchema>,
    ksonValue: KsonValue,
    mode: ValidationMode,
    enumErrors: MessageSink
): List<Int> {
    if (ksonValue !is KsonObject) {
        return branches.indices.toList()
    }
    val document = ksonValue.propertyLookup
    val allPins = branches.map { (it as? JsonObjectSchema)?.pinnedProperties() ?: emptyMap() }
    val narrowingPins = if (mode == ValidationMode.FULL) discriminators(branches, allPins) else allPins

    val contradictedPropertiesByBranch = narrowingPins.map { branchPins ->
        branchPins.filter { (property, values) -> document[property]?.let { it !in values } ?: false }.keys
    }
    val survivors = branches.indices.filter { contradictedPropertiesByBranch[it].isEmpty() }

    if (survivors.isEmpty()) {
        // `reduce` needs a branch, and a lone branch keeps its own errors, which say more
        val rejectedEverywhere =
            if (branches.size > 1) contradictedPropertiesByBranch.reduce { shared, next -> shared intersect next } else emptySet()
        rejectedEverywhere.forEach { property ->
            val allowedValues = narrowingPins.flatMapTo(LinkedHashSet()) { it.getValue(property) }
            reportEnumValueNotAllowed(document.getValue(property), enumErrors, allowedValues)
        }
        return if (rejectedEverywhere.isEmpty()) branches.indices.toList() else emptyList()
    }

    val selected = selectedBranches(narrowingPins, document.keys, survivors)
    if (selected.isNotEmpty()) {
        return selected
    }

    val known = branches.map { (it as? JsonObjectSchema)?.knownProperties() ?: emptySet() }
    return presenceMatchedBranches(known, document.keys).filter { it in survivors }.ifEmpty { survivors }
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

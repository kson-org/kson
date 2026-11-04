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
import org.kson.schema.PinDecision
import org.kson.schema.SchemaDecisions
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

/**
 * Reports a document that matched no branch of a `oneOf` / `anyOf`, led by the branches that
 * matter by what the pins decide for an object document:
 *  - [PinDecision.NoBranchFits]: each rejected property gets one enum error listing the pinned values,
 *    and these lead instead of any branch.  With no such property, every branch leads.
 *  - [PinDecision.Selected]: the chosen branches lead.
 *  - [PinDecision.Open]: the survivors that lead are those that know a property the document has and
 *    not every branch knows, or all of them if none do.  A branch *knows* a property it declares or
 *    requires, read through `$ref` and `allOf` by [JsonObjectSchema.knownProperties].
 *
 * Only discriminators decide in [ValidationMode.FULL] ([SchemaDecisions.narrowOnDiscriminators]), as a
 * lone optional pin is more likely a setting than a choice of branch.  That is a heuristic: a shared
 * optional setting qualifies too.  In [ValidationMode.PARTIAL] all pins decide
 * ([SchemaDecisions.narrowBranches]), which can move the report from the object holding a half-typed
 * value onto the value itself.
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
    val decision = if (mode == ValidationMode.FULL) {
        SchemaDecisions.narrowOnDiscriminators(branches, ksonValue)
    } else {
        SchemaDecisions.narrowBranches(branches, ksonValue)
    }
    return when (decision) {
        is PinDecision.NoBranchFits -> {
            decision.rejected.forEach { (property, allowedValues) ->
                reportEnumValueNotAllowed(document.getValue(property), enumErrors, allowedValues)
            }
            if (decision.rejected.isEmpty()) branches.indices.toList() else emptyList()
        }
        is PinDecision.Selected -> decision.chosen
        is PinDecision.Open -> {
            val known = branches.map { (it as? JsonObjectSchema)?.knownProperties() ?: emptySet() }
            presenceMatchedBranches(known, document.keys).filter { it in decision.survivors }.ifEmpty { decision.survivors }
        }
    }
}

private fun presenceMatchedBranches(known: List<Set<String>>, present: Set<String>): List<Int> {
    val distinguishing = known.flatten()
        .filterTo(mutableSetOf()) { property -> known.count { property in it } < known.size }
    return known.indices.filter { i -> known[i].any { it in distinguishing && it in present } }
}

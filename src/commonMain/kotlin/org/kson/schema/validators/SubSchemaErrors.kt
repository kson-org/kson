package org.kson.schema.validators

import org.kson.parser.MessageSink
import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageType.SCHEMA_SUB_SCHEMA_ERRORS
import org.kson.value.KsonValue

internal class LabelledMessageSink(val label: String, val messageSink: MessageSink)

/**
 * Report errors when a value fails to match any sub-schema in a composite schema constraint.
 *
 * If there are "universal" issues—errors common to every sub-schema attempt—those are reported
 * directly, since they must be fixed before more specific errors become meaningful. When errors
 * are diverse across sub-schemas, the [noMatchMessage] is reported followed by grouped
 * per-sub-schema error details.
 */
internal fun reportNoSubSchemaMatchErrors(
    ksonValue: KsonValue,
    messageSink: MessageSink,
    matchAttemptMessageSinks: List<LabelledMessageSink>,
    noMatchMessage: Message
) {
    val matchAttemptMessageGroups = matchAttemptMessageSinks.map { it.messageSink.loggedMessages() }
    val universalMessages = matchAttemptMessageGroups.takeIf {
        it.isNotEmpty()
    }?.reduce { acc, messages ->
        acc.intersect(messages.toSet()).toList()
    } ?: emptyList()

    if (universalMessages.isNotEmpty()) {
        universalMessages.forEach {
            messageSink.error(it.location, it.message)
        }
    } else {
        messageSink.error(ksonValue.location.trimToFirstLine(), noMatchMessage)

        // for the other subSchema-specific messages, we write one group message anchored to
        // the beginning of the object
        val subSchemaErrors = matchAttemptMessageSinks.joinToString("\n\n") { matchAttemptSink ->
            "'" + matchAttemptSink.label + "': [" +
                    matchAttemptSink.messageSink
                        .loggedMessages()
                        .joinToString(",") {
                            "'${it.message}'"
                        } + "]"
        }

        messageSink.error(ksonValue.location.trimToFirstLine(), SCHEMA_SUB_SCHEMA_ERRORS.create(subSchemaErrors))
    }
}

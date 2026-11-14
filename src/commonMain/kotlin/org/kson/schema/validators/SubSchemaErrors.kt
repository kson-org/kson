package org.kson.schema.validators

import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink

internal class LabelledMessageSink(val label: String, val messageSink: MessageSink)

/**
 * The errors [subSchemas] logged, less any [reportedSeparately], rendered as nested bullets: one
 * bullet per sub-schema, each of its errors on an indented sub-bullet with a base-1 `(line:column)`
 * location after its first line.  The further lines of an error, such as a nested union's report, are
 * indented beneath its sub-bullet.  A sub-schema with no error left still gets a bullet saying so, as
 * it may be the closest match.
 */
internal fun subSchemaErrorSummary(
    subSchemas: List<LabelledMessageSink>,
    reportedSeparately: Set<LoggedMessage>
): String =
    subSchemas.joinToString("\n") { subSchema ->
        val errors = subSchema.messageSink.loggedMessages() - reportedSeparately
        if (errors.isEmpty()) {
            "- ${subSchema.label}: no errors beyond those reported separately"
        } else {
            errors.joinToString("\n", prefix = "- ${subSchema.label}:\n") {
                val start = it.location.start
                val lines = it.message.toString().lines()
                "    - ${lines.first()} (${start.line + 1}:${start.column + 1})" +
                    lines.drop(1).joinToString("") { line -> "\n      $line" }
            }
        }
    }

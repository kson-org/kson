package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType.SCHEMA_ANY_OF_VALIDATION_FAILED
import org.kson.parser.messages.MessageType.SCHEMA_SUB_SCHEMA_ERRORS
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator

class AnyOfValidator(private val anyOf: List<JsonSchema>) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val matchAttemptMessageSinks: MutableList<LabelledMessageSink> = mutableListOf()
        val anyValid = anyOf.any {
            val anyOfMessageSink = MessageSink()
            it.validate(ksonValue, anyOfMessageSink)
            matchAttemptMessageSinks.add(LabelledMessageSink(it.descriptionWithDefault(), anyOfMessageSink))
            // were we valid
            !anyOfMessageSink.hasMessages()
        }

        if (!anyValid) {
            val matchAttemptMessageGroups = matchAttemptMessageSinks.map { it.messageSink.loggedMessages() }
            val universalMessages = matchAttemptMessageGroups.takeIf {
                it.isNotEmpty()
            }?.reduce { acc, messages ->
                acc.intersect(messages).toList()
            } ?: emptyList()

            if (universalMessages.isNotEmpty()) {
                /**
                 * If there are "universal" issues that make this invalid for all the schemas, we can log those directly.
                 * Users must fix these errors to reveal any other errors---though it's usually preferable to render all
                 * possible errors when they are detected, using a progressive approach here makes the often-opaque
                 * sub-schema errors a bit easier to handle
                 */
                universalMessages.forEach {
                    messageSink.error(it.location, it.message)
                }
            } else {
                messageSink.error(ksonValue.location.trimToFirstLine(), SCHEMA_ANY_OF_VALIDATION_FAILED.create())

                // for the other subSchema-specific messages, we write one group message anchored to
                // the beginning of the object
                var subSchemaErrors = matchAttemptMessageSinks.joinToString("\n\n") { matchAttemptSink ->
                    "'" + matchAttemptSink.label + "': [" +
                            matchAttemptSink.messageSink
                                .loggedMessages()
                                .joinToString(",") {
                                    "\'${it.message}\'"
                                } + "]"
                }

                messageSink.error(ksonValue.location.trimToFirstLine(), SCHEMA_SUB_SCHEMA_ERRORS.create(subSchemaErrors))
            }
        }
    }
}

private class LabelledMessageSink(val label: String, val messageSink: MessageSink)

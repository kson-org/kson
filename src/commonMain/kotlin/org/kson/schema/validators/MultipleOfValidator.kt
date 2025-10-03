package org.kson.schema.validators

import org.kson.value.KsonNumber
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonNumberValidator
import kotlin.math.abs
import kotlin.math.max

class MultipleOfValidator(private val multipleOf: Double) : JsonNumberValidator() {
    override fun validateNumber(node: KsonNumber, messageSink: MessageSink) {
        val number = node.value.asDouble
        if (multipleOf != 0.0) {
            val remainder = number % multipleOf
            val epsilon = 1e-10 * max(abs(number), abs(multipleOf))
            if (abs(remainder) > epsilon && abs(remainder - multipleOf) > epsilon) {
                messageSink.error(
                    node.location,
                    MessageType.SCHEMA_VALUE_MUST_BE_MULTIPLE_OF.create(multipleOf.toString())
                )
            }
        }
    }
}

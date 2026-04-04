package org.kson.schema.validators

import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonStringValidator
import org.kson.value.KsonString

class PatternValidator(
    private val pattern: Regex,
) : JsonStringValidator() {
    override fun validateString(
        node: KsonString,
        messageSink: MessageSink,
    ) {
        val str = node.value
        if (!pattern.containsMatchIn(str)) {
            messageSink.error(node.location, MessageType.SCHEMA_STRING_PATTERN_MISMATCH.create(pattern.pattern))
        }
    }
}

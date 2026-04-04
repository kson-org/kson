package org.kson.schema.validators

import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext
import org.kson.value.KsonList
import org.kson.value.KsonValue

class EnumValidator(
    private val enum: KsonList,
) : JsonSchemaValidator {
    override fun validate(
        ksonValue: KsonValue,
        messageSink: MessageSink,
        sourceContext: SourceContext,
    ) {
        val enumValues = enum.elements
        if (!enumValues.contains(ksonValue)) {
            val allowedValues = enumValues.joinToString(", ") { it.toDisplayString() }
            messageSink.error(ksonValue.location, MessageType.SCHEMA_ENUM_VALUE_NOT_ALLOWED.create(allowedValues))
        }
    }
}

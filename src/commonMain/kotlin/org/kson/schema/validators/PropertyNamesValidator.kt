package org.kson.schema.validators

import org.kson.parser.MessageSink
import org.kson.schema.JsonObjectValidator
import org.kson.schema.JsonSchema
import org.kson.value.KsonObject

class PropertyNamesValidator(
    private val propertyNamesSchema: JsonSchema,
) : JsonObjectValidator() {
    override fun validateObject(
        node: KsonObject,
        messageSink: MessageSink,
    ) {
        node.propertyMap.forEach { (_, property) ->
            propertyNamesSchema.validate(property.propName, messageSink)
        }
    }
}

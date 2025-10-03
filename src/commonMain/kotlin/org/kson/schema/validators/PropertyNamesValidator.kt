package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.parser.MessageSink
import org.kson.schema.JsonObjectValidator
import org.kson.schema.JsonSchema

class PropertyNamesValidator(private val propertyNamesSchema: JsonSchema?) : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        node.propertyMap.forEach { (_, property) ->
            propertyNamesSchema?.validate(property.propName, messageSink)
        }
    }
}

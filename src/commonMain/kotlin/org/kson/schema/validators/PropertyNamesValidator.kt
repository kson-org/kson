package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.parser.MessageSink
import org.kson.schema.JsonObjectValidator
import org.kson.schema.JsonSchema
import org.kson.validation.SourceContext

class PropertyNamesValidator(private val propertyNamesSchema: JsonSchema) : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink, sourceContext: SourceContext) {
        node.propertyMap.forEach { (_, property) ->
            propertyNamesSchema.validate(property.propName, messageSink, sourceContext)
        }
    }
}

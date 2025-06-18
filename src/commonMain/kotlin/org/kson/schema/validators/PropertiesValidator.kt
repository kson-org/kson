package org.kson.schema.validators

import org.kson.ast.KsonObject
import org.kson.ast.KsonValue
import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator
import org.kson.schema.JsonSchema

class PropertiesValidator(private val propertySchemas: Map<String, JsonSchema?>?,
                          private val patternPropertySchemas: Map<String, JsonSchema?>?,
                          private val additionalPropertiesValidator: AdditionalPropertiesValidator?)
    : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        val objectProperties = node.propertyMap
        val seenKeys = mutableSetOf<String>()
        
        // First, validate regular properties
        propertySchemas?.forEach { (key, schema) ->
            objectProperties[key]?.let { objectProperty ->
                schema?.validate(objectProperty, messageSink)
            }
            seenKeys.add(key)
        }

        // Then validate pattern properties - need to check ALL patterns for each property
        patternPropertySchemas?.let { patterns ->
            objectProperties.forEach { (propertyName, propertyValue) ->
                var matchedAnyPattern = false
                
                patterns.forEach { (pattern, schema) ->
                    val patternMatcher = Regex(pattern)
                    if (patternMatcher.containsMatchIn(propertyName)) {
                        matchedAnyPattern = true
                        schema?.validate(propertyValue, messageSink)
                    }
                }
                
                if (matchedAnyPattern) {
                    seenKeys.add(propertyName)
                }
            }
        }

        // Finally, validate additional properties
        val remainingProperties = node.propertyMap.filter { !seenKeys.contains(it.key) }
        additionalPropertiesValidator?.validateProperties(remainingProperties, node.location, messageSink)
    }
}

sealed interface AdditionalPropertiesValidator {
    fun validateProperties(remainingProperties: Map<String, KsonValue>, location: Location, messageSink: MessageSink)
}

data class AdditionalPropertiesBooleanValidator(val allowed: Boolean) : AdditionalPropertiesValidator {
    override fun validateProperties(remainingProperties: Map<String, KsonValue>, location: Location, messageSink: MessageSink) {
        if (!allowed && remainingProperties.isNotEmpty()) {
            messageSink.error(location, MessageType.SCHEMA_VALIDATION_ERROR.create("dm todo"))
        }
    }
}

data class AdditionalPropertiesSchemaValidator(val schema: JsonSchema?) : AdditionalPropertiesValidator {
    override fun validateProperties(remainingProperties: Map<String, KsonValue>, location: Location, messageSink: MessageSink) {
        if (schema == null) {
            return
        }
        remainingProperties.forEach {
            schema.validate(it.value, messageSink)
        }
    }
}

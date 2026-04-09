package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonObjectProperty
import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator
import org.kson.schema.JsonSchema

/** A pre-compiled regex from a JSON Schema `patternProperties` key, paired with its sub-schema. */
data class CompiledPatternSchema(val regex: Regex, val schema: JsonSchema?)

class PropertiesValidator(private val propertySchemas: Map<KsonString, JsonSchema?>?,
                          private val compiledPatterns: List<CompiledPatternSchema>?,
                          private val additionalPropertiesValidator: AdditionalPropertiesValidator?)
    : JsonObjectValidator() {

    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        val objectProperties = node.propertyLookup
        val seenKeys = mutableSetOf<String>()

        // First, validate regular properties
        propertySchemas?.forEach { (key, schema) ->
            objectProperties[key.value]?.let { objectProperty ->
                schema?.validate(objectProperty, messageSink)
            }
            seenKeys.add(key.value)
        }

        // Then validate pattern properties - need to check ALL patterns for each property
        compiledPatterns?.let { patterns ->
            objectProperties.forEach { (propertyName, propertyValue) ->
                var matchedAnyPattern = false

                patterns.forEach { (regex, schema) ->
                    if (regex.containsMatchIn(propertyName)) {
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
    fun validateProperties(remainingProperties: Map<String, KsonObjectProperty>, location: Location, messageSink: MessageSink)
}

data class AdditionalPropertiesBooleanValidator(val allowed: Boolean, private val schemaTitle: String?) : AdditionalPropertiesValidator {
    override fun validateProperties(remainingProperties: Map<String, KsonObjectProperty>, location: Location, messageSink: MessageSink) {
        if (!allowed && remainingProperties.isNotEmpty()) {
            remainingProperties.forEach { (_, property) ->
                messageSink.error(property.propName.location, MessageType.SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED.create(property.propName.value, schemaTitle ?: ""))
            }
        }
    }
}

data class AdditionalPropertiesSchemaValidator(val schema: JsonSchema) : AdditionalPropertiesValidator {
    override fun validateProperties(remainingProperties: Map<String, KsonObjectProperty>, location: Location, messageSink: MessageSink) {
        remainingProperties.forEach { (_, property) ->
            val propertyMessageSink = MessageSink()
            schema.validate(property.propValue, propertyMessageSink)
            if (propertyMessageSink.hasMessages()) {
                messageSink.error(
                    property.propName.location,
                    MessageType.SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH.create(
                        property.propName.value,
                        schema.descriptionWithDefault()
                    )
                )
                propertyMessageSink.loggedMessages().forEach {
                    messageSink.error(it.location, it.message)
                }
            }
        }
    }
}

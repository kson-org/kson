package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonObjectProperty
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator
import org.kson.schema.JsonSchema

class DependenciesValidator(private val dependencies: Map<String, DependencyValidator>)
    : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        val properties = node.propertyMap
        dependencies.forEach { (name, dependency) ->
            properties[name]?.let {
                dependency.validate(node, it, messageSink)
            }
        }
    }
}

sealed interface DependencyValidator {
    /**
     * @param ksonObject the [KsonObject] to check for dependencies
     * @param requiredBy the [KsonObjectProperty] of [KsonObject] that requires these dependencies
     */
    fun validate(ksonObject: KsonObject, requiredBy: KsonObjectProperty, messageSink: MessageSink)
}
data class DependencyValidatorArray(val dependency: Set<KsonString>) : DependencyValidator {
    override fun validate(ksonObject: KsonObject, requiredBy: KsonObjectProperty, messageSink: MessageSink) {
        val propertyNames = ksonObject.propertyMap.keys
        dependency.forEach {
            val requiredPropertyName = it.value
            if (!propertyNames.contains(requiredPropertyName)) {
                val requiredByName = requiredBy.propName
                messageSink.error(requiredByName.location,
                    MessageType.SCHEMA_MISSING_REQUIRED_DEPENDENCIES.create(requiredByName.value, requiredPropertyName))
            }
        }
    }
}
data class DependencyValidatorSchema(val dependency: JsonSchema?) : DependencyValidator {
    override fun validate(ksonObject: KsonObject, requiredBy: KsonObjectProperty, messageSink: MessageSink) {
        if (dependency == null) {
            return
        }
        val dependencyErrorsMessageSink = MessageSink()
        dependency.validate(ksonObject, dependencyErrorsMessageSink)
        if (dependencyErrorsMessageSink.loggedMessages().isNotEmpty()) {
            val requiredByName = requiredBy.propName
            dependencyErrorsMessageSink.loggedMessages().forEach { message ->
                messageSink.error(message.location,
                    MessageType.SCHEMA_DEPENDENCIES_SCHEMA_ERROR.create(requiredByName.value, message.message.toString()))
            }
        }
    }
}

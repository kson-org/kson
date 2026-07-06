package org.kson.schema.validators

import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonObjectProperty
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator
import org.kson.schema.JsonSchema
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class DependenciesValidator(private val dependencies: Map<String, DependencyValidator>)
    : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink, sourceContext: SourceContext) {
        val properties = node.propertyMap
        dependencies.forEach { (name, dependency) ->
            properties[name]?.let {
                dependency.validate(node, it, messageSink, sourceContext)
            }
        }
    }
}

sealed interface DependencyValidator {
    /**
     * @param ksonObject the [KsonObject] to check for dependencies
     * @param requiredBy the [KsonObjectProperty] of [KsonObject] that requires these dependencies
     */
    fun validate(ksonObject: KsonObject, requiredBy: KsonObjectProperty, messageSink: MessageSink, sourceContext: SourceContext)
}
data class DependencyValidatorArray(val dependency: Set<KsonString>) : DependencyValidator {
    override fun validate(ksonObject: KsonObject, requiredBy: KsonObjectProperty, messageSink: MessageSink, sourceContext: SourceContext) {
        // a not-yet-typed required dependency is mere incompleteness, never a contradiction
        if (sourceContext.mode == ValidationMode.PARTIAL) return
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
data class DependencyValidatorSchema(val dependency: JsonSchema) : DependencyValidator {
    override fun validate(ksonObject: KsonObject, requiredBy: KsonObjectProperty, messageSink: MessageSink, sourceContext: SourceContext) {
        val dependencyErrorsMessageSink = MessageSink()
        dependency.validate(ksonObject, dependencyErrorsMessageSink, sourceContext)
        if (dependencyErrorsMessageSink.loggedMessages().isNotEmpty()) {
            val requiredByName = requiredBy.propName
            dependencyErrorsMessageSink.loggedMessages().forEach { message ->
                messageSink.error(message.location,
                    MessageType.SCHEMA_DEPENDENCIES_SCHEMA_ERROR.create(requiredByName.value, message.message.toString()))
            }
        }
    }
}

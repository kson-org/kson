package org.kson.schema.validators

import org.kson.KsonObject
import org.kson.KsonString
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonObjectValidator
import org.kson.schema.JsonSchema

class DependenciesValidator(private val dependencies: Map<KsonString, DependencyValidator>)
    : JsonObjectValidator() {
    override fun validateObject(node: KsonObject, messageSink: MessageSink) {
        val properties = node.propertyMap
        dependencies.forEach { (name, dependency) ->
            properties[name]?.let {
                dependency.validate(node, messageSink)
            }
        }
    }
}

sealed interface DependencyValidator {
    fun validate(ksonObject: KsonObject, messageSink: MessageSink): Boolean
}
data class DependencyValidatorArray(val dependency: Set<KsonString>) : DependencyValidator {
    override fun validate(ksonObject: KsonObject, messageSink: MessageSink): Boolean {
        val propertyNames = ksonObject.propertyMap.keys
        val allPresent = dependency.all {
            propertyNames.contains(it)
        }
        if (!allPresent) {
            messageSink.error(ksonObject.location, MessageType.SCHEMA_MISSING_REQUIRED_DEPENDENCIES.create())
        }
        return allPresent
    }
}
data class DependencyValidatorSchema(val dependency: JsonSchema?) : DependencyValidator {
    override fun validate(ksonObject: KsonObject, messageSink: MessageSink): Boolean {
        if (dependency == null) {
            return true
        }
        val numErrors = messageSink.loggedMessages().size
        dependency.validate(ksonObject, messageSink)
        // valid if we didn't add any new errors
        return messageSink.loggedMessages().size == numErrors
    }
}

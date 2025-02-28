package org.kson.testSupport

import org.yaml.snakeyaml.Yaml

actual fun validateYaml(yamlString: String) {
    try {
        Yaml().load<Any>(yamlString)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid YAML: ${e.message}", e)
    }
} 
package org.kson.api

/**
 * Core interface for transpilation options shared across all output formats.
 */
sealed class TranspileOptions {
    abstract val retainEmbedTags: Boolean

    /**
     * Options for transpiling Kson to JSON.
     */
    class Json(
        override val retainEmbedTags: Boolean = true
    ) : TranspileOptions()

    /**
     * Options for transpiling Kson to YAML.
     */
    class Yaml(
        override val retainEmbedTags: Boolean = true
    ) : TranspileOptions()
}

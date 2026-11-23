package org.kson.schema

import org.kson.value.KsonValue

/** The schemas applied to one instance so far, which a `$ref` must not lead back to */
internal class SameInstanceSchemas private constructor(private val innermost: Link?) {
    private class Link(val schema: KsonValue, val outer: Link?)

    /** These schemas and [schema] */
    fun with(schema: KsonValue): SameInstanceSchemas = SameInstanceSchemas(Link(schema, innermost))

    /** The schemas a subschema starts with: these if [applies] says it validates the same instance, else none */
    fun into(applies: Applies): SameInstanceSchemas = if (applies.toSameInstance) this else NONE

    /** Whether [schema] itself, rather than an equal value elsewhere, is one of these schemas */
    operator fun contains(schema: KsonValue): Boolean =
        generateSequence(innermost) { it.outer }.any { it.schema === schema }

    companion object {
        val NONE = SameInstanceSchemas(null)
    }
}

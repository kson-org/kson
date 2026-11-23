package org.kson.schema

/**
 * The part of a schema that decides which of its branches an instance takes.  An `anyOf` and a `oneOf` with the same
 * alternatives are equal.
 */
sealed interface SchemaChoice {
    /** An `anyOf` or `oneOf`, where an instance takes some of the [alternatives] */
    @ConsistentCopyVisibility
    data class Among internal constructor(val alternatives: List<SchemaNode>) : SchemaChoice

    /** An `if`, whose [condition] decides between `then` and `else` */
    @ConsistentCopyVisibility
    data class By internal constructor(val condition: SchemaNode) : SchemaChoice
}

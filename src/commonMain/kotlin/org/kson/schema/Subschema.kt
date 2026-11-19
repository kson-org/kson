package org.kson.schema

/**
 * A subschema under [keyword], and the part of the instance it [applies] to.
 *
 * @param choice The [SchemaChoice] this subschema belongs to, if any.  All alternatives of an `anyOf` or `oneOf`
 *   share one choice, and so do an `if` and its `then` and `else`.
 */
class Subschema internal constructor(
    val keyword: String,
    resolve: () -> SchemaNode,
    val applies: Applies,
    val choice: SchemaChoice?
) {
    /**
     * The subschema with its chain of `$ref`s followed (see [IndexedSchemaDocuments.resolveRefIfPresent]), found when
     * first read
     */
    val resolved: SchemaNode by lazy(resolve)
}

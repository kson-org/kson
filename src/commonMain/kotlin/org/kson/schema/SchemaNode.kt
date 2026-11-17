package org.kson.schema

import org.kson.uri.UriReference
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/**
 * A schema together with the base URI it inherits and the indexed documents its `$ref`s resolve in.
 *
 * @param baseUri The base URI that [value] inherits from its parent schema, or from its document for a root schema.
 *   It depends only on where [value] sits in its document, so it is the same whether [value] is reached through
 *   subschemas or `$ref`s.
 * @param unresolvedRef The `$ref` in [value] that couldn't be followed, if any
 */
@ConsistentCopyVisibility
data class SchemaNode internal constructor(
    val value: KsonValue,
    val baseUri: UriReference,
    internal val indexedDocuments: IndexedSchemaDocuments,
    val unresolvedRef: UnresolvedRef? = null
) {
    val endsAtUnresolvedRef: Boolean get() = unresolvedRef != null

    /** The base URI inside [value], which its `$ref` resolves against and its subschemas inherit */
    internal val innerBaseUri: UriReference by lazy { baseUri.withIdOf(value) }

    /** Returns a node for [child], a value inside this schema, with [innerBaseUri] as its base URI */
    internal fun enter(child: KsonValue): SchemaNode = SchemaNode(child, innerBaseUri, indexedDocuments)

    /**
     * Returns the node for the last value in [path], or this node when the path is empty.  The first value must sit
     * directly inside this schema, and each later value directly inside the value before it.
     */
    fun descend(path: List<KsonValue>): SchemaNode = path.fold(this) { parent, child -> parent.enter(child) }

    /** The direct subschemas of this schema, in the order validation applies their keywords */
    val subschemas: List<Subschema> by lazy {
        val schemaObject = value as? KsonObject ?: return@lazy emptyList()
        draft7Keywords.flatMap { keyword ->
            val declaredSubschemas = keyword.declaredSubschemas(schemaObject)
            val alternatives = declaredSubschemas.filter { it.applies is Applies.Alternative }.map { enter(it.value) }
            val condition = declaredSubschemas.firstOrNull { it.applies == Applies.Condition }?.value
            val amongAlternatives = if (alternatives.isEmpty()) null else SchemaChoice.Among(alternatives)
            val byCondition = condition?.let { SchemaChoice.By(enter(it)) }
            declaredSubschemas.map { declared ->
                val choice = when (declared.applies) {
                    is Applies.Alternative -> amongAlternatives
                    Applies.Condition, Applies.Then, Applies.Else -> byCondition
                    else -> null
                }
                val subschema = { indexedDocuments.resolveRefIfPresent(enter(declared.value)) }
                Subschema(declared.keyword, subschema, declared.applies, choice)
            }
        }
    }
}

/** A `$ref` that couldn't be followed to a schema, and why */
sealed interface UnresolvedRef {
    val ref: KsonString

    /** [ref] names no schema, for the reason [failure] gives */
    data class MissingTarget(override val ref: KsonString, val failure: RefResolution.Failure) : UnresolvedRef

    /** [ref] leads back to a schema earlier in its chain of `$ref`s */
    data class Cycle(override val ref: KsonString) : UnresolvedRef
}

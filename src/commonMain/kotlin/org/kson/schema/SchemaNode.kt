package org.kson.schema

import org.kson.uri.UriReference
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/**
 * A schema together with what it takes to interpret it: the base URI it inherits, the draft that decides which
 * keywords it has, and the documents its `$ref`s can point into.  Only core creates these, for each document's root
 * and for the subschemas and `$ref` targets reached from one.
 *
 * @param baseUri The base URI that [value] inherits.  For a subschema, this is its parent's [innerBaseUri].  For a
 *   root schema, it is the document's base URI.  [value]'s own `$id` resolves against it.  A value has the same base
 *   URI however it is reached, because the base URI depends only on where the value sits in its document.
 * @param unresolvedRef The `$ref` in [value] that couldn't be followed, if any
 */
@ConsistentCopyVisibility
data class SchemaNode internal constructor(
    val value: KsonValue,
    val baseUri: UriReference,
    internal val draft: SchemaDraft,
    internal val indexedDocuments: IndexedSchemaDocuments,
    val unresolvedRef: UnresolvedRef? = null
) {
    val endsAtUnresolvedRef: Boolean get() = unresolvedRef != null

    /** The base URI inside [value], which its `$ref` resolves against and its subschemas inherit */
    internal val innerBaseUri: UriReference by lazy { baseUriWithin(value, baseUri, draft) }

    /** Returns a node for [child], a subschema or other value inside [value], with [innerBaseUri] as its base URI */
    internal fun enter(child: KsonValue): SchemaNode = SchemaNode(child, innerBaseUri, draft, indexedDocuments)

    /**
     * The direct subschemas of this schema, in the order validation applies their keywords.  Each has its `$ref`s
     * followed (see [Subschema.resolved]).
     */
    val subschemas: List<Subschema> by lazy {
        val schemaObject = value as? KsonObject ?: return@lazy emptyList()
        draft.keywords.flatMap { keyword ->
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
    /** The `$ref`'s value */
    val ref: KsonString

    /** [ref] addresses no schema */
    data class MissingTarget(override val ref: KsonString) : UnresolvedRef

    /** [ref] leads back to a schema earlier in its chain of `$ref`s */
    data class Cycle(override val ref: KsonString) : UnresolvedRef
}

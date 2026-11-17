package org.kson.schema

import org.kson.uri.UriReference
import org.kson.value.navigation.json_pointer.FragmentPointer
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.value.navigation.json_pointer.fragmentPointer
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.walker.KsonValueWalker
import org.kson.walker.TreePointer
import org.kson.walker.nodesAlong

/** A schema document's root [value] and the URI it was retrieved from, which may differ from its `$id` */
data class SchemaDocument(val value: KsonValue, val retrievalUri: UriReference? = null)

/**
 * Indexes the schemas in [root] and [additionalDocuments] by retrieval URI and `$id`, to resolve `$ref`s among them.
 * It fetches no documents: [missingDocumentUris] lists those still needed.
 *
 * @param root the root schema document, which may have no retrieval URI
 * @param additionalDocuments the documents [root] references, directly or through each other
 * @throws IllegalArgumentException if a retrieval URI in [additionalDocuments] is missing, empty or fragment-only
 */
class IndexedSchemaDocuments(
    val root: SchemaDocument,
    additionalDocuments: List<SchemaDocument> = emptyList()
) {
    /** A `$ref`'s value and the base URI it resolves against */
    private data class Reference(val ref: String, val baseUri: UriReference)

    /**
     * The schemas found in the documents, and the `$ref`s they hold
     *
     * @param schemasByUri The schemas a retrieval URI or an `$id` identifies, by that URI
     */
    private class Index(
        val schemasByUri: Map<UriReference, SchemaNode>,
        val schemas: List<SchemaNode>,
        val references: List<Reference>
    ) {
        /** Resolves [ref] within this index */
        fun resolveRef(ref: String, currentBaseUri: UriReference): SchemaNode? {
            val refUri = currentBaseUri.resolve(ref)
            val resourceUri = refUri.withoutFragment()
            val identified = schemasByUri[refUri.withoutEmptyFragment()]
            if (identified != null) return identified

            val pointer = (refUri.fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return null
            return schemasByUri[resourceUri]?.let { resolveJsonPointer(pointer, it) }
        }

        /** The values the [references] lead to that are not indexed */
        fun unindexedReferenceTargets(): List<KsonValue> = references.mapNotNull { reference ->
            resolveRef(reference.ref, reference.baseUri)?.value?.takeIf { schemaOf(it) == null }
        }

        fun schemaOf(value: KsonValue): SchemaNode? = schemas.firstOrNull { it.value === value }

        /** The innermost indexed schema enclosing the last value of [path], which must start at an indexed schema */
        fun enclosingSchemaOf(path: List<KsonValue>): SchemaNode =
            path.dropLast(1).asReversed().firstNotNullOf(::schemaOf)

        /** Returns the schema [pointer] points to within [resource], or null if nothing is there */
        private fun resolveJsonPointer(pointer: JsonPointer, resource: SchemaNode): SchemaNode? {
            val pathToTarget = KsonValueWalker.nodesAlong(resource.value, TreePointer<KsonValue>(pointer))
            if (pathToTarget.size < pointer.tokens.size) return null
            val target = pathToTarget.lastOrNull() ?: resource.value
            schemaOf(target)?.let { return it }

            // extension data gets its base URI from the schemas enclosing it
            return enclosingSchemaOf(listOf(resource.value) + pathToTarget).enter(target)
        }
    }

    /** The meta-schema, the root and the additional documents */
    private val documents: List<SchemaDocument>
    private val index: Index

    /** The base URI [root]'s value is read under: its retrieval URI without a fragment, or else empty */
    val rootBaseUri: UriReference = root.initialBaseUri

    /** The root schema, which is [root]'s value with [rootBaseUri] as its base URI */
    internal val rootSchema: SchemaNode = SchemaNode(root.value, rootBaseUri, this)

    /** The root schema with its chain of `$ref`s followed (see [resolveRefIfPresent]) */
    val resolvedRootSchema: SchemaNode by lazy { resolveRefIfPresent(rootSchema) }

    /** The base URI everything inside [root]'s value is read under */
    val rootSubschemaBaseUri: UriReference = rootSchema.innerBaseUri

    init {
        require(additionalDocuments.all { it.initialBaseUri.toString().isNotEmpty() }) {
            "Each additional schema document needs the URI it was retrieved from"
        }
        val metaSchema = SchemaDocument(KsonDraft7MetaSchema.schemaValue, UriReference.parse(KsonDraft7MetaSchema.ID))
        documents = listOf(metaSchema, root) + additionalDocuments
        index = indexUntilReferencesSettle()
    }

    /** The retrieval URIs of referenced documents that were not supplied */
    val missingDocumentUris: List<UriReference>
        get() = index.references.mapNotNull { reference ->
            val targetUri = reference.baseUri.resolve(reference.ref)
            val documentUri = targetUri.withoutFragment()
            val isIndexed = targetUri in index.schemasByUri || documentUri in index.schemasByUri
            documentUri.takeUnless { it.toString().isEmpty() || isIndexed }
        }.distinct()

    /**
     * Resolves [ref] to the schema with that `$id` or retrieval URI, or else follows its JSON Pointer fragment within
     * the schema the rest of its URI identifies.
     *
     * @param ref The reference string (e.g., "#foo", "#/definitions/address", "other.json#/definitions/address")
     * @param currentBaseUri The current base URI context for resolving relative references
     * @return The schema [ref] points to, or null if none is found, as when [ref]'s fragment is a malformed JSON
     *   Pointer
     */
    fun resolveRef(ref: String, currentBaseUri: UriReference): SchemaNode? = index.resolveRef(ref, currentBaseUri)

    /**
     * Follows the chain of `$ref`s from [schema] and returns the schema at its end, which is [schema] itself if it has
     * no `$ref`.  Each `$ref` resolves against the base URI inside the schema that contains it.  If a `$ref` doesn't
     * resolve, or leads back into the chain, the chain ends at the schema containing it, with that `$ref` as the
     * result's [unresolvedRef][SchemaNode.unresolvedRef].
     */
    internal fun resolveRefIfPresent(schema: SchemaNode): SchemaNode {
        val followed = mutableListOf<KsonValue>()
        var current = schema
        while (true) {
            val ref = (current.value as? KsonObject)?.propertyLookup?.get("\$ref") as? KsonString
                ?: return current
            followed.add(current.value)
            val target = resolveRef(ref.value, current.innerBaseUri)
                ?: return current.copy(unresolvedRef = UnresolvedRef.MissingTarget(ref))
            val leadsBack = followed.any { it === target.value }
            if (leadsBack) return current.copy(unresolvedRef = UnresolvedRef.Cycle(ref))
            current = target
        }
    }

    /** Indexes the [documents], plus each `$ref` target that is not one of their subschemas, such as extension data */
    private fun indexUntilReferencesSettle(): Index {
        var referencedValues = emptyList<KsonValue>()
        while (true) {
            // rebuilt each round: a newly referenced value can enclose earlier ones and change their base URI
            val candidate = buildIndex(referencedValues)
            val unindexedTargets = candidate.unindexedReferenceTargets()
            if (unindexedTargets.isEmpty()) return candidate
            referencedValues += unindexedTargets
        }
    }

    /**
     * Indexes the [documents], and each of the [referencedValues] as if it were a subschema of the innermost schema
     * around it
     */
    private fun buildIndex(referencedValues: List<KsonValue>): Index {
        val builder = IndexBuilder()
        for (document in documents) {
            builder.addSchema(SchemaNode(document.value, document.initialBaseUri, this), isDocumentRoot = true)
        }
        // outermost first, so a value inside another gets the base URI the outer one gives it
        val pathsToReferencedValues = referencedValues.mapNotNull(::pathTo).sortedBy { it.size }
        for (pathToValue in pathsToReferencedValues) {
            val value = pathToValue.last()
            if (builder.index.schemaOf(value) != null) continue
            builder.addSchema(builder.index.enclosingSchemaOf(pathToValue).enter(value))
        }
        return builder.index
    }

    /** Collects schemas into its [index], which holds each one as soon as it is added */
    private class IndexBuilder {
        private val schemasByUri = mutableMapOf<UriReference, SchemaNode>()
        private val schemas = mutableListOf<SchemaNode>()
        private val references = mutableListOf<Reference>()
        val index = Index(schemasByUri, schemas, references)

        /**
         * Adds [schema] and its subschemas, and records their `$ref`s.  [schema] is indexed under the base URI its
         * `$id` sets, if that differs from the one it inherits, and under the one it inherits if [isDocumentRoot].
         */
        fun addSchema(schema: SchemaNode, isDocumentRoot: Boolean = false) {
            val subschemaBaseUri = schema.innerBaseUri

            schemas.add(schema)
            if (isDocumentRoot) schemasByUri[schema.baseUri] = schema
            if (subschemaBaseUri != schema.baseUri) schemasByUri[subschemaBaseUri] = schema
            val schemaObject = schema.value as? KsonObject ?: return
            val ref = schemaObject.propertyLookup["\$ref"] as? KsonString
            if (ref != null) references.add(Reference(ref.value, subschemaBaseUri))
            // keywords beside a `$ref` are ignored, but the schemas they hold can still be referenced
            draft7Keywords.flatMap { it.declaredSubschemas(schemaObject) }.forEach { addSchema(schema.enter(it.value)) }
        }
    }

    /** The values from a document root down to [value], or null if no document holds [value] */
    private fun pathTo(value: KsonValue): List<KsonValue>? =
        documents.firstNotNullOfOrNull { pathWithin(it.value, value) }

    /** The values from [root] down to [target], or null if [target] is not in [root] */
    private fun pathWithin(root: KsonValue, target: KsonValue): List<KsonValue>? {
        if (root === target) return listOf(root)
        val children = when (root) {
            is KsonObject -> root.propertyLookup.values
            is KsonList -> root.elements
            else -> emptyList()
        }
        val pathFromChild = children.firstNotNullOfOrNull { pathWithin(it, target) } ?: return null
        return listOf(root) + pathFromChild
    }
}

/** The base URI this document's root `$id` resolves against: its retrieval URI without a fragment, or else empty */
private val SchemaDocument.initialBaseUri: UriReference
    get() = retrievalUri?.withoutFragment() ?: UriReference.parse("")

/**
 * The base URI everything inside [schema] is read under, [schema] itself being read under [baseUri]: the URI
 * [schema]'s `$id` resolves to against [baseUri], or [baseUri] when it has none or holds a `$ref`.
 */
internal fun baseUriWithin(schema: KsonValue, baseUri: UriReference): UriReference {
    val schemaObject = schema as? KsonObject ?: return baseUri
    val holdsRef = schemaObject.propertyLookup["\$ref"] is KsonString
    val id = schemaObject.propertyLookup["\$id"] as? KsonString
    if (holdsRef || id == null) return baseUri
    return baseUri.resolve(id.value).withoutEmptyFragment()
}

/** This URI without its fragment when that is empty, as `other.json#` identifies the same schema as `other.json` */
private fun UriReference.withoutEmptyFragment(): UriReference = if (fragment == "") withoutFragment() else this

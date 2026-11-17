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
    /**
     * A schema value found while indexing
     *
     * @param baseUri The base URI [value] is read under, before its own `$id` applies
     * @param subschemaBaseUri The base URI everything inside [value] is read under
     */
    private data class IndexedSchema(
        val value: KsonValue,
        val baseUri: UriReference,
        val subschemaBaseUri: UriReference
    )

    /** A `$ref`'s value and the base URI it resolves against */
    private data class Reference(val ref: String, val baseUri: UriReference)

    /**
     * The schemas found in the documents, and the `$ref`s they hold
     *
     * @param schemasByUri The schemas a retrieval URI or an `$id` identifies, by that URI
     */
    private class Index(
        val schemasByUri: Map<UriReference, IndexedSchema>,
        val schemas: List<IndexedSchema>,
        val references: List<Reference>
    ) {
        /** Resolves [ref] within this index */
        fun resolveRef(ref: String, currentBaseUri: UriReference): ResolvedRef? {
            val refUri = currentBaseUri.resolve(ref)
            val resourceUri = refUri.withoutFragment()
            val identified = schemasByUri[refUri.withoutEmptyFragment()]
            if (identified != null) return ResolvedRef(identified.value, identified.baseUri)

            val pointer = (refUri.fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return null
            return schemasByUri[resourceUri]?.let { resolveJsonPointer(pointer, it) }
        }

        /** The values the [references] lead to that are not indexed */
        fun unindexedReferenceTargets(): List<KsonValue> = references.mapNotNull { reference ->
            resolveRef(reference.ref, reference.baseUri)?.resolvedValue?.takeIf { schemaOf(it) == null }
        }

        fun schemaOf(value: KsonValue): IndexedSchema? = schemas.firstOrNull { it.value === value }

        /** The innermost indexed schema enclosing the last value of [path], which must start at an indexed schema */
        fun enclosingSchemaOf(path: List<KsonValue>): IndexedSchema =
            path.dropLast(1).asReversed().firstNotNullOf(::schemaOf)

        /** The value [pointer] addresses within [resource], and the base URI that value is read under */
        private fun resolveJsonPointer(pointer: JsonPointer, resource: IndexedSchema): ResolvedRef? {
            val pathToTarget = KsonValueWalker.nodesAlong(resource.value, TreePointer<KsonValue>(pointer))
            if (pathToTarget.size < pointer.tokens.size) return null
            val target = pathToTarget.lastOrNull() ?: resource.value
            val indexedTarget = schemaOf(target)
            if (indexedTarget != null) return ResolvedRef(target, indexedTarget.baseUri)

            // extension data gets its base URI from the schemas enclosing it
            val enclosingSchema = enclosingSchemaOf(listOf(resource.value) + pathToTarget)
            return ResolvedRef(target, enclosingSchema.subschemaBaseUri)
        }
    }

    /** The meta-schema, the root and the additional documents */
    private val documents: List<SchemaDocument>
    private val index: Index

    /** The base URI [root]'s value is read under: its retrieval URI without a fragment, or else empty */
    val rootBaseUri: UriReference = root.initialBaseUri

    /** The base URI everything inside [root]'s value is read under */
    val rootSubschemaBaseUri: UriReference = baseUriWithin(root.value, rootBaseUri)

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
     * @return The referenced value and the base URI it is read under, or null if not found, as when [ref]'s fragment
     *   is a malformed JSON Pointer
     */
    fun resolveRef(ref: String, currentBaseUri: UriReference): ResolvedRef? = index.resolveRef(ref, currentBaseUri)

    /**
     * Resolves the `$ref` in [value], if any, following a chain of `$ref`s to the schema it ultimately
     * refers to.  Keywords beside a `$ref`, `$id` included, are ignored.  A `$ref` that doesn't resolve or leads back
     * into the chain ends it, and becomes the result's [unresolvedRef][ResolvedRef.unresolvedRef].
     *
     * @param value The schema value that might contain a `$ref`
     * @param currentBaseUri The base URI [value] is read under
     * @return The schema at the end of the chain, and the base URI it is read under
     */
    fun resolveRefIfPresent(value: KsonValue, currentBaseUri: UriReference): ResolvedRef {
        val followed = mutableListOf<KsonValue>()
        var current = ResolvedRef(value, currentBaseUri)
        while (true) {
            val ref = (current.resolvedValue as? KsonObject)?.propertyLookup?.get("\$ref") as? KsonString
                ?: return current
            followed.add(current.resolvedValue)
            val target = resolveRef(ref.value, current.resolvedValueBaseUri)
                ?: return current.copy(unresolvedRef = UnresolvedRef.MissingTarget(ref))
            val leadsBack = followed.any { it === target.resolvedValue }
            if (leadsBack) return current.copy(unresolvedRef = UnresolvedRef.Cycle(ref))
            current = target
        }
    }

    /** Indexes the [documents], plus the reference targets [subschemasOf] doesn't reach, such as extension data */
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

    /** Indexes the [documents], and the [referencedValues] under the base URI of the schemas enclosing them */
    private fun buildIndex(referencedValues: List<KsonValue>): Index {
        val builder = IndexBuilder()
        for (document in documents) {
            builder.addSchema(document.value, document.initialBaseUri, isDocumentRoot = true)
        }
        // outermost first, so a value inside another gets the base URI the outer one gives it
        val pathsToReferencedValues = referencedValues.mapNotNull(::pathTo).sortedBy { it.size }
        for (pathToValue in pathsToReferencedValues) {
            val value = pathToValue.last()
            if (builder.index.schemaOf(value) != null) continue
            val enclosingSchema = builder.index.enclosingSchemaOf(pathToValue)
            builder.addSchema(value, enclosingSchema.subschemaBaseUri)
        }
        return builder.index
    }

    /** Collects schemas into its [index], which holds each one as soon as it is added */
    private class IndexBuilder {
        private val schemasByUri = mutableMapOf<UriReference, IndexedSchema>()
        private val schemas = mutableListOf<IndexedSchema>()
        private val references = mutableListOf<Reference>()
        val index = Index(schemasByUri, schemas, references)

        /**
         * Adds [value] and its subschemas, and records their `$ref`s.  [value] is keyed by any new base URI its `$id`
         * sets, and by [enclosingBaseUri] if [isDocumentRoot].
         *
         * @param enclosingBaseUri The base URI [value] is read under: its document's initial base URI if
         *   [isDocumentRoot]
         */
        fun addSchema(
            value: KsonValue,
            enclosingBaseUri: UriReference,
            isDocumentRoot: Boolean = false
        ) {
            val subschemaBaseUri = baseUriWithin(value, enclosingBaseUri)
            val setsANewBaseUri = subschemaBaseUri != enclosingBaseUri
            val schema = IndexedSchema(value, enclosingBaseUri, subschemaBaseUri)

            schemas.add(schema)
            if (isDocumentRoot) schemasByUri[enclosingBaseUri] = schema
            if (setsANewBaseUri) schemasByUri[subschemaBaseUri] = schema
            val schemaObject = value as? KsonObject
            val ref = schemaObject?.propertyLookup?.get("\$ref") as? KsonString
            if (ref != null) references.add(Reference(ref.value, enclosingBaseUri))
            // keywords beside a `$ref` are ignored, but the schemas they hold can still be referenced
            schemaObject?.propertyLookup?.forEach { (keyword, keywordValue) ->
                subschemasOf(keyword, keywordValue).forEach { addSchema(it, subschemaBaseUri) }
            }
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
fun baseUriWithin(schema: KsonValue, baseUri: UriReference): UriReference {
    val schemaObject = schema as? KsonObject ?: return baseUri
    val holdsRef = schemaObject.propertyLookup["\$ref"] is KsonString
    val id = schemaObject.propertyLookup["\$id"] as? KsonString
    if (holdsRef || id == null) return baseUri
    return baseUri.resolve(id.value).withoutEmptyFragment()
}

/** The subschemas [keywordValue] holds, if [keyword] is one of Draft 7's subschema keywords */
private fun subschemasOf(keyword: String, keywordValue: KsonValue): List<KsonValue> = when (keyword) {
    "properties", "patternProperties", "definitions" ->
        (keywordValue as? KsonObject)?.propertyLookup?.values?.toList().orEmpty()
    // a dependency is either a schema or a list of required property names
    "dependencies" ->
        (keywordValue as? KsonObject)?.propertyLookup?.values?.filterNot { it is KsonList }.orEmpty()
    "allOf", "anyOf", "oneOf" ->
        (keywordValue as? KsonList)?.elements.orEmpty()
    "items" ->
        if (keywordValue is KsonList) keywordValue.elements else listOf(keywordValue)
    "additionalItems", "additionalProperties", "contains", "propertyNames", "not", "if", "then", "else" ->
        listOf(keywordValue)
    else -> emptyList()
}

/** This URI without its fragment when that is empty, as `other.json#` identifies the same schema as `other.json` */
private fun UriReference.withoutEmptyFragment(): UriReference = if (fragment == "") withoutFragment() else this

/**
 * A schema node resolved during navigation, carrying the context of how it was found.
 *
 * @param resolvedValue The schema value at this location
 * @param resolvedValueBaseUri The base URI [resolvedValue] is read under, before its own `$id` applies
 * @param unresolvedRef The `$ref` [resolvedValue] holds that couldn't be followed, if any
 */
data class ResolvedRef(
    val resolvedValue: KsonValue,
    val resolvedValueBaseUri: UriReference,
    val unresolvedRef: UnresolvedRef? = null
) {
    /** True when [resolvedValue] holds an [unresolvedRef] */
    val endsAtUnresolvedRef: Boolean get() = unresolvedRef != null
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

package org.kson.schema

import org.kson.uri.UriReference
import org.kson.value.navigation.json_pointer.FragmentPointer
import org.kson.value.navigation.json_pointer.fragmentPointer
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/** A schema document's root [value] and the URI it was retrieved from, which may differ from its `$id` */
data class SchemaDocument(val value: KsonValue, val retrievalUri: UriReference? = null)

/**
 * Indexes the schemas in [root] and [additionalDocuments] by retrieval URI and `$id`, to resolve `$ref`s among them.
 * It fetches no documents: [missingDocumentUris] lists those still needed.
 *
 * @param root the root schema document, which may have no retrieval URI
 * @param additionalDocuments the documents [root] references, directly or through each other
 * @param draft the draft used to interpret the documents, with its meta-schemas indexed alongside them
 * @throws IllegalArgumentException if a retrieval URI in [additionalDocuments] is missing, empty or fragment-only
 */
class IndexedSchemaDocuments internal constructor(
    val root: SchemaDocument,
    additionalDocuments: List<SchemaDocument>,
    private val draft: SchemaDraft
) {
    constructor(root: SchemaDocument, additionalDocuments: List<SchemaDocument> = emptyList()) :
        this(root, additionalDocuments, SchemaDraft.DRAFT_7)

    /** A `$ref`'s value and the base URI it resolves against */
    private data class Reference(val ref: String, val baseUri: UriReference)

    /**
     * The location of a value, given by the index of its [document] in [documents] and the JSON Pointer [path] from
     * that document's root
     */
    private data class Place(val document: Int, val path: List<String>) {
        fun below(tokens: List<String>) = Place(document, path + tokens)
    }

    /**
     * The schemas found in the documents, and the `$ref`s they hold
     *
     * @param placesByUri The place of each schema that a retrieval URI or an `$id` identifies, keyed by that URI
     */
    private inner class Index(
        val placesByUri: Map<UriReference, Place>,
        val schemasByPlace: Map<Place, SchemaNode>,
        val references: List<Reference>
    ) {
        /** Resolves [ref] within this index */
        fun resolveRef(ref: String, currentBaseUri: UriReference): SchemaNode? {
            val place = placeOf(ref, currentBaseUri) ?: return null
            // an unindexed value, such as extension data, gets the base URI inside the innermost schema around it
            return schemasByPlace[place] ?: enclosingSchemaOf(place).enter(checkNotNull(valueAt(place)))
        }

        /**
         * Resolves [ref] against [currentBaseUri] and returns the place of its target, or null if there is none.
         * The target is the schema whose `$id` or retrieval URI is the resolved URI.  Failing that, it is the value
         * that the URI's JSON Pointer fragment points to, within the schema the rest of the URI identifies.
         */
        fun placeOf(ref: String, currentBaseUri: UriReference): Place? {
            val refUri = currentBaseUri.resolve(ref)
            placesByUri[refUri.withoutEmptyFragment()]?.let { return it }
            val pointer = (refUri.fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return null
            val place = placesByUri[refUri.withoutFragment()]?.below(pointer.tokens) ?: return null
            return place.takeIf { valueAt(it) != null }
        }

        /** Returns the places that [references] point to but that hold no indexed schema */
        fun unindexedReferenceTargets(): List<Place> = references.mapNotNull { reference ->
            placeOf(reference.ref, reference.baseUri)?.takeIf { it !in schemasByPlace }
        }

        /** The innermost indexed schema enclosing [place] */
        fun enclosingSchemaOf(place: Place): SchemaNode = (place.path.size - 1 downTo 0).firstNotNullOf { length ->
            schemasByPlace[Place(place.document, place.path.subList(0, length))]
        }
    }

    /** The meta-schemas, the root and the additional documents */
    private val documents: List<SchemaDocument>
    private val index: Index

    /** The base URI [root]'s value is read under: its retrieval URI without a fragment, or else empty */
    val rootBaseUri: UriReference = root.initialBaseUri

    /** The root schema, which is [root]'s value with [rootBaseUri] as its base URI */
    internal val rootSchema: SchemaNode = documentSchema(root)

    /** The root schema with its chain of `$ref`s followed (see [resolveRefIfPresent]) */
    val resolvedRootSchema: SchemaNode by lazy { resolveRefIfPresent(rootSchema) }

    /** The base URI everything inside [root]'s value is read under */
    val rootSubschemaBaseUri: UriReference = rootSchema.innerBaseUri

    init {
        require(additionalDocuments.all { it.initialBaseUri.toString().isNotEmpty() }) {
            "Each additional schema document needs the URI it was retrieved from"
        }
        documents = draft.metaSchemas() + root + additionalDocuments
        index = indexUntilReferencesSettle()
    }

    /** The retrieval URIs of referenced documents that were not supplied */
    val missingDocumentUris: List<UriReference>
        get() = index.references.mapNotNull { reference ->
            val targetUri = reference.baseUri.resolve(reference.ref)
            val documentUri = targetUri.withoutFragment()
            val isIndexed = targetUri in index.placesByUri || documentUri in index.placesByUri
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
        var referencedPlaces = emptyList<Place>()
        while (true) {
            // rebuilt each round: a newly referenced value can enclose earlier ones and change their base URI
            val candidate = buildIndex(referencedPlaces)
            val unindexedTargets = candidate.unindexedReferenceTargets()
            if (unindexedTargets.isEmpty()) return candidate
            referencedPlaces += unindexedTargets
        }
    }

    /**
     * Indexes the [documents], and the value at each of [referencedPlaces] as if it were a subschema of the innermost
     * schema around it
     */
    private fun buildIndex(referencedPlaces: List<Place>): Index {
        val builder = IndexBuilder()
        documents.forEachIndexed { document, schemaDocument ->
            builder.addSchema(documentSchema(schemaDocument), Place(document, emptyList()), isDocumentRoot = true)
        }
        // outermost first, so a value inside another gets the base URI the outer one gives it
        for (place in referencedPlaces.sortedBy { it.path.size }) {
            if (place in builder.index.schemasByPlace) continue
            builder.addSchema(builder.index.enclosingSchemaOf(place).enter(checkNotNull(valueAt(place))), place)
        }
        return builder.index
    }

    /** Collects schemas into its [index], which holds each one as soon as it is added */
    private inner class IndexBuilder {
        private val placesByUri = mutableMapOf<UriReference, Place>()
        private val schemasByPlace = mutableMapOf<Place, SchemaNode>()
        private val references = mutableListOf<Reference>()
        val index = Index(placesByUri, schemasByPlace, references)

        /**
         * Adds [schema], which is at [place], and its subschemas, and records their `$ref`s.  [schema] is indexed under
         * the base URI its `$id` sets, if that differs from the one it inherits, and under the one it inherits if
         * [isDocumentRoot].
         */
        fun addSchema(schema: SchemaNode, place: Place, isDocumentRoot: Boolean = false) {
            val subschemaBaseUri = schema.innerBaseUri

            schemasByPlace[place] = schema
            if (isDocumentRoot) placesByUri[schema.baseUri] = place
            if (subschemaBaseUri != schema.baseUri) placesByUri[subschemaBaseUri] = place
            val schemaObject = schema.value as? KsonObject ?: return
            val ref = schemaObject.propertyLookup["\$ref"] as? KsonString
            if (ref != null) references.add(Reference(ref.value, subschemaBaseUri))
            // a `$ref` may hide the keywords beside it, but the schemas they hold can still be referenced
            schema.draft.keywords.flatMap { it.declaredSubschemas(schemaObject) }.forEach { declared ->
                addSchema(schema.enter(declared.value), place.below(declared.path))
            }
        }
    }

    private fun documentSchema(document: SchemaDocument): SchemaNode =
        SchemaNode(document.value, document.initialBaseUri, draft, this)

    /** Returns the value at [place], or null if there is none.  Each token names an object property or a list index. */
    private fun valueAt(place: Place): KsonValue? =
        place.path.fold<String, KsonValue?>(documents[place.document].value) { value, token ->
            when (value) {
                is KsonObject -> value.propertyLookup[token]
                is KsonList -> token.toIntOrNull()?.let { value.elements.getOrNull(it) }
                else -> null
            }
        }
}

/** The base URI this document's root `$id` resolves against: its retrieval URI without a fragment, or else empty */
private val SchemaDocument.initialBaseUri: UriReference
    get() = retrievalUri?.withoutFragment() ?: UriReference.parse("")

/**
 * Returns the base URI inside [schema], given [baseUri], the one it inherits.  That is [schema]'s `$id` resolved
 * against [baseUri], or [baseUri] itself when [schema] has no string `$id`, or when a `$ref` hides it in [draft] (see
 * [hasHidingRef]).
 */
internal fun baseUriWithin(schema: KsonValue, baseUri: UriReference, draft: SchemaDraft): UriReference {
    val schemaObject = schema as? KsonObject ?: return baseUri
    val id = schemaObject.propertyLookup["\$id"] as? KsonString
    if (id == null || hasHidingRef(schemaObject, draft)) return baseUri
    return baseUri.resolve(id.value).withoutEmptyFragment()
}

/** This URI without its fragment when that is empty, as `other.json#` identifies the same schema as `other.json` */
private fun UriReference.withoutEmptyFragment(): UriReference = if (fragment == "") withoutFragment() else this

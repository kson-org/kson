package org.kson.schema

import org.kson.parser.messages.Message
import org.kson.uri.UriReference
import org.kson.value.navigation.json_pointer.FragmentPointer
import org.kson.value.navigation.json_pointer.JsonPointer
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
 * @throws IllegalArgumentException if a retrieval URI in [additionalDocuments] is missing, empty or fragment-only
 */
class IndexedSchemaDocuments(
    val root: SchemaDocument,
    additionalDocuments: List<SchemaDocument> = emptyList()
) {
    /** A `$ref`'s value and the base URI it resolves against */
    private data class Reference(val ref: String, val baseUri: UriReference) {
        val uri: UriReference = baseUri.resolve(ref)
    }

    /**
     * The location of a value: the index of its document in [documents], and the JSON Pointer tokens from that
     * document's root to the value
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
        fun resolveRef(ref: String, currentBaseUri: UriReference): RefResolution {
            val refUri = currentBaseUri.resolve(ref)
            val identified = placeIdentifiedBy(refUri)
            if (identified != null) return RefResolution.Found(schemasByPlace.getValue(identified))

            val pointer = when (val fragmentPointer = refUri.fragmentPointer()) {
                is FragmentPointer.Valid -> fragmentPointer.pointer
                is FragmentPointer.Malformed -> return RefResolution.MalformedPointer(fragmentPointer.message)
                null -> return RefResolution.NotFound
            }
            val target = pointerTarget(refUri.withoutFragment(), pointer) ?: return RefResolution.NotFound
            return RefResolution.Found(schemaAt(target))
        }

        /** The places of the values the [references] lead to that are not indexed */
        fun unindexedTargetsOf(references: List<Reference>): List<Place> = references.mapNotNull { reference ->
            if (placeIdentifiedBy(reference.uri) != null) return@mapNotNull null
            val pointer = (reference.uri.fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return@mapNotNull null
            pointerTarget(reference.uri.withoutFragment(), pointer)?.takeIf { it !in schemasByPlace }
        }

        /** The innermost indexed schema enclosing [place] */
        fun enclosingSchemaOf(place: Place): SchemaNode = (place.path.size - 1 downTo 0).firstNotNullOf { length ->
            schemasByPlace[Place(place.document, place.path.subList(0, length))]
        }

        private fun placeIdentifiedBy(uri: UriReference): Place? = placesByUri[uri.withoutEmptyFragment()]

        /** The place [pointer] names within the schema [resourceUri] identifies, or null if no value is there */
        private fun pointerTarget(resourceUri: UriReference, pointer: JsonPointer): Place? {
            val place = placesByUri[resourceUri]?.below(pointer.tokens) ?: return null
            return place.takeIf { valueAt(it) != null }
        }

        private fun schemaAt(place: Place): SchemaNode {
            schemasByPlace[place]?.let { return it }

            // a value that is not indexed gets its base URI from the schemas enclosing it
            return enclosingSchemaOf(place).enter(checkNotNull(valueAt(place)))
        }
    }

    /** The meta-schema, the root and the additional documents */
    private val documents: List<SchemaDocument>
    private val index: Index

    /** The base URI [root]'s value is read under: its retrieval URI without a fragment, or else empty */
    val rootBaseUri: UriReference = root.initialBaseUri

    /** The root schema, which is [root]'s value with [rootBaseUri] as its base URI */
    val rootSchema: SchemaNode = SchemaNode(root.value, rootBaseUri, this)

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
            val targetUri = reference.uri
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
     * @return The schema [ref] names with the base URI it is read under, or why [ref] names none
     */
    fun resolveRef(ref: String, currentBaseUri: UriReference): RefResolution = index.resolveRef(ref, currentBaseUri)

    /**
     * Follows the chain of `$ref`s from [schema] and returns the schema at the end of the chain, or [schema] itself if
     * it has no `$ref`.  If a `$ref` doesn't resolve, or leads back into the chain, the chain ends at the schema
     * containing it, with that `$ref` as the result's [unresolvedRef][SchemaNode.unresolvedRef].
     */
    internal fun resolveRefIfPresent(schema: SchemaNode): SchemaNode {
        val followed = mutableListOf<KsonValue>()
        var current = schema
        while (true) {
            val ref = (current.value as? KsonObject)?.propertyLookup?.get($$"$ref") as? KsonString
                ?: return current
            followed.add(current.value)
            val target = when (val resolution = resolveRef(ref.value, current.innerBaseUri)) {
                is RefResolution.Found -> resolution.target
                is RefResolution.Failure ->
                    return current.copy(unresolvedRef = UnresolvedRef.MissingTarget(ref, resolution))
            }
            val leadsBack = followed.any { it === target.value }
            if (leadsBack) return current.copy(unresolvedRef = UnresolvedRef.Cycle(ref))
            current = target
        }
    }

    /** Indexes the [documents], and every value their `$ref`s lead to */
    private fun indexUntilReferencesSettle(): Index {
        val referencedPlaces = mutableListOf<Place>()
        var builder = IndexBuilder(referencedPlaces)
        while (true) {
            val unindexedTargets = builder.index.unindexedTargetsOf(builder.takeUncheckedReferences())
            if (unindexedTargets.isEmpty()) return builder.index
            // Values go in shallowest first, because a value takes its base URI from the values enclosing it and the
            // schema added last wins a shared `$id`.  A value shallower than an earlier one forces a rebuild.
            val deepestReferenced = referencedPlaces.maxOfOrNull { it.path.size } ?: 0
            referencedPlaces += unindexedTargets
            if (unindexedTargets.all { it.path.size >= deepestReferenced }) {
                builder.addReferencedValues(unindexedTargets)
            } else {
                builder = IndexBuilder(referencedPlaces)
            }
        }
    }

    /** Collects schemas into its [index], which holds each one as soon as it is added */
    private inner class IndexBuilder(referencedPlaces: List<Place>) {
        private val placesByUri = mutableMapOf<UriReference, Place>()
        private val schemasByPlace = mutableMapOf<Place, SchemaNode>()
        private val references = mutableListOf<Reference>()
        val index = Index(placesByUri, schemasByPlace, references)

        private var checkedReferenceCount = 0
        private val identifiedSinceCheck = mutableSetOf<UriReference>()

        init {
            documents.forEachIndexed { document, schemaDocument ->
                val schema = SchemaNode(schemaDocument.value, schemaDocument.initialBaseUri, this@IndexedSchemaDocuments)
                addSchema(schema, Place(document, emptyList()), isDocumentRoot = true)
            }
            addReferencedValues(referencedPlaces)
        }

        fun takeUncheckedReferences(): List<Reference> {
            val checked = references.subList(0, checkedReferenceCount)
            // a reference into a schema identified since the last check may lead to a value not indexed yet
            val pointingIntoNewlyIdentified = if (identifiedSinceCheck.isEmpty()) {
                emptyList()
            } else {
                checked.filter { it.uri.withoutFragment() in identifiedSinceCheck }
            }
            val unchecked = pointingIntoNewlyIdentified + references.drop(checkedReferenceCount)
            checkedReferenceCount = references.size
            identifiedSinceCheck.clear()
            return unchecked
        }

        fun addReferencedValues(referencedPlaces: List<Place>) {
            for (place in referencedPlaces.sortedBy { it.path.size }) {
                if (place in schemasByPlace) continue
                addSchema(index.enclosingSchemaOf(place).enter(checkNotNull(valueAt(place))), place)
            }
        }

        /**
         * Adds [schema] at [place], along with its subschemas, and records their `$ref`s.  [schema] is keyed by its
         * `$id`, and by the base URI it inherits if [isDocumentRoot].
         */
        private fun addSchema(schema: SchemaNode, place: Place, isDocumentRoot: Boolean = false) {
            val keywords = keywordsOf(schema.value)
            val id = keywords[$$"$id"] as? KsonString
            val ref = keywords[$$"$ref"] as? KsonString

            schemasByPlace[place] = schema
            if (isDocumentRoot) identify(schema.baseUri, place)
            if (id != null) identify(schema.innerBaseUri, place)
            if (ref != null) references.add(Reference(ref.value, schema.innerBaseUri))
            // a schema with a string `$ref` has no subschemas, as the keywords beside it don't apply
            val schemaObject = schema.value as? KsonObject
            if (schemaObject == null || ref != null) return
            draft7Keywords.flatMap { it.declaredSubschemas(schemaObject) }.forEach { declared ->
                addSchema(schema.enter(declared.value), place.below(declared.path))
            }
        }

        private fun identify(uri: UriReference, place: Place) {
            placesByUri[uri] = place
            identifiedSinceCheck += uri
        }
    }

    /** Returns the value at [place], or null if there is none. */
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

/** The keywords of [schema] that apply: a string `$ref` alone, as Draft 7 ignores the keywords beside it */
private fun keywordsOf(schema: KsonValue): Map<String, KsonValue> {
    val keywords = (schema as? KsonObject)?.propertyLookup.orEmpty()
    val ref = keywords[$$"$ref"]
    return if (ref is KsonString) mapOf($$"$ref" to ref) else keywords
}

/**
 * The base URI for [schema]'s keywords when [schema] is read under this URI: [schema]'s `$id` resolved against this
 * URI, or this URI when [schema] has no `$id` that applies
 */
internal fun UriReference.withIdOf(schema: KsonValue): UriReference {
    val id = keywordsOf(schema)[$$"$id"] as? KsonString ?: return this
    return resolve(id.value).withoutEmptyFragment()
}

/** This URI without its fragment when that is empty, as `other.json#` identifies the same schema as `other.json` */
private fun UriReference.withoutEmptyFragment(): UriReference = if (fragment == "") withoutFragment() else this

/** The schema a `$ref` names, or why it names none */
sealed interface RefResolution {
    data class Found(val target: SchemaNode) : RefResolution

    /** Why a `$ref` names no schema */
    sealed interface Failure : RefResolution

    /** No schema has the URI the `$ref` names */
    data object NotFound : Failure

    /** The `$ref`'s fragment is a malformed JSON Pointer, as [message] explains */
    data class MalformedPointer(val message: Message) : Failure
}

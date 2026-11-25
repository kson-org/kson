package org.kson.schema

import org.kson.parser.messages.Message
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
 * A schema is a document's root, a subschema under one of Draft 7's subschema keywords, or a value a `$ref` points
 * to.  Only the `$id`s of schemas change base URIs.
 *
 * A URI several schemas share identifies the document retrieved from it, or else the deepest schema with that `$id`.
 * Ties go to the last one, in the order: [root], [additionalDocuments].  The built-in Draft 7 meta-schema yields its
 * URI to any schema that claims it.
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
     * The location of a value: the index of its document in [documents], and the JSON Pointer tokens from that
     * document's root to the value
     */
    private data class Place(val document: Int, val path: List<String>) {
        fun below(tokens: List<String>) = Place(document, path + tokens)
    }

    /** The meta-schema, the root and the additional documents */
    private val documents: List<SchemaDocument>

    /** The root of the last of [root] and [additionalDocuments] retrieved from each URI, by that URI */
    private val documentRootsByUri: Map<UriReference, Place>

    /** The value of each schema found, by its place */
    private val schemas = mutableMapOf<Place, KsonValue>()

    /** The schemas whose `$id` resolves to each URI, by that URI */
    private val schemasById = mutableMapOf<UriReference, MutableSet<Place>>()

    /** Ranks schemas whose `$id`s resolve to the same URI: the URI identifies the highest-ranked one */
    private val idOrder =
        compareBy<Place>({ it.path.size }, { it.document }, { schemas.getValue(it).location.startOffset })

    /** The base URI [root]'s value is read under: its retrieval URI without a fragment, or else empty */
    val rootBaseUri: UriReference = root.initialBaseUri

    /** The base URI everything inside [root]'s value is read under */
    val rootSubschemaBaseUri: UriReference = rootBaseUri.withIdOf(root.value)

    init {
        require(additionalDocuments.all { it.initialBaseUri.toString().isNotEmpty() }) {
            "Each additional schema document needs the URI it was retrieved from"
        }
        val metaSchema = SchemaDocument(KsonDraft7MetaSchema.schemaValue, UriReference.parse(KsonDraft7MetaSchema.ID))
        documents = listOf(metaSchema, root) + additionalDocuments
        // only its `$id` identifies the meta-schema, so any other schema claiming its URI wins
        documentRootsByUri =
            documents.indices.drop(1).associateBy({ documents[it].initialBaseUri }) { Place(it, emptyList()) }
        Indexer().indexDocuments()
    }

    /** The retrieval URIs of referenced documents that were not supplied */
    val missingDocumentUris: List<UriReference>
        get() = schemas.mapNotNull { (place, schema) ->
            val ref = keywordsOf(schema)[$$"$ref"] as? KsonString ?: return@mapNotNull null
            val refUri = baseUriOf(place).resolve(ref.value)
            refUri.withoutFragment().takeIf {
                it.toString().isNotEmpty() && schemaIdentifiedBy(refUri) == null && schemaIdentifiedBy(it) == null
            }
        }.distinct()

    /**
     * Resolves [ref] to the schema with that `$id` or retrieval URI, or else follows its JSON Pointer fragment within
     * the schema the rest of its URI identifies.
     *
     * @param ref The reference string (e.g., "#foo", "#/definitions/address", "other.json#/definitions/address")
     * @param currentBaseUri The current base URI context for resolving relative references
     * @return The schema [ref] names with the base URI it is read under, or why [ref] names none
     */
    fun resolveRef(ref: String, currentBaseUri: UriReference): RefResolution {
        val refUri = currentBaseUri.resolve(ref)
        val target = placeNamedBy(refUri) ?: return when (val fragmentPointer = refUri.fragmentPointer()) {
            is FragmentPointer.Malformed -> RefResolution.MalformedPointer(fragmentPointer.message)
            else -> RefResolution.NotFound
        }
        return RefResolution.Found(ResolvedRef(checkNotNull(valueAt(target)), baseUriOf(target)))
    }

    /**
     * Resolves the `$ref` in [value], if any, following a chain of `$ref`s to the schema it ultimately
     * refers to.  A `$ref` that doesn't resolve or leads back into the chain ends it, and becomes the result's
     * [unresolvedRef][ResolvedRef.unresolvedRef].
     *
     * @param value The schema value that might contain a `$ref`
     * @param currentBaseUri The base URI [value] is read under
     * @return The schema at the end of the chain, and the base URI it is read under
     */
    fun resolveRefIfPresent(value: KsonValue, currentBaseUri: UriReference): ResolvedRef {
        val followed = mutableListOf<KsonValue>()
        var current = ResolvedRef(value, currentBaseUri)
        while (true) {
            val ref = (current.resolvedValue as? KsonObject)?.propertyLookup?.get($$"$ref") as? KsonString
                ?: return current
            followed.add(current.resolvedValue)
            val target = when (val resolution = resolveRef(ref.value, current.resolvedValueBaseUri)) {
                is RefResolution.Found -> resolution.target
                is RefResolution.Failure ->
                    return current.copy(unresolvedRef = UnresolvedRef.MissingTarget(ref, resolution))
            }
            val leadsBack = followed.any { it === target.resolvedValue }
            if (leadsBack) return current.copy(unresolvedRef = UnresolvedRef.Cycle(ref))
            current = target
        }
    }

    /**
     * Returns the place of the schema [uri] identifies, or null if no document was retrieved from [uri] and no schema's
     * `$id` resolves to it
     */
    private fun schemaIdentifiedBy(uri: UriReference): Place? {
        val identifyingUri = uri.withoutEmptyFragment()
        return documentRootsByUri[identifyingUri] ?: schemasById[identifyingUri]?.maxWithOrNull(idOrder)
    }

    /**
     * Returns the place of the value [uri] names, or null if it names none.  That value is the schema [uri] identifies,
     * or else the value that [uri]'s JSON Pointer fragment names within the schema the rest of [uri] identifies.
     */
    private fun placeNamedBy(uri: UriReference): Place? {
        schemaIdentifiedBy(uri)?.let { return it }
        val pointer = (uri.fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return null
        return schemaIdentifiedBy(uri.withoutFragment())?.below(pointer.tokens)?.takeIf { valueAt(it) != null }
    }

    /** Returns the base URI of the value at [place] */
    private fun baseUriOf(place: Place): UriReference =
        enclosingPlaces(place).fold(documents[place.document].initialBaseUri) { baseUri, enclosing ->
            schemas[enclosing]?.let { baseUri.withIdOf(it) } ?: baseUri
        }

    /** Returns the places of the values enclosing the value at [place], outermost first */
    private fun enclosingPlaces(place: Place): List<Place> =
        place.path.indices.map { Place(place.document, place.path.subList(0, it)) }

    /** Returns the value at [place], or null if there is none. */
    private fun valueAt(place: Place): KsonValue? =
        place.path.fold<String, KsonValue?>(documents[place.document].value) { value, token ->
            when (value) {
                is KsonObject -> value.propertyLookup[token]
                is KsonList -> token.toIntOrNull()?.let { value.elements.getOrNull(it) }
                else -> null
            }
        }

    /**
     * Finds the schemas in the [documents].  Reading a schema identifies it by its `$id`, follows its `$ref` and
     * registers its subschemas.
     *
     * A schema is read again when something found later can change its base URI or where its `$ref` leads: an
     * enclosing value turning out to be a schema with an `$id`, or its `$ref`'s resource gaining or losing an `$id`.
     *
     * A value stays a schema once a `$ref` has pointed to it, even if a schema found later redirects that `$ref`.
     */
    private inner class Indexer {
        /** The schemas to read, some of them again */
        private val unread = ArrayDeque<Place>()

        /** The URI each schema's `$id` resolved to when the schema was last read */
        private val idUris = mutableMapOf<Place, UriReference>()

        /** The schemas whose `$ref` has pointed into each resource, by the resource's URI */
        private val referringSchemas = mutableMapOf<UriReference, MutableSet<Place>>()

        /** For each value that has an `$id` but is not a schema yet, the schemas found inside it so far */
        private val awaitingEnclosingSchema = mutableMapOf<Place, MutableList<Place>>()

        fun indexDocuments() {
            documents.indices.forEach { register(Place(it, emptyList())) }
            while (unread.isNotEmpty()) read(unread.removeFirst())
        }

        /** Makes the value at [place] a schema and queues it for reading, unless it is a schema already */
        private fun register(place: Place) {
            if (place in schemas) return
            schemas[place] = checkNotNull(valueAt(place))
            unread += place
            awaitingEnclosingSchema.remove(place)?.let(unread::addAll)
            for (enclosing in enclosingPlaces(place)) {
                if (enclosing !in schemas && keywordsOf(checkNotNull(valueAt(enclosing)))[$$"$id"] is KsonString) {
                    awaitingEnclosingSchema.getOrPut(enclosing, ::mutableListOf) += place
                }
            }
        }

        private fun read(schema: Place) {
            val value = schemas.getValue(schema)
            val keywords = keywordsOf(value)
            val baseUri = baseUriOf(schema)
            if (keywords[$$"$id"] is KsonString) identifyById(schema, baseUri.withIdOf(value))
            (keywords[$$"$ref"] as? KsonString)?.let { follow(schema, baseUri.resolve(it.value)) }
            for ((keyword, keywordValue) in keywords) {
                for (tokens in subschemaTokensOf(keyword, keywordValue)) {
                    register(schema.below(listOf(keyword) + tokens))
                }
            }
        }

        private fun follow(schema: Place, refUri: UriReference) {
            referringSchemas.getOrPut(refUri.withoutFragment(), ::mutableSetOf) += schema
            placeNamedBy(refUri)?.let(::register)
        }

        /**
         * Identifies [schema] by [idUri], the URI its `$id` resolves to under its current base URI.  [schema] stops
         * being identified by the URI its `$id` resolved to before, if that was different.
         */
        private fun identifyById(schema: Place, idUri: UriReference) {
            val previousIdUri = idUris.put(schema, idUri)
            if (previousIdUri == idUri) return
            if (previousIdUri != null) {
                schemasById.getValue(previousIdUri) -= schema
                unread += referringSchemas[previousIdUri].orEmpty()
            }
            schemasById.getOrPut(idUri, ::mutableSetOf) += schema
            unread += referringSchemas[idUri].orEmpty()
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
 * Returns the JSON Pointer tokens from [keywordValue] to each subschema in it, if [keyword] is one of Draft 7's
 * subschema keywords.  The tokens are empty when [keywordValue] is itself the subschema.
 */
private fun subschemaTokensOf(keyword: String, keywordValue: KsonValue): List<List<String>> {
    val properties = (keywordValue as? KsonObject)?.propertyLookup.orEmpty()
    val elements = (keywordValue as? KsonList)?.elements.orEmpty()
    return when (keyword) {
        "properties", "patternProperties", "definitions" -> properties.keys.map { listOf(it) }
        // a dependency is either a schema or a list of required property names
        "dependencies" -> properties.filterValues { it !is KsonList }.keys.map { listOf(it) }
        "allOf", "anyOf", "oneOf" -> elements.indices.map { listOf(it.toString()) }
        "items" -> if (keywordValue is KsonList) elements.indices.map { listOf(it.toString()) } else listOf(emptyList())
        "additionalItems", "additionalProperties", "contains", "propertyNames", "not", "if", "then", "else" ->
            listOf(emptyList())
        else -> emptyList()
    }
}

/**
 * The schema at the end of [path] with the base URI it is read under, or this schema when [path] is empty.  Each value
 * in [path] sits directly inside the one before it, the first directly inside this schema.
 */
fun ResolvedRef.descend(path: List<KsonValue>): ResolvedRef = path.fold(this) { parent, value ->
    ResolvedRef(value, parent.resolvedValueBaseUri.withIdOf(parent.resolvedValue))
}

/**
 * The base URI for [schema]'s keywords when [schema] is read under this URI: [schema]'s `$id` resolved against this
 * URI, or this URI when [schema] has no `$id` that applies
 */
fun UriReference.withIdOf(schema: KsonValue): UriReference {
    val id = keywordsOf(schema)[$$"$id"] as? KsonString ?: return this
    return resolve(id.value).withoutEmptyFragment()
}

/** This URI without its fragment when that is empty, as `other.json#` identifies the same schema as `other.json` */
private fun UriReference.withoutEmptyFragment(): UriReference = if (fragment == "") withoutFragment() else this

/**
 * A schema node resolved during navigation, carrying the context of how it was found.
 *
 * @param resolvedValue The schema value at this location
 * @param resolvedValueBaseUri The base URI [resolvedValue] is read under.  Its own `$id`, if it has one, is
 *   not applied yet: whatever reads its keywords applies it first, with [withIdOf].
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

/** The schema a `$ref` names, or why it names none */
sealed interface RefResolution {
    data class Found(val target: ResolvedRef) : RefResolution

    /** Why a `$ref` names no schema */
    sealed interface Failure : RefResolution

    /** No schema has the URI the `$ref` names */
    data object NotFound : Failure

    /** The `$ref`'s fragment is a malformed JSON Pointer, as [message] explains */
    data class MalformedPointer(val message: Message) : Failure
}

/** A `$ref` that couldn't be followed to a schema, and why */
sealed interface UnresolvedRef {
    /** The `$ref`'s value */
    val ref: KsonString

    /** [ref] names no schema, for the reason [failure] gives */
    data class MissingTarget(override val ref: KsonString, val failure: RefResolution.Failure) : UnresolvedRef

    /** [ref] leads back to a schema earlier in its chain of `$ref`s */
    data class Cycle(override val ref: KsonString) : UnresolvedRef
}

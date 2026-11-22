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
     * @param depth How many values enclose [value] in its document
     */
    private data class IndexedSchema(
        val value: KsonValue,
        val baseUri: UriReference,
        val subschemaBaseUri: UriReference,
        val depth: Int
    )

    /** A `$ref`'s value and the base URI it resolves against */
    private data class Reference(val ref: String, val baseUri: UriReference) {
        val uri: UriReference = baseUri.resolve(ref)
    }

    /**
     * The value a JSON Pointer names within an indexed schema
     *
     * @param path The values from that indexed schema down to the named value
     */
    private class PointerTarget(val path: List<KsonValue>, val depth: Int) {
        val value: KsonValue get() = path.last()
    }

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
        fun resolveRef(ref: String, currentBaseUri: UriReference): RefResolution {
            val refUri = currentBaseUri.resolve(ref)
            val identified = schemaIdentifiedBy(refUri)
            if (identified != null) return RefResolution.Found(ResolvedRef(identified.value, identified.baseUri))

            val pointer = when (val fragmentPointer = refUri.fragmentPointer()) {
                is FragmentPointer.Valid -> fragmentPointer.pointer
                is FragmentPointer.Malformed -> return RefResolution.MalformedPointer(fragmentPointer.message)
                null -> return RefResolution.NotFound
            }
            val target = pointerTarget(refUri.withoutFragment(), pointer) ?: return RefResolution.NotFound
            return RefResolution.Found(resolvedRefTo(target))
        }

        /** The values the [references] lead to that are not indexed */
        fun unindexedTargetsOf(references: List<Reference>): List<PointerTarget> = references.mapNotNull { reference ->
            if (schemaIdentifiedBy(reference.uri) != null) return@mapNotNull null
            val pointer = (reference.uri.fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return@mapNotNull null
            pointerTarget(reference.uri.withoutFragment(), pointer)?.takeIf { schemaOf(it.value) == null }
        }

        fun schemaOf(value: KsonValue): IndexedSchema? = schemas.firstOrNull { it.value === value }

        /** The innermost indexed schema enclosing the last value of [path], which must start at an indexed schema */
        fun enclosingSchemaOf(path: List<KsonValue>): IndexedSchema =
            path.dropLast(1).asReversed().firstNotNullOf(::schemaOf)

        private fun schemaIdentifiedBy(uri: UriReference): IndexedSchema? = schemasByUri[uri.withoutEmptyFragment()]

        /** The value [pointer] names within the schema [resourceUri] identifies */
        private fun pointerTarget(resourceUri: UriReference, pointer: JsonPointer): PointerTarget? {
            val resource = schemasByUri[resourceUri] ?: return null
            val pathToTarget = KsonValueWalker.nodesAlong(resource.value, TreePointer<KsonValue>(pointer))
            if (pathToTarget.size < pointer.tokens.size) return null
            return PointerTarget(listOf(resource.value) + pathToTarget, resource.depth + pathToTarget.size)
        }

        private fun resolvedRefTo(target: PointerTarget): ResolvedRef {
            val indexedTarget = schemaOf(target.value)
            if (indexedTarget != null) return ResolvedRef(target.value, indexedTarget.baseUri)

            // a value that is not indexed gets its base URI from the schemas enclosing it
            return ResolvedRef(target.value, enclosingSchemaOf(target.path).subschemaBaseUri)
        }
    }

    /** The meta-schema, the root and the additional documents */
    private val documents: List<SchemaDocument>
    private val index: Index

    /** The base URI [root]'s value is read under: its retrieval URI without a fragment, or else empty */
    val rootBaseUri: UriReference = root.initialBaseUri

    /** The base URI everything inside [root]'s value is read under */
    val rootSubschemaBaseUri: UriReference

    init {
        require(additionalDocuments.all { it.initialBaseUri.toString().isNotEmpty() }) {
            "Each additional schema document needs the URI it was retrieved from"
        }
        val metaSchema = SchemaDocument(KsonDraft7MetaSchema.schemaValue, UriReference.parse(KsonDraft7MetaSchema.ID))
        documents = listOf(metaSchema, root) + additionalDocuments
        index = indexUntilReferencesSettle()
        rootSubschemaBaseUri = checkNotNull(index.schemaOf(root.value)).subschemaBaseUri
    }

    /** The retrieval URIs of referenced documents that were not supplied */
    val missingDocumentUris: List<UriReference>
        get() = index.references.mapNotNull { reference ->
            val targetUri = reference.uri
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
     * @return The schema [ref] names with the base URI it is read under, or why [ref] names none
     */
    fun resolveRef(ref: String, currentBaseUri: UriReference): RefResolution = index.resolveRef(ref, currentBaseUri)

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

    /** Indexes the [documents], and every value their `$ref`s lead to */
    private fun indexUntilReferencesSettle(): Index {
        val referencedValues = mutableListOf<PointerTarget>()
        var builder = IndexBuilder(documents, referencedValues)
        while (true) {
            val unindexedTargets = builder.index.unindexedTargetsOf(builder.takeUncheckedReferences())
            if (unindexedTargets.isEmpty()) return builder.index
            // Values go in shallowest first, because a value takes its base URI from the values enclosing it and the
            // schema added last wins a shared `$id`.  A value shallower than an earlier one forces a rebuild.
            val deepestReferenced = referencedValues.maxOfOrNull { it.depth } ?: 0
            referencedValues += unindexedTargets
            if (unindexedTargets.all { it.depth >= deepestReferenced }) {
                builder.addReferencedValues(unindexedTargets)
            } else {
                builder = IndexBuilder(documents, referencedValues)
            }
        }
    }

    /** Collects schemas into its [index], which holds each one as soon as it is added */
    private class IndexBuilder(documents: List<SchemaDocument>, referencedValues: List<PointerTarget>) {
        private val schemasByUri = mutableMapOf<UriReference, IndexedSchema>()
        private val schemas = mutableListOf<IndexedSchema>()
        private val references = mutableListOf<Reference>()
        val index = Index(schemasByUri, schemas, references)

        private var checkedReferenceCount = 0
        private val identifiedSinceCheck = mutableSetOf<UriReference>()

        init {
            for (document in documents) {
                addSchema(document.value, document.initialBaseUri, depth = 0, isDocumentRoot = true)
            }
            addReferencedValues(referencedValues)
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

        fun addReferencedValues(referencedValues: List<PointerTarget>) {
            for (referencedValue in referencedValues.sortedBy { it.depth }) {
                if (index.schemaOf(referencedValue.value) != null) continue
                val enclosingSchema = index.enclosingSchemaOf(referencedValue.path)
                addSchema(referencedValue.value, enclosingSchema.subschemaBaseUri, referencedValue.depth)
            }
        }

        /**
         * Adds [value] and its subschemas, and records their `$ref`s.  [value] is keyed by its `$id`, and by
         * [enclosingBaseUri] if [isDocumentRoot].
         *
         * @param enclosingBaseUri The base URI [value] is read under: its document's initial base URI if
         *   [isDocumentRoot]
         */
        private fun addSchema(
            value: KsonValue,
            enclosingBaseUri: UriReference,
            depth: Int,
            isDocumentRoot: Boolean = false
        ) {
            val keywords = keywordsOf(value)
            val id = keywords[$$"$id"] as? KsonString
            val ref = keywords[$$"$ref"] as? KsonString
            val subschemaBaseUri = enclosingBaseUri.withIdOf(value)
            val schema = IndexedSchema(value, enclosingBaseUri, subschemaBaseUri, depth)

            schemas.add(schema)
            if (isDocumentRoot) identify(enclosingBaseUri, schema)
            if (id != null) identify(subschemaBaseUri, schema)
            if (ref != null) references.add(Reference(ref.value, enclosingBaseUri))
            keywords.forEach { (keyword, keywordValue) ->
                subschemasOf(keyword, keywordValue).forEach { subschema ->
                    // a subschema is the keyword's value, or one of the values it holds
                    val subschemaDepth = if (subschema === keywordValue) depth + 1 else depth + 2
                    addSchema(subschema, subschemaBaseUri, subschemaDepth)
                }
            }
        }

        private fun identify(uri: UriReference, schema: IndexedSchema) {
            schemasByUri[uri] = schema
            identifiedSinceCheck += uri
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

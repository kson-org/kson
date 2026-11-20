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

/**
 * Manages the mapping of `$id` values to their corresponding schema nodes for `$ref` resolution.
 *
 * @param schemaRootValue the [KsonValue] root of the schema to build this [SchemaIdLookup] from
 */
class SchemaIdLookup(schemaRootValue: KsonValue) {

    /** The schema each URI identifies, with the base URI that schema is read under */
    private val idMap: Map<UriReference, ResolvedRef>

    /** The URI the root schema was retrieved from.  KSON doesn't know it, so it is the empty reference. */
    private val retrievalUri: UriReference = UriReference.parse("")

    /** The root schema, read under the URI it was retrieved from */
    val root: ResolvedRef = ResolvedRef(schemaRootValue, retrievalUri)

    init {
        /**
         * Collect all `$id` entries from the given schema tree.
         * This pre-processes the entire schema to build a map of fully-qualified IDs.
         */
        idMap = mutableMapOf()

        // preload known meta-schemas, each as if retrieved from its ID
        val draft7MetaSchemaUri = UriReference.parse(KsonDraft7MetaSchema.ID)
        idMap[draft7MetaSchemaUri] = ResolvedRef(KsonDraft7MetaSchema.schemaValue, draft7MetaSchemaUri)

        if (schemaRootValue is KsonObject) {
            // the root is identified by the URI it was retrieved from as well as by its `$id`
            idMap[retrievalUri] = root

            // Walk the schema tree to collect all IDs with fully-qualified URIs
            walkSchemaForIds(schemaRootValue, idMap, retrievalUri)
        }
    }

    /**
     * Resolves a `$ref` reference string to the schema value it refers to: the one whose `$id` is the reference's
     * URI, or else the one its JSON Pointer fragment addresses within the schema the rest of that URI identifies.
     *
     * @param ref The reference string (e.g., "#foo", "#/definitions/address", "bar")
     * @param currentBaseUri The current base URI context for resolving relative references
     * @return The schema [ref] names with the base URI it is read under, or why [ref] names none
     */
    fun resolveRef(ref: String, currentBaseUri: UriReference): RefResolution {
        val refUri = currentBaseUri.resolve(ref)
        val resourceUri = refUri.withoutFragment()
        val identified = idMap[refUri.withoutEmptyFragment()]
        if (identified != null) return RefResolution.Found(identified)

        val pointer = when (val fragmentPointer = refUri.fragmentPointer()) {
            is FragmentPointer.Valid -> fragmentPointer.pointer
            is FragmentPointer.Malformed -> return RefResolution.MalformedPointer(fragmentPointer.message)
            null -> return RefResolution.NotFound
        }
        val target = idMap[resourceUri]?.let { resolveJsonPointer(pointer, it) } ?: return RefResolution.NotFound
        return RefResolution.Found(target)
    }


    /**
     * Resolves the `$ref` in [value], if any, following a chain of `$ref`s to the schema it ultimately
     * refers to.  Keywords beside a `$ref`, `$id` included, are ignored.  A `$ref` that doesn't resolve,
     * or leads back into the chain, ends it at the schema holding it, marked
     * [endsAtUnresolvedRef][ResolvedRef.endsAtUnresolvedRef].
     *
     * @param value The schema value that might contain a `$ref`
     * @param currentBaseUri The base URI [value] is read under
     * @return The schema at the end of the chain, and the base URI it is read under
     */
    fun resolveRefIfPresent(value: KsonValue, currentBaseUri: UriReference): ResolvedRef {
        val followed = mutableListOf<ResolvedRef>()
        var current = ResolvedRef(value, currentBaseUri)
        while (true) {
            val refValue = (current.resolvedValue as? KsonObject)?.propertyLookup?.get("\$ref") as? KsonString
                ?: return current
            followed.add(current)
            val target = (resolveRef(refValue.value, current.resolvedValueBaseUri) as? RefResolution.Found)?.target
                ?: return current.copy(endsAtUnresolvedRef = true)
            val leadsBack = followed.any {
                it.resolvedValue === target.resolvedValue && it.resolvedValueBaseUri == target.resolvedValueBaseUri
            }
            if (leadsBack) return current.copy(endsAtUnresolvedRef = true)
            current = target
        }
    }

    companion object {
        /**
         * Recursively walks a schema value to collect all `$id` entries with fully-qualified URIs.
         *
         * @param schemaValue The current schema node to examine
         * @param idMap The map to collect each schema with an `$id` into, by its fully-qualified `$id`
         * @param currentBaseUri The base URI [schemaValue] is read under
         */
        private fun walkSchemaForIds(
            schemaValue: KsonValue,
            idMap: MutableMap<UriReference, ResolvedRef>,
            currentBaseUri: UriReference
        ) {
            when (schemaValue) {
                is KsonObject -> {
                    val contextBaseUri = currentBaseUri.withIdOf(schemaValue)
                    if (schemaValue.propertyLookup["\$id"] is KsonString) {
                        idMap[contextBaseUri] = ResolvedRef(schemaValue, currentBaseUri)
                    }

                    // Recursively walk all property values with the updated context
                    schemaValue.propertyMap.values.forEach { propertyValue ->
                        walkSchemaForIds(propertyValue.propValue, idMap, contextBaseUri)
                    }
                }

                is KsonList -> {
                    // Recursively walk all list elements
                    schemaValue.elements.forEach { element ->
                        walkSchemaForIds(element, idMap, currentBaseUri)
                    }
                }

                else -> {
                    /** no-op, only [KsonObject] and [KsonValue] have children */
                }
            }
        }
    }
}

/**
 * Resolves a JSON Pointer path within a schema.
 *
 * @param pointer The JSON Pointer to follow (e.g., "/definitions/address")
 * @param resource The schema to traverse, with the base URI it is read under
 * @return The [KsonValue] at the pointer location with the base URI it is read under, or null if not found
 */
private fun resolveJsonPointer(pointer: JsonPointer, resource: ResolvedRef): ResolvedRef? {
    // a $ref fragment addresses the schema's value tree
    val nodes = KsonValueWalker.nodesAlong(resource.resolvedValue, TreePointer<KsonValue>(pointer))
    if (nodes.size < pointer.tokens.size) return null
    return resource.descend(nodes)
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
 * URI, or this URI when [schema] has no `$id`
 */
private fun UriReference.withIdOf(schema: KsonValue): UriReference {
    val id = (schema as? KsonObject)?.propertyLookup["\$id"] as? KsonString ?: return this
    return resolve(id.value).withoutEmptyFragment()
}

/** This URI without its fragment when that is empty, as `other.json#` identifies the same schema as `other.json` */
private fun UriReference.withoutEmptyFragment(): UriReference = if (fragment == "") withoutFragment() else this

/**
 * A schema node resolved during navigation, carrying the context of how it was found.
 *
 * @param resolvedValue The schema value at this location
 * @param resolvedValueBaseUri The base URI [resolvedValue] is read under.  Its own `$id`, if it has one, is
 *   not applied yet: whatever reads its keywords applies it first, as [SchemaParser] does.
 * @param endsAtUnresolvedRef True when [resolvedValue] holds a `$ref` that couldn't be followed, as its target is
 *   missing or leads back into the chain (see [SchemaIdLookup.resolveRefIfPresent])
 */
data class ResolvedRef(
    val resolvedValue: KsonValue,
    val resolvedValueBaseUri: UriReference,
    val endsAtUnresolvedRef: Boolean = false
)

/** The schema a `$ref` names, or why it names none */
sealed interface RefResolution {
    data class Found(val target: ResolvedRef) : RefResolution

    /** No schema has the URI the `$ref` names */
    data object NotFound : RefResolution

    /** The `$ref`'s fragment is a malformed JSON Pointer, as [message] explains */
    data class MalformedPointer(val message: Message) : RefResolution
}

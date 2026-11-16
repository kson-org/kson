package org.kson.schema

import org.kson.value.navigation.json_pointer.JsonPointer
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
class SchemaIdLookup(val schemaRootValue: KsonValue) {

    private val idMap: Map<String, KsonValue>

    /** The root schema's base URI: its `$id` when present, otherwise the empty string. */
    val rootBaseUri: String =
        ((schemaRootValue as? KsonObject)?.propertyLookup["\$id"] as? KsonString)
            ?.let { UriReference.parse(it.value).withoutEmptyFragment().toString() } ?: ""

    init {
        /**
         * Collect all `$id` entries from the given schema tree.
         * This pre-processes the entire schema to build a map of fully-qualified IDs.
         */
        idMap = mutableMapOf()

        // preload know meta-schemas
        idMap[KsonDraft7MetaSchema.ID] = KsonDraft7MetaSchema.schemaValue

        if (schemaRootValue is KsonObject) {
            // Store the root schema at its baseUri
            idMap[rootBaseUri] = schemaRootValue

            // Walk the schema tree to collect all IDs with fully-qualified URIs
            walkSchemaForIds(schemaRootValue, idMap, rootBaseUri)
        }
    }

    /**
     * Resolves a `$ref` reference string to the schema value it refers to: the one whose `$id` is the reference's
     * URI, or else the one its JSON Pointer fragment addresses within the schema the rest of that URI identifies.
     *
     * @param ref The reference string (e.g., "#foo", "#/definitions/address", "bar")
     * @param currentBaseUri The current base URI context for resolving relative references
     * @return The resolved [KsonValue] representing the referenced schema, or null if not found, as when [ref]'s
     *   fragment is a malformed JSON Pointer
     */
    fun resolveRef(ref: String, currentBaseUri: String): ResolvedRef? {
        val refUri = UriReference.parse(currentBaseUri).resolve(ref)
        val resourceUri = refUri.withoutFragment().toString()
        val identified = idMap[refUri.withoutEmptyFragment().toString()]
        if (identified != null) return ResolvedRef(identified, currentBaseUri)

        val pointer = (refUri.fragmentPointer() as? FragmentPointer.Valid)?.pointer ?: return null
        return idMap[resourceUri]?.let { resolveJsonPointer(pointer, it, resourceUri) }
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
    fun resolveRefIfPresent(value: KsonValue, currentBaseUri: String): ResolvedRef {
        val followed = mutableListOf<ResolvedRef>()
        var current = ResolvedRef(value, currentBaseUri)
        while (true) {
            val refValue = (current.resolvedValue as? KsonObject)?.propertyLookup?.get("\$ref") as? KsonString
                ?: return current
            followed.add(current)
            val target = resolveRef(refValue.value, current.resolvedValueBaseUri)
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
         * @param idMap The map to collect fully-qualified $id entries into
         * @param currentBaseUri The current base URI context for resolving relative URIs
         */
        private fun walkSchemaForIds(
            schemaValue: KsonValue,
            idMap: MutableMap<String, KsonValue>,
            currentBaseUri: String
        ) {
            when (schemaValue) {
                is KsonObject -> {
                    var contextBaseUri = currentBaseUri

                    // Check for $id in this object
                    schemaValue.propertyLookup["\$id"]?.let { idValue ->
                        if (idValue is KsonString) {
                            val idString = idValue.value

                            // Resolve the ID to its fully-qualified form
                            val fullyQualifiedId =
                                UriReference.parse(currentBaseUri).resolve(idString).withoutEmptyFragment()
                            contextBaseUri = fullyQualifiedId.toString()
                            idMap[contextBaseUri] = schemaValue
                        }
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
 * Resolves a JSON Pointer path within a [KsonValue] structure.
 *
 * @param pointer The JSON Pointer to follow (e.g., "/definitions/address")
 * @param ksonValue The [KsonValue] to traverse
 * @param currentBaseUri The base URI [ksonValue]'s keywords are read under, its own `$id` already applied
 * @return The [KsonValue] at the pointer location with its base URI, or null if not found
 */
private fun resolveJsonPointer(pointer: JsonPointer, ksonValue: KsonValue, currentBaseUri: String): ResolvedRef? {
    // a $ref fragment addresses the schema's value tree
    val nodes = KsonValueWalker.nodesAlong(ksonValue, TreePointer<KsonValue>(pointer))
    if (nodes.size < pointer.tokens.size) return null
    val baseUri = nodes.dropLast(1).fold(currentBaseUri) { uri, node ->
        val id = (node as? KsonObject)?.propertyLookup["\$id"] as? KsonString
        if (id == null) uri else UriReference.parse(uri).resolve(id.value).toString()
    }
    return ResolvedRef(nodes.lastOrNull() ?: ksonValue, baseUri)
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
    val resolvedValueBaseUri: String,
    val endsAtUnresolvedRef: Boolean = false
)

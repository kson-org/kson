package org.kson.schema

import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.InvalidJsonPointerException

/**
 * A *pin* is a property a `oneOf` / `anyOf` branch fixes by `const` or `enum` (see [JsonObjectSchema.pinnedProperties]).
 * [Pins] is what a document's pins say about those branches, given as indices into the list.
 */
internal sealed interface Pins {
    /**
     * No branch survives.  [rejected] holds each property all of several branches reject, with every value
     * pinned for it, ordered as the first branch pins them.  It may be empty.
     */
    class None(val rejected: Map<String, Set<KsonValue>>) : Pins

    /** The [branches] the pins pick. */
    class Pinned(val branches: List<Int>) : Pins

    /** The pins pick no branch.  [branches] are those they don't rule out. */
    class Candidates(val branches: List<Int>) : Pins
}

/** What a schema says about a document that may still be half written. */
object SchemaDecisions {
    /**
     * The schema [node] denotes, or `null` when it is no schema or a `$ref` that doesn't resolve.
     * A malformed keyword further inside goes unreported and doesn't constrain.
     *
     * @throws InvalidJsonPointerException on a `$ref` whose JSON Pointer is malformed
     */
    fun compile(node: KsonValue, baseUri: String, idLookup: SchemaIdLookup): JsonSchema? =
        SchemaParser.parseSchemaElement(node, MessageSink(), baseUri, idLookup)

    /**
     * Whether [value] satisfies [schema] as it stands.  This isn't the opposite of [breaks]: a document that doesn't
     * break it may still not be accepted, because it's unfinished.
     */
    fun accepts(schema: JsonSchema, value: KsonValue): Boolean =
        schema.isValid(value, MessageSink(), SourceContext(mode = ValidationMode.FULL))

    /**
     * Whether what [value] has so far breaks [schema].  A missing required property doesn't, and neither
     * does a problem inside [forgiving].
     */
    fun breaks(schema: JsonSchema, value: KsonValue, forgiving: Location? = null): Boolean {
        val sink = MessageSink()
        schema.validate(value, sink, SourceContext(mode = ValidationMode.PARTIAL))
        return sink.loggedMessages().any { logged -> forgiving == null || logged.location !in forgiving }
    }

    /** What the pins in [value] decide among [branches].  Every pin counts. */
    internal fun narrowByAllPins(branches: List<JsonSchema?>, value: KsonValue): Pins =
        narrow(branches, value, onlyDiscriminators = false)

    /** What the pins in [value] decide among [branches].  Only [discriminators] count. */
    internal fun narrowByDiscriminators(branches: List<JsonSchema?>, value: KsonValue): Pins =
        narrow(branches, value, onlyDiscriminators = true)

    private fun narrow(branches: List<JsonSchema?>, value: KsonValue, onlyDiscriminators: Boolean): Pins {
        val document = (value as? KsonObject)?.propertyLookup
        if (document == null || branches.isEmpty()) {
            return Pins.Candidates(branches.indices.toList())
        }
        val allPins = branches.map { (it as? JsonObjectSchema)?.pinnedProperties(document.keys).orEmpty() }
        val pins = if (onlyDiscriminators) discriminators(branches, allPins) else allPins

        val contradicted = pins.map { branchPins ->
            branchPins.filter { (property, values) -> document.getValue(property) !in values }.keys
        }
        val survivors = branches.indices.filter { contradicted[it].isEmpty() }
        if (survivors.isEmpty()) {
            // a lone branch keeps its own errors, which say more
            val rejected =
                if (branches.size > 1) contradicted.reduce { shared, next -> shared intersect next } else emptySet()
            return Pins.None(
                rejected.associateWith { property -> pins.flatMapTo(LinkedHashSet()) { it.getValue(property) } }
            )
        }

        val chosen = survivors.filter { branch ->
            pins[branch].keys.any { property -> survivors.count { property in pins[it] } == 1 }
        }
        return if (chosen.isNotEmpty()) {
            Pins.Pinned(chosen)
        } else {
            Pins.Candidates(survivors)
        }
    }

    /** Keeps the pins that choose a branch: a property several branches pin, or one its branch requires. */
    private fun discriminators(
        branches: List<JsonSchema?>,
        pins: List<Map<String, Set<KsonValue>>>
    ): List<Map<String, Set<KsonValue>>> =
        branches.zip(pins) { branch, branchPins ->
            val required = (branch as? JsonObjectSchema)?.requiredProperties().orEmpty()
            branchPins.filterKeys { property -> property in required || pins.count { property in it } >= 2 }
        }
}

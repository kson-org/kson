package org.kson.schema

import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import org.kson.value.KsonValue

/** What a schema says about a document that may still be half written. */
object SchemaDecisions {
    /**
     * The schema [node] denotes, or `null` when it is no schema or a `$ref` that doesn't resolve.
     * A malformed keyword further inside goes unreported and doesn't constrain.
     *
     * @throws IllegalArgumentException on a `$ref` whose JSON Pointer is malformed
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
}

package org.kson.schema

import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import org.kson.uri.UriReference
import org.kson.value.KsonValue

/**
 * Evaluates the progress of a possibly unfinished [KsonValue] towards the schema [node] denotes.  A [node] that is
 * no schema, or a `$ref` that doesn't resolve, neither accepts nor rejects anything, and a keyword inside [node]
 * that can't be read constrains nothing.
 */
class SchemaProgress(node: KsonValue, baseUri: UriReference, indexedDocuments: IndexedSchemaDocuments) {
    private val schema: JsonSchema? =
        SchemaParser.parseSchemaElement(node, MessageSink(), baseUri, indexedDocuments)

    /**
     * Whether [value] already satisfies the schema.  Not the opposite of [rejects]: an unfinished value may do
     * neither.
     */
    fun accepts(value: KsonValue): Boolean =
        schema?.isValid(value, MessageSink(), SourceContext(mode = ValidationMode.FULL)) ?: false

    /**
     * Whether what [value] has so far fails the schema.  A missing required property doesn't, and neither does
     * a problem inside [forgiving].
     */
    fun rejects(value: KsonValue, forgiving: Location? = null): Boolean {
        val schema = schema ?: return false
        val sink = MessageSink()
        schema.validate(value, sink, SourceContext(mode = ValidationMode.PARTIAL))
        return sink.loggedMessages().any { logged -> forgiving == null || logged.location !in forgiving }
    }
}

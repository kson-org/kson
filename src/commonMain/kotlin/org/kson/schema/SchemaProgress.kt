package org.kson.schema

import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import org.kson.value.KsonValue

/**
 * Evaluates the progress of a possibly unfinished [KsonValue] towards [schema].  If [schema]'s value is not a valid
 * schema, or its `$ref` doesn't resolve, [accepts] and [rejects] are false for every value.  A malformed keyword
 * inside it constrains nothing.
 */
class SchemaProgress(schema: SchemaNode) {
    private val compiled: JsonSchema? = SchemaParser.parseSchemaElement(schema, MessageSink())

    /**
     * Whether [value] already satisfies the schema.  Not the opposite of [rejects]: an unfinished value may do
     * neither.
     */
    fun accepts(value: KsonValue): Boolean =
        compiled?.isValid(value, MessageSink(), SourceContext(mode = ValidationMode.FULL)) ?: false

    /**
     * Whether what [value] has so far fails the schema.  A missing required property doesn't, and neither does
     * a problem inside [forgiving].
     */
    fun rejects(value: KsonValue, forgiving: Location? = null): Boolean {
        val compiled = compiled ?: return false
        val sink = MessageSink()
        compiled.validate(value, sink, SourceContext(mode = ValidationMode.PARTIAL))
        return sink.loggedMessages().any { logged -> forgiving == null || logged.location !in forgiving }
    }
}

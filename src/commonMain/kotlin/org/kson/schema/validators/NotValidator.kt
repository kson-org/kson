package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchema
import org.kson.schema.JsonSchemaValidator
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode

class NotValidator(private val notSchema: JsonSchema) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink, sourceContext: SourceContext) {
        /**
         * Conservative in [ValidationMode.PARTIAL] mode: a half-typed value that partially satisfies [notSchema] may
         * still diverge from it once finished, so we don't yet know the `not` is violated.  Rejecting
         * here would drop branches that are still viable, so we skip and let [ValidationMode.FULL] validation decide.
         */
        if (sourceContext.mode == ValidationMode.PARTIAL) return
        val notMessageSink = MessageSink()
        if (notSchema.isValid(ksonValue, notMessageSink)) {
            messageSink.error(ksonValue.location, MessageType.SCHEMA_NOT_VALIDATION_FAILED.create())
        }
    }
}

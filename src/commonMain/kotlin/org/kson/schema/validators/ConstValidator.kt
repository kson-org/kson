package org.kson.schema.validators

import org.kson.value.KsonValue
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonSchemaValidator
import org.kson.value.EmbedBlock
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonNull
import org.kson.value.KsonNumber
import org.kson.value.KsonObject
import org.kson.value.KsonString

class ConstValidator(private val const: KsonValue) : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        if (ksonValue != const) {
            val requiredValue = when (const) {
                is KsonNull -> "null"
                is KsonBoolean -> const.value.toString()
                is KsonNumber -> const.value.asString
                is KsonString -> const.value
                // TODO ksonValue should be able to serialize to its corresponding KSON
                is KsonObject -> "the schema-specified object"
                is KsonList -> "the schema-specified list"
                is EmbedBlock -> "the schema-specified embed block"
            }
            messageSink.error(ksonValue.location, MessageType.SCHEMA_VALUE_NOT_EQUAL_TO_CONST.create(requiredValue))
        }
    }
}

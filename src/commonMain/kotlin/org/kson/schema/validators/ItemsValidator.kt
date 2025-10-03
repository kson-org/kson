package org.kson.schema.validators

import org.kson.value.KsonList
import org.kson.value.KsonValue
import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.JsonArrayValidator
import org.kson.schema.JsonSchema

class ItemsValidator(private val itemsValidator: LeadingItemsValidator, private val additionalItemsValidator: AdditionalItemsValidator?) : JsonArrayValidator() {

    override fun validateArray(node: KsonList, messageSink: MessageSink) {
        val remainingItems = itemsValidator.validateArray(node, messageSink)
        additionalItemsValidator?.validateArray(remainingItems, node.location, messageSink)
    }
}

sealed interface LeadingItemsValidator {
    fun validateArray(list: KsonList, messageSink: MessageSink): List<KsonValue>
}

data class LeadingItemsTupleValidator(private val schemas: List<JsonSchema>) : LeadingItemsValidator {
    override fun validateArray(list: KsonList, messageSink: MessageSink): List<KsonValue> {
        var index = 0
        for (i in schemas.indices) {
            if (i >= list.elements.size) {
                break
            }
            schemas[i].validate(list.elements[i], messageSink)
            index = i + 1
        }
        return if (index < list.elements.size) {
            list.elements.subList(index, list.elements.size)
        } else {
            emptyList()
        }
    }
}

data class LeadingItemsSchemaValidator(private val jsonSchema: JsonSchema): LeadingItemsValidator {
    override fun validateArray(list: KsonList, messageSink: MessageSink): List<KsonValue> {
        list.elements.forEach {
            jsonSchema.validate(it, messageSink)
        }
        return emptyList()
    }
}

sealed interface AdditionalItemsValidator {
    fun validateArray(remainingItems: List<KsonValue>, location: Location, messageSink: MessageSink)
}
data class AdditionalItemsBooleanValidator(val allowed: Boolean): AdditionalItemsValidator {
    override fun validateArray(remainingItems: List<KsonValue>, location: Location, messageSink: MessageSink) {
        if (!allowed && remainingItems.isNotEmpty()) {
            messageSink.error(location, MessageType.SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED.create())
        }
    }
}
data class AdditionalItemsSchemaValidator(val schema: JsonSchema?): AdditionalItemsValidator {
    override fun validateArray(remainingItems: List<KsonValue>, location: Location, messageSink: MessageSink) {
        if (schema == null) {
            return
        }
        remainingItems.forEach {
            schema.validate(it, messageSink)
        }
    }
}

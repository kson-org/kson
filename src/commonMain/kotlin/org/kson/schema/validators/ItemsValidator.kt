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
        val validatedCount = itemsValidator.validateArray(node, messageSink)
        val remainingItems = if (validatedCount < node.elements.size) {
            node.elements.subList(validatedCount, node.elements.size)
        } else {
            emptyList()
        }
        additionalItemsValidator?.validateArray(remainingItems, node.elements.size, node.location, messageSink)
    }
}

sealed interface LeadingItemsValidator {
    /**
     * Validates leading items and returns the count of items validated (i.e., the tuple length).
     */
    fun validateArray(list: KsonList, messageSink: MessageSink): Int
}

data class LeadingItemsTupleValidator(private val schemas: List<JsonSchema>) : LeadingItemsValidator {
    override fun validateArray(list: KsonList, messageSink: MessageSink): Int {
        for (i in schemas.indices) {
            if (i >= list.elements.size) {
                break
            }
            schemas[i].validate(list.elements[i], messageSink)
        }
        return schemas.size
    }
}

data class LeadingItemsSchemaValidator(private val jsonSchema: JsonSchema): LeadingItemsValidator {
    override fun validateArray(list: KsonList, messageSink: MessageSink): Int {
        list.elements.forEach {
            jsonSchema.validate(it, messageSink)
        }
        return list.elements.size
    }
}

sealed interface AdditionalItemsValidator {
    fun validateArray(remainingItems: List<KsonValue>, actualCount: Int, location: Location, messageSink: MessageSink)
}
data class AdditionalItemsBooleanValidator(val allowed: Boolean, private val tupleLength: Int): AdditionalItemsValidator {
    override fun validateArray(remainingItems: List<KsonValue>, actualCount: Int, location: Location, messageSink: MessageSink) {
        if (!allowed && remainingItems.isNotEmpty()) {
            messageSink.error(location, MessageType.SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED.create(
                tupleLength.toString(),
                actualCount.toString()
            ))
        }
    }
}
data class AdditionalItemsSchemaValidator(val schema: JsonSchema?): AdditionalItemsValidator {
    override fun validateArray(remainingItems: List<KsonValue>, actualCount: Int, location: Location, messageSink: MessageSink) {
        if (schema == null) {
            return
        }
        remainingItems.forEach {
            schema.validate(it, messageSink)
        }
    }
}

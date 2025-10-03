package org.kson.schema

import org.kson.parser.MessageSink
import org.kson.value.*

// schema todo capture file/location info from schema to link back to schema def?
interface JsonSchemaValidator {
    /**
     * Validates that the given [ksonValue] satisfies this [JsonNumberValidator].  Logs any validation errors to the
     * given [messageSink]
     */
    fun validate(ksonValue: KsonValue, messageSink: MessageSink)
}

abstract class JsonNumberValidator : JsonSchemaValidator {
    final override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        if (ksonValue !is KsonNumber) {
            return
        }

        validateNumber(ksonValue, messageSink)
    }

    abstract fun validateNumber(node: KsonNumber, messageSink: MessageSink)
}

abstract class JsonArrayValidator : JsonSchemaValidator {
    final override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        if (ksonValue !is KsonList) {
            return
        }

        validateArray(ksonValue, messageSink)
    }

    abstract fun validateArray(node: KsonList, messageSink: MessageSink)
}

abstract class JsonObjectValidator : JsonSchemaValidator {
    final override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        val ksonObject = if (ksonValue is KsonObject)
            ksonValue
        else if (ksonValue is EmbedBlock) {
            ksonValue.asKsonObject()
        } else {
            return
        }

        validateObject(ksonObject, messageSink)
    }

    abstract fun validateObject(node: KsonObject, messageSink: MessageSink)
}

abstract class JsonStringValidator : JsonSchemaValidator {
    override fun validate(ksonValue: KsonValue, messageSink: MessageSink) {
        if (ksonValue !is KsonString) {
            return
        }

        validateString(ksonValue, messageSink)
    }

    abstract fun validateString(node: KsonString, messageSink: MessageSink)

    /**
     * Count Unicode code points in a string, not UTF-16 code units.
     * This is required by JSON Schema specification.
     */
    protected fun countCodePoints(str: String): Int {
        var count = 0
        var index = 0
        while (index < str.length) {
            val char = str[index]
            index += if (char.isHighSurrogate() && index + 1 < str.length && str[index + 1].isLowSurrogate()) {
                // This is a surrogate pair, count as one code point
                2
            } else {
                // Regular character, count as one code point
                1
            }
            count++
        }
        return count
    }
}

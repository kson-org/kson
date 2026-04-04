package org.kson.schema.validators

import org.kson.value.EmbedBlock
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonNull
import org.kson.value.KsonNumber
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/**
 * Renders a [KsonValue] as a concise, human-readable string for use in error messages.
 * Strings are quoted to distinguish them from keywords and other value types.
 */
internal fun KsonValue.toDisplayString(): String =
    when (this) {
        is KsonNull -> "null"
        is KsonBoolean -> value.toString()
        is KsonNumber -> value.asString
        is KsonString -> "\"${value}\""
        is KsonObject -> "{...}"
        is KsonList -> "[...]"
        is EmbedBlock -> "<<<...>>>"
    }

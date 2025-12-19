package org.kson.validation

import org.kson.parser.MessageSink
import org.kson.value.KsonValue

interface Validator {
    fun validate(ksonValue: KsonValue, messageSink: MessageSink)
}
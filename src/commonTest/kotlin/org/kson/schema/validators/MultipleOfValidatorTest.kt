package org.kson.schema.validators

import org.kson.CoreCompileConfig
import org.kson.KsonCore
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MultipleOfValidatorTest : JsonSchemaTest {

    /**
     * Validates [value] against `{"multipleOf": [multipleOf]}`
     */
    private fun checkMultipleOf(value: String, multipleOf: String, shouldBeValid: Boolean) {
        val schema = """{"multipleOf": $multipleOf}"""
        val jsonSchema = assertValidSchema(schema)
        val result = KsonCore.parseToAst(value, coreCompileConfig = CoreCompileConfig(schemaJson = jsonSchema))
        val messages = result.messages.map { it.message.toString() }

        assertEquals(shouldBeValid, messages.isEmpty(), "$value with multipleOf $multipleOf; messages: $messages")
    }

    @Test
    fun negativeDecimalMultipleIsValid() = checkMultipleOf("-0.3", "0.1", true)

    @Test
    fun negativeCentsAmountIsValid() = checkMultipleOf("-19.99", "0.01", true)

    @Test
    fun positiveDecimalMultipleIsValid() = checkMultipleOf("0.3", "0.1", true)

    @Test
    fun positiveCentsAmountIsValid() = checkMultipleOf("19.99", "0.01", true)

    @Test
    fun negativeExactlyRepresentableMultipleIsValid() = checkMultipleOf("-0.25", "0.25", true)

    @Test
    fun negativeNonMultipleIsInvalid() = checkMultipleOf("-7", "2", false)

    @Test
    fun negativeDecimalNonMultipleIsInvalid() = checkMultipleOf("-0.35", "0.1", false)
}

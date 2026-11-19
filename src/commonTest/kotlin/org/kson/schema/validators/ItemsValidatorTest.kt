package org.kson.schema.validators

import org.kson.KsonCore
import org.kson.parser.Location
import org.kson.schema.JsonSchemaTest
import org.kson.parser.messages.MessageType.SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED
import org.kson.parser.messages.MessageType.SCHEMA_REF_RESOLUTION_FAILED
import org.kson.parser.messages.MessageType.SCHEMA_VALUE_TYPE_MISMATCH
import org.kson.value.KsonList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ItemsValidatorTest : JsonSchemaTest {
    /** A tuple whose first item schema doesn't compile and whose second requires a string */
    private val tupleWithUncompiledFirstItem = $$"""[{ "$ref": "#/missing" }, { "type": "string" }]"""

    private fun itemLocation(listSource: String, index: Int): Location =
        assertIs<KsonList>(KsonCore.parseToAst(listSource).ksonValue).elements[index].location

    @Test
    fun testAdditionalItemsErrorIncludesCounts() {
        val errors = assertKsonSchemaErrors(
            """
                {
                    "tuple": [1, 2, 3, 4, 5]
                }
            """,
            """
                {
                    "properties": {
                        "tuple": {
                            "items": [
                                { "type": "number" },
                                { "type": "number" },
                                { "type": "number" }
                            ],
                            "additionalItems": false
                        }
                    }
                }
            """,
            listOf(SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED)
        )

        assertEquals("Expected at most 3 items, but found 5", errors[0].message.toString())
    }

    @Test
    fun testAdditionalItemsAllowedProducesNoError() {
        assertKsonEnforcesSchema(
            """
                {
                    "tuple": [1, 2, 3]
                }
            """,
            """
                {
                    "properties": {
                        "tuple": {
                            "items": [
                                { "type": "number" },
                                { "type": "number" },
                                { "type": "number" }
                            ],
                            "additionalItems": false
                        }
                    }
                }
            """,
            true
        )
    }

    @Test
    fun testItemSchemaThatDoesNotCompileLeavesTheLaterItemsInPlace() {
        val schema = """{ "items": $tupleWithUncompiledFirstItem }"""
        val schemaErrors = listOf(SCHEMA_REF_RESOLUTION_FAILED)
        assertEquals(emptyList(), validateAgainstSchemaWithErrors("""[1, "x"]""", schema, schemaErrors))

        val secondItemNotAString = """["x", 1]"""
        val messages = validateAgainstSchemaWithErrors(secondItemNotAString, schema, schemaErrors)
        assertEquals(listOf(SCHEMA_VALUE_TYPE_MISMATCH), messages.map { it.message.type })
        assertEquals(itemLocation(secondItemNotAString, 1), messages.single().location)
    }

    @Test
    fun testAdditionalItemsFollowAnItemSchemaThatDoesNotCompile() {
        val schema = """{ "items": $tupleWithUncompiledFirstItem, "additionalItems": false }"""
        val schemaErrors = listOf(SCHEMA_REF_RESOLUTION_FAILED)
        assertEquals(emptyList(), validateAgainstSchemaWithErrors("""[1, "x"]""", schema, schemaErrors))

        val messages = validateAgainstSchemaWithErrors("""[1, "x", 2]""", schema, schemaErrors)
        assertEquals(listOf(SCHEMA_ADDITIONAL_ITEMS_NOT_ALLOWED), messages.map { it.message.type })
        assertEquals("Expected at most 2 items, but found 3", messages.single().message.toString())
    }
}

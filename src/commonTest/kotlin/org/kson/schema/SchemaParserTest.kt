package org.kson.schema

import org.kson.parser.messages.MessageType
import kotlin.test.*
import org.kson.KsonCore

class SchemaParserTest : JsonSchemaTest {

    /**
     * Assertion helper for testing that [source] is partially parsed by the schema parser
     * (produces non-null jsonSchema) but has validation errors listed in [expectedMessageTypes]
     */
    private fun assertSchemaHasValidationErrors(
        source: String,
        expectedMessageTypes: List<MessageType>
    ) {
        val result = KsonCore.parseSchema(source)
        assertEquals(
            expectedMessageTypes,
            result.messages.map { it.message.type },
            "Should have the expected validation errors."
        )
        assertTrue(result.messages.isNotEmpty(), "Should have error messages for validation errors")
    }

    /**
     * Assertion helper for testing that [source] produces a [JsonObjectSchema] with no errors
     */
    private fun assertValidObjectSchema(source: String): JsonObjectSchema {
        val schema = assertValidSchema(source)
        assertTrue(schema is JsonObjectSchema, "Should produce a JsonObjectSchema")
        return schema
    }

    @Test
    fun testParseBooleanSchema() {
        val trueSchema = assertValidSchema("true")
        assertTrue(trueSchema is JsonBooleanSchema)
        assertTrue(trueSchema.valid)

        val falseSchema = assertValidSchema("false")
        assertTrue(falseSchema is JsonBooleanSchema)
        assertFalse(falseSchema.valid)
    }

    @Test
    fun testParseEmptySchema() {
        assertSchemaHasValidationErrors("", listOf(MessageType.SCHEMA_EMPTY_SCHEMA))
    }

    @Test
    fun testParseMinimalObjectSchema() {
        assertValidObjectSchema("{}")
    }

    @Test
    fun testParseSchemaWithTitle() {
        val schema = assertValidObjectSchema("""{"title": "Test Schema"}""")
        assertEquals("Test Schema", schema.title)
    }

    @Test
    fun testParseSchemaWithDescription() {
        val schema = assertValidObjectSchema("""{"description": "A test schema"}""")
        assertEquals("A test schema", schema.description)
    }

    @Test
    fun testParseSchemaWithTypeString() {
        assertValidObjectSchema("""{"type": "string"}""")
    }

    @Test
    fun testParseSchemaWithTypeArray() {
        assertValidObjectSchema("""{"type": ["string", "number"]}""")
    }

    @Test
    fun testParseSchemaWithInvalidTypeArrayEntry() {
        assertSchemaHasValidationErrors(
            """{"type": ["string", 123]}""",
            listOf(MessageType.SCHEMA_TYPE_ARRAY_ENTRY_ERROR)
        )
    }

    @Test
    fun testParseSchemaWithNumericValidators() {
        assertValidObjectSchema("""
            {
                "minimum": 0,
                "maximum": 100,
                "multipleOf": 5,
                "exclusiveMinimum": -1,
                "exclusiveMaximum": 101
            }
        """)
    }

    @Test
    fun testParseSchemaWithInvalidMinimum() {
        assertSchemaHasValidationErrors(
            """{"minimum": "not a number"}""",
            listOf(MessageType.SCHEMA_NUMBER_REQUIRED)
        )
    }

    @Test
    fun testParseSchemaWithStringValidators() {
        assertValidObjectSchema("""
            {
                "minLength": 1,
                "maxLength": 10,
                "pattern": "^[a-z]+$"
            }
        """)
    }

    @Test
    fun testParseSchemaWithArrayValidators() {
        assertValidObjectSchema("""
            {
                "minItems": 1,
                "maxItems": 5,
                "uniqueItems": true,
                "items": {"type": "string"}
            }
        """)
    }

    @Test
    fun testParseSchemaWithObjectValidators() {
        assertValidObjectSchema("""
            {
                "minProperties": 1,
                "maxProperties": 10,
                "required": ["name", "age"],
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "number"}
                }
            }
        """)
    }

    @Test
    fun testParseSchemaWithInvalidRequiredArray() {
        assertSchemaHasValidationErrors(
            """{"required": [123, "name"]}""",
            listOf(MessageType.SCHEMA_STRING_ARRAY_ENTRY_ERROR)
        )
    }

    @Test
    fun testParseSchemaWithIfThenElse() {
        assertValidObjectSchema("""
            {
                "if": {"type": "string"},
                "then": {"minLength": 1},
                "else": {"type": "number"}
            }
        """)
    }

    @Test
    fun testParseSchemaWithDependencies() {
        assertValidObjectSchema("""
            {
                "dependencies": {
                    "name": ["age"],
                    "address": {"type": "object"}
                }
            }
        """)
    }

    @Test
    fun testParseInvalidSchemaType() {
        assertSchemaHasValidationErrors(
            "123",
            listOf(MessageType.SCHEMA_OBJECT_OR_BOOLEAN)
        )
    }

    @Test
    fun testParseSchemaWithIntegerLengths() {
        assertValidObjectSchema("""
            {
                "minLength": 1.0,
                "maxLength": 10.0,
                "minItems": 2.0,
                "maxItems": 5.0
            }
        """)
    }

    @Test
    fun testParseSchemaWithInvalidIntegerLengths() {
        assertSchemaHasValidationErrors(
            """
            {
                "minLength": 1.5,
                "maxLength": "not a number"
            }
            """,
            listOf(MessageType.SCHEMA_INTEGER_REQUIRED)
        )
    }
}

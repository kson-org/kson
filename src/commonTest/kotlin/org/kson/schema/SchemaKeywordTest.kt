package org.kson.schema

import org.kson.KsonCore
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.validators.AnyOfValidator
import org.kson.schema.validators.OneOfValidator
import org.kson.value.KsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SchemaKeywordTest {
    /** A subschema that the index can find by its `$id`, with a malformed `minimum` that compiling reports */
    private val probe = $$"""{ "$id": "urn:example:probe", "minimum": "low" }"""

    /** Compiles the Draft 7 keyword that reads the property [name] of the object schema [schemaSource] */
    private fun compileKeyword(name: String, schemaSource: String): JsonSchemaValidator? {
        val schemaObject = assertIs<KsonObject>(KsonCore.parseToAst(schemaSource).ksonValue)
        val schema = IndexedSchemaDocuments(SchemaDocument(schemaObject)).rootSchema
        val messageSink = MessageSink()
        val keyword = SchemaDraft.DRAFT_7.keywordsByName.getValue(name)
        val subschemas = keyword.declaredSubschemas(schemaObject).map { declared ->
            CompilableSubschema(declared) { SchemaParser.parseSchemaElement(schema.enter(declared.value), messageSink) }
        }
        val context = KeywordContext(schema, messageSink, SameInstanceSchemas.NONE.with(schemaObject))
        return keyword.compile(context, subschemas)
    }

    private fun titleOf(schema: JsonSchema?): String? = (schema as JsonObjectSchema?)?.title

    @Test
    fun everySubschemaAKeywordCompilesIsIndexed() {
        val placements = listOf(
            """"properties": { "p": PROBE }""",
            """"patternProperties": { "^p": PROBE }""",
            """"additionalProperties": PROBE""",
            """"items": PROBE""",
            """"items": [PROBE]""",
            """"items": [], "additionalItems": PROBE""",
            """"contains": PROBE""",
            """"allOf": [PROBE]""",
            """"anyOf": [PROBE]""",
            """"oneOf": [PROBE]""",
            """"not": PROBE""",
            """"if": PROBE""",
            """"if": {}, "then": PROBE""",
            """"if": {}, "else": PROBE""",
            """"dependencies": { "p": PROBE }""",
            """"propertyNames": PROBE""",
            """"definitions": { "d": PROBE }"""
        )
        for (placement in placements) {
            val root = assertNotNull(KsonCore.parseToAst("{ ${placement.replace("PROBE", probe)} }").ksonValue)
            val documents = IndexedSchemaDocuments(SchemaDocument(root))

            val resolution = documents.resolveRef("urn:example:probe", documents.rootSubschemaBaseUri)
            assertIs<RefResolution.Found>(resolution, placement)
            val messageSink = MessageSink()
            SchemaParser.parseSchemaRoot(documents, messageSink)
            assertEquals(
                listOf(MessageType.SCHEMA_NUMBER_REQUIRED),
                messageSink.loggedMessages().map { it.message.type },
                placement
            )
        }
    }

    @Test
    fun anIfThatDoesNotCompileAddsNoValidator() {
        assertNull(compileKeyword("if", """{ "if": 7 }"""))
    }

    @Test
    fun unionBranchesKeepTheirDeclaredIndicesWhenOneDoesNotCompile() {
        val branches = $$"""[{ "$ref": "#/missing" }, { "title": "second" }]"""
        val anyOf = assertIs<AnyOfValidator>(compileKeyword("anyOf", """{ "anyOf": $branches }"""))
        val oneOf = assertIs<OneOfValidator>(compileKeyword("oneOf", """{ "oneOf": $branches }"""))

        assertNull(anyOf.declared[0])
        assertEquals(listOf(null, "second"), anyOf.declared.map(::titleOf))
        assertEquals(listOf("second"), anyOf.anyOf.map(::titleOf))
        assertNull(oneOf.declared[0])
        assertEquals(listOf(null, "second"), oneOf.declared.map(::titleOf))
        assertEquals(listOf("second"), oneOf.oneOf.map(::titleOf))
    }
}

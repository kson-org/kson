package org.kson.schema

import org.kson.KsonCore
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SchemaKeywordTest {
    /** A subschema that the index can find by its `$id`, with a malformed `minimum` that compiling reports */
    private val probe = $$"""{ "$id": "urn:example:probe", "minimum": "low" }"""

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

            assertNotNull(documents.resolveRef("urn:example:probe", documents.rootSubschemaBaseUri), placement)
            val messageSink = MessageSink()
            SchemaParser.parseSchemaRoot(documents, messageSink)
            assertEquals(
                listOf(MessageType.SCHEMA_NUMBER_REQUIRED),
                messageSink.loggedMessages().map { it.message.type },
                placement
            )
        }
    }
}

package org.kson.schema.draft

import org.kson.parser.MessageSink
import org.kson.schema.IndexedSchemaDocuments
import org.kson.schema.JsonSchemaTest
import org.kson.schema.SchemaParser
import org.kson.value.KsonObject
import org.kson.value.KsonString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KsonDraft202012MetaSchemasTest : JsonSchemaTest {
    private val metaSchemaRef = $$"""
        '$schema': 'https://json-schema.org/draft/2020-12/schema'
        '$ref': 'https://json-schema.org/draft/2020-12/schema'
    """

    @Test
    fun eachMetaSchemaIsPublishedAtItsIdAndCompilesWithoutErrors() {
        for (metaSchema in KsonDraft202012MetaSchemas.documents) {
            val id = assertNotNull((metaSchema.value as KsonObject).propertyLookup[$$"$id"] as? KsonString).value
            assertEquals(id, metaSchema.retrievalUri.toString())
            val messageSink = MessageSink()
            assertNotNull(SchemaParser.parseSchemaRoot(IndexedSchemaDocuments(metaSchema), messageSink), id)
            assertEquals(emptyList(), messageSink.loggedMessages(), id)
        }
    }

    @Test
    fun aSchemaUsingEachVocabularyIsValid() {
        assertKsonEnforcesSchema(
            $$"""
                '$id': 'https://example.test/person.json'
                '$comment': 'Describes a person'
                title: Person
                type: object
                properties:
                  name:
                    type: string
                    minLength: 1
                    format: hostname
                    contentMediaType: 'text/plain'
                    .
                  .
                required: [name]
                unevaluatedProperties: false
                '$defs':
                  unused: true
            """,
            metaSchemaRef,
            true
        )
    }

    @Test
    fun aKeywordWithAValueOfTheWrongKindIsInvalid() {
        val schemas = listOf(
            $$"'$defs': 5",
            "allOf: []",
            "type: 5",
            "minLength: -1",
            "title: 5",
            "format: 5",
            "contentMediaType: 5"
        )
        for (schema in schemas) {
            assertKsonEnforcesSchema(schema, metaSchemaRef, false, schema)
        }
    }

    @Test
    fun aVocabularyMetaSchemaCanBeReferencedOnItsOwn() {
        val validationVocabulary = $$"'$ref': 'https://json-schema.org/draft/2020-12/meta/validation'"

        assertKsonEnforcesSchema("minLength: -1", validationVocabulary, false)
        assertKsonEnforcesSchema("title: 5", validationVocabulary, true)
    }

    @Test
    fun aDraft7SchemaCanReferenceTheMetaSchema() {
        assertKsonEnforcesSchema("type: 5", $$"'$ref': 'https://json-schema.org/draft/2020-12/schema'", false)
    }
}

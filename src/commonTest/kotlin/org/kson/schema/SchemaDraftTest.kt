package org.kson.schema

import org.kson.KsonCore
import org.kson.parser.MessageSink
import org.kson.schema.validators.MinimumValidator
import org.kson.uri.UriReference
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchemaDraftTest {
    private fun value(source: String): KsonValue = assertNotNull(KsonCore.parseToAst(source.trimIndent()).ksonValue)

    /** Draft 7 without `maximum`, so a schema compiled in this draft accepts values above its `maximum`. */
    private val withoutMaximum = SchemaDraft(
        refHidesSiblings = true,
        keywords = SchemaDraft.DRAFT_7.keywords.filterNot { "maximum" in it.names },
        metaSchemas = { emptyList() }
    )

    private fun compile(
        root: SchemaDocument,
        additionalDocuments: List<SchemaDocument> = emptyList(),
        draft: SchemaDraft = withoutMaximum
    ): JsonSchema {
        val messageSink = MessageSink()
        val schema = SchemaParser.parseSchemaRoot(IndexedSchemaDocuments(root, additionalDocuments, draft), messageSink)
        assertEquals(emptyList(), messageSink.loggedMessages())
        return assertNotNull(schema)
    }

    private fun accepts(schema: JsonSchema, instance: String): Boolean = schema.isValid(value(instance), MessageSink())

    @Test
    fun nestedSchemasAreReadInTheDraft() {
        val schema = compile(
            SchemaDocument(
                value(
                    """
                        properties:
                          numbers:
                            contains:
                              allOf:
                                - minimum: 5
                                  maximum: 1
                    """
                )
            )
        )

        assertTrue(accepts(schema, "numbers: [7]"))
        assertFalse(accepts(schema, "numbers: [3]"))
    }

    @Test
    fun aSchemaARefLeadsToIsReadInTheDraft() {
        val schema = compile(
            SchemaDocument(
                value(
                    $$"""
                        '$ref': '#/definitions/alias'
                        definitions:
                          alias:
                            '$ref': '#/definitions/target'
                            .
                          target:
                            const: 7
                            maximum: 1
                    """
                )
            )
        )

        assertTrue(accepts(schema, "7"))
        assertFalse(accepts(schema, "8"))
    }

    @Test
    fun suppliedDocumentsAreReadInTheDraft() {
        val root = SchemaDocument(value($$"'$ref': 'defs.json'"), UriReference.parse("https://example.test/root.json"))
        val defs = SchemaDocument(
            value(
                $$"""
                    '$id': 'schemas/number.json'
                    allOf:
                      - minimum: 5
                        maximum: 1
                """
            ),
            UriReference.parse("https://example.test/defs.json")
        )
        val schema = compile(root, listOf(defs))

        assertTrue(accepts(schema, "7"))
        assertFalse(accepts(schema, "3"))
    }

    @Test
    fun theDraftsKeywordsAndMetaSchemasAreWhatIsIndexed() {
        val nested = object : SchemaKeyword(setOf("nested")) {
            override fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> =
                listOfNotNull(schema.propertyLookup["nested"]?.let { DeclaredSubschema(listOf("nested"), it, Applies.InPlace) })

            override fun compile(
                context: KeywordContext,
                subschemas: List<CompilableSubschema>
            ): JsonSchemaValidator? = null
        }
        val metaSchema = SchemaDocument(value("const: 7"), UriReference.parse("urn:example:meta"))
        val draft = SchemaDraft(refHidesSiblings = true, keywords = listOf(nested), metaSchemas = { listOf(metaSchema) })
        val root = SchemaDocument(value($$"nested: { '$id': 'urn:example:nested' }"))

        val inDraft = IndexedSchemaDocuments(root, emptyList(), draft)
        assertIs<RefResolution.Found>(inDraft.resolveRef("urn:example:nested", inDraft.rootSubschemaBaseUri))
        assertIs<RefResolution.Found>(inDraft.resolveRef("urn:example:meta", inDraft.rootSubschemaBaseUri))
        assertEquals(RefResolution.NotFound, inDraft.resolveRef(KsonDraft7MetaSchema.ID, inDraft.rootSubschemaBaseUri))
        assertEquals(listOf("nested"), inDraft.resolvedRootSchema.subschemas.map { it.keyword })

        val inDraft7 = IndexedSchemaDocuments(root)
        assertEquals(RefResolution.NotFound, inDraft7.resolveRef("urn:example:nested", inDraft7.rootSubschemaBaseUri))
        assertEquals(emptyList(), inDraft7.resolvedRootSchema.subschemas)
    }

    @Test
    fun theKeywordsBesideARefApplyUnlessTheDraftSaysTheRefHidesThem() {
        val keepsSiblings = SchemaDraft(
            refHidesSiblings = false,
            keywords = SchemaDraft.DRAFT_7.keywords,
            metaSchemas = { emptyList() }
        )
        val root = SchemaDocument(
            value(
                $$"""
                    '$id': 'https://example.test/root/'
                    '$ref': '#/definitions/number'
                    minimum: 5
                    definitions:
                      number:
                        type: number
                        .
                      unreferenced:
                        '$id': 'urn:example:unreferenced'
                """
            )
        )

        assertFalse(accepts(compile(root, draft = keepsSiblings), "3"))
        assertTrue(accepts(compile(root, draft = SchemaDraft.DRAFT_7), "3"))
        val siblingsKept = IndexedSchemaDocuments(root, emptyList(), keepsSiblings)
        assertIs<RefResolution.Found>(siblingsKept.resolveRef("https://example.test/root/", siblingsKept.rootBaseUri))
        assertIs<RefResolution.Found>(siblingsKept.resolveRef("urn:example:unreferenced", siblingsKept.rootBaseUri))
        val siblingsHidden = IndexedSchemaDocuments(root)
        assertEquals(
            RefResolution.NotFound,
            siblingsHidden.resolveRef("https://example.test/root/", siblingsHidden.rootBaseUri)
        )
        assertEquals(
            RefResolution.NotFound,
            siblingsHidden.resolveRef("urn:example:unreferenced", siblingsHidden.rootBaseUri)
        )
    }

    @Test
    fun aDraftWhoseKeywordsReadOneNameTwiceFails() {
        assertFailsWith<IllegalArgumentException> {
            SchemaDraft(
                refHidesSiblings = true,
                keywords = listOf(NumberKeyword("minimum", ::MinimumValidator), NumberKeyword("minimum", ::MinimumValidator)),
                metaSchemas = { emptyList() }
            )
        }
    }
}

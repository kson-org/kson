package org.kson.schema

import org.kson.KsonCore
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.schema.validators.MinimumValidator
import org.kson.uri.UriReference
import org.kson.validation.SourceContext
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SchemaDraftTest {
    private fun value(source: String): KsonValue = assertNotNull(KsonCore.parseToAst(source.trimIndent()).ksonValue)

    /** Draft 7 without `maximum`, so a schema compiled in this draft accepts values above its `maximum`. */
    private val withoutMaximum = SchemaDraft(
        refHidesSiblings = true,
        keywords = SchemaDraft.DRAFT_7.keywords.filterNot { "maximum" in it.names },
        metaSchemas = { emptyList() }
    )

    /** Draft 7 where a `$ref` keeps the keywords beside it. */
    private val keepsSiblings = SchemaDraft(
        refHidesSiblings = false,
        keywords = SchemaDraft.DRAFT_7.keywords,
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

    private fun validationMessages(schema: JsonSchema, instance: String): List<MessageType> {
        val messageSink = MessageSink()
        schema.validate(value(instance), messageSink, SourceContext())
        return messageSink.loggedMessages().map { it.message.type }
    }

    private fun indexedIn(draft: SchemaDraft, root: KsonValue): IndexedSchemaDocuments =
        IndexedSchemaDocuments(SchemaDocument(root), emptyList(), draft)

    private fun schemaAt(root: KsonValue, draft: SchemaDraft, vararg path: String): SchemaNode {
        val values = path.runningFold(root) { parent, name -> (parent as KsonObject).propertyLookup.getValue(name) }
        return indexedIn(draft, root).rootSchema.descend(values.drop(1))
    }

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
        val metaSchema =
            SchemaDocument(value($$"{ '$id': 'urn:example:meta', const: 7 }"), UriReference.parse("urn:example:meta"))
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
    fun siblingKeepingRefIsFollowedUnderItsOwnId() {
        val root = SchemaDocument(
            value(
                $$"""
                    allOf: [
                      {
                        '$id': 'http://example.test/other.json',
                        '$ref': '#/extension/x',
                        extension: { x: { '$id': 'http://example.test/inner.json' } }
                      }
                    ]
                    extension: { x: { '$id': 'http://example.test/outer.json' } }
                """
            )
        )

        val indexed = IndexedSchemaDocuments(root, emptyList(), keepsSiblings)

        assertIs<RefResolution.Found>(indexed.resolveRef("http://example.test/inner.json", indexed.rootBaseUri))
        assertEquals(RefResolution.NotFound, indexed.resolveRef("http://example.test/outer.json", indexed.rootBaseUri))
    }

    @Test
    fun siblingKeepingRefsMissingDocumentIsReportedUnderItsOwnId() {
        val root = SchemaDocument(
            value(
                $$"""
                    properties:
                      field:
                        '$id': 'http://example.test/nested/'
                        '$ref': 'dep.json'
                """
            ),
            UriReference.parse("file:///root.json")
        )

        val indexed = IndexedSchemaDocuments(root, emptyList(), keepsSiblings)

        assertEquals(listOf(UriReference.parse("http://example.test/nested/dep.json")), indexed.missingDocumentUris)
    }

    @Test
    fun eachRefAlongAChainAppliesTheKeywordsItKeeps() {
        val root = SchemaDocument(
            value(
                $$"""
                    '$ref': '#/definitions/positive'
                    definitions:
                      positive:
                        '$ref': '#/definitions/number'
                        exclusiveMinimum: 0
                        .
                      number:
                        type: number
                """
            )
        )

        val kept = compile(root, draft = keepsSiblings)
        assertTrue(accepts(kept, "3"))
        assertFalse(accepts(kept, "-3"))
        assertFalse(accepts(kept, "'three'"))
        assertTrue(accepts(compile(root, draft = SchemaDraft.DRAFT_7), "-3"))
    }

    @Test
    fun aRefLeadingBackToASchemaThatKeepsItsKeywordsIsACycle() {
        val throughAnother = SchemaDocument(
            value(
                $$"""
                    '$ref': '#/definitions/a'
                    definitions:
                      a:
                        '$ref': '#/definitions/b'
                        minimum: 1
                        .
                      b:
                        '$ref': '#/definitions/a'
                """
            )
        )
        val toItself = SchemaDocument(value($$"{ '$ref': '#', minimum: 1 }"))

        val cycle = MessageType.SCHEMA_REF_CYCLE
        assertEquals(listOf(cycle), validationMessages(compile(throughAnother, draft = keepsSiblings), "5"))
        assertEquals(listOf(cycle), validationMessages(compile(toItself, draft = keepsSiblings), "5"))
        assertEquals(
            listOf(cycle, MessageType.SCHEMA_VALUE_TOO_SMALL),
            validationMessages(compile(toItself, draft = keepsSiblings), "0")
        )
    }

    @Test
    fun aCycleOfRefsThatKeepTheKeywordsBesideThemPinsNothing() {
        val root = SchemaDocument(
            value(
                $$"""
                    properties:
                      kind:
                        '$ref': '#/definitions/a'
                        .
                      .
                    definitions:
                      a:
                        '$ref': '#/definitions/b'
                        .
                      b:
                        '$ref': '#/definitions/a'
                """
            )
        )

        val schema = assertIs<JsonObjectSchema>(compile(root, draft = keepsSiblings))
        assertEquals(emptyMap(), schema.pinnedProperties(setOf("kind")))
    }

    @Test
    fun onlyARefThatHidesTheKeywordsBesideItIsFollowedInPlaceOfItsSchema() {
        val root = value(
            $$"""
                definitions:
                  alias:
                    '$ref': '#/definitions/number'
                    .
                  number:
                    type: number
            """
        )
        fun resolvedAlias(draft: SchemaDraft): KsonValue {
            val alias = schemaAt(root, draft, "definitions", "alias")
            return alias.indexedDocuments.resolveRefIfPresent(alias).value
        }

        val definitions = (root as KsonObject).propertyLookup.getValue("definitions") as KsonObject
        assertSame(definitions.propertyLookup.getValue("alias"), resolvedAlias(keepsSiblings))
        assertSame(definitions.propertyLookup.getValue("number"), resolvedAlias(SchemaDraft.DRAFT_7))
    }

    @Test
    fun theSchemaARefThatKeepsTheKeywordsBesideItNamesIsAmongItsSubschemas() {
        val root = value(
            $$"""
                '$ref': '#/definitions/base'
                properties:
                  sibling: {}
                  .
                definitions:
                  base:
                    properties:
                      inherited: {}
            """
        )
        val base = schemaAt(root, keepsSiblings, "definitions", "base").value

        val kept = indexedIn(keepsSiblings, root).resolvedRootSchema
        assertSame(root, kept.value)
        assertEquals(
            listOf(
                $$"$ref" to Applies.InPlace,
                "definitions" to Applies.NotApplied,
                "properties" to Applies.Property("sibling")
            ),
            kept.subschemas.map { it.keyword to it.applies }
        )
        assertSame(base, kept.subschemas.first().resolved.value)
        assertSame(base, IndexedSchemaDocuments(SchemaDocument(root)).resolvedRootSchema.value)
    }

    @Test
    fun aRefThatKeepsTheKeywordsBesideItIsAmongTheSubschemasOnlyIfItResolves() {
        val root = value(
            $$"{ '$ref': '#', properties: { sibling: {} }, definitions: { other: { '$ref': '#/missing' } } }"
        )

        val toItself = indexedIn(keepsSiblings, root).resolvedRootSchema
        assertEquals(listOf($$"$ref", "definitions", "properties"), toItself.subschemas.map { it.keyword })
        assertSame(root, toItself.subschemas.first().resolved.value)
        assertEquals(emptyList(), schemaAt(root, keepsSiblings, "definitions", "other").subschemas)
    }

    @Test
    fun theSchemaARefBesideOtherKeywordsNamesPinsAndRequiresPropertiesAlongWithThem() {
        val root = SchemaDocument(
            value(
                $$"""
                    '$ref': '#/definitions/cat'
                    required: [name]
                    definitions:
                      cat:
                        required: [kind]
                        properties:
                          kind:
                            const: cat
                """
            )
        )

        val schema = assertIs<JsonObjectSchema>(compile(root, draft = keepsSiblings))
        assertEquals(mapOf("kind" to setOf(value("cat"))), schema.pinnedProperties(setOf("kind")))
        assertEquals(setOf("name", "kind"), schema.requiredProperties())
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

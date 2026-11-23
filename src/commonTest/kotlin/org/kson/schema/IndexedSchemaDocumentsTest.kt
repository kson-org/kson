package org.kson.schema

import org.kson.KsonCore
import org.kson.parser.messages.MessageType
import org.kson.uri.UriReference
import org.kson.value.KsonObject
import org.kson.value.KsonString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for how [IndexedSchemaDocuments] indexes schemas and resolves `$ref`s among them.
 */
class IndexedSchemaDocumentsTest {

    private fun value(source: String): KsonObject = KsonCore.parseToAst(source).ksonValue as KsonObject

    /** Resolves [ref] as [schema]'s root `$ref` would be. */
    private fun resolveRef(schema: String, ref: String): RefResolution {
        val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(value(schema)))
        return indexedDocuments.resolveRef(ref, indexedDocuments.rootBaseUri)
    }

    private fun resolve(schema: String): SchemaNode {
        val root = value(schema)
        val properties = root.propertyLookup.getValue("properties") as KsonObject
        val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(root))
        val field = indexedDocuments.rootSchema.descend(listOf(properties, properties.propertyLookup.getValue("field")))
        return indexedDocuments.resolveRefIfPresent(field)
    }

    /** Resolves [schema]'s property `field`, returning its `title` and base URI. */
    private fun resolveField(schema: String): Pair<String?, String> = titleAndBaseUri(resolve(schema))

    /** Resolves [schema]'s root, read under the URI it was retrieved from, returning its `title` and base URI. */
    private fun resolveRoot(schema: String): Pair<String?, String> {
        val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(value(schema)))
        return titleAndBaseUri(indexedDocuments.resolveRefIfPresent(indexedDocuments.rootSchema))
    }

    private fun titleAndBaseUri(resolved: SchemaNode): Pair<String?, String> {
        val title = (resolved.value as KsonObject).propertyLookup["title"] as? KsonString
        return title?.value to resolved.baseUri.toString()
    }

    /** Whether resolving [schema]'s property `field` ends at a `$ref` it couldn't follow. */
    private fun endsAtUnresolvedRef(schema: String): Boolean = resolve(schema).endsAtUnresolvedRef

    @Test
    fun followsAChainOfRefs() {
        // `first`'s title is ignored because it sits beside a `$ref`
        val schema = $$"""
            properties:
              field:
                '$ref': '#/$defs/first'
                .
              .
            '$defs':
              first:
                '$ref': '#/$defs/second'
                title: first
                .
              second:
                title: second
        """.trimIndent()
        assertEquals("second" to "", resolveField(schema))
        assertFalse(endsAtUnresolvedRef(schema))
    }

    @Test
    fun resolvesEachRefUnderTheBaseUriItsSchemaIsReadUnder() {
        // `first` is read under `outer`'s `$id`, while its own is ignored beside its `$ref`: the result is
        // `outer`'s `second`, read under outer.json rather than first.json
        val schema = $$"""
            properties:
              field:
                '$ref': '#/definitions/outer/definitions/first'
                .
              .
            definitions:
              outer:
                '$id': 'http://example.com/outer.json'
                definitions:
                  first:
                    '$id': 'http://example.com/first.json'
                    '$ref': '#/definitions/second'
                    .
                  second:
                    title: 'outer second'
                    .
                  .
                .
              second:
                title: 'root second'
        """.trimIndent()
        assertEquals("outer second" to "http://example.com/outer.json", resolveField(schema))
    }

    @Test
    fun stopsAtARefLeadingBackIntoTheChain() {
        // `second`'s `$ref` leads back to `first`, which the chain already holds, so the chain ends
        // at `second`
        val schema = $$"""
            properties:
              field:
                '$ref': '#/$defs/first'
                .
              .
            '$defs':
              first:
                '$ref': '#/$defs/second'
                title: first
                .
              second:
                '$ref': '#/$defs/first'
                title: second
        """.trimIndent()
        assertEquals("second" to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun stopsAtARefToNothing() {
        // `first`'s `$ref` names no schema, so the chain ends at `first`
        val schema = $$"""
            properties:
              field:
                '$ref': '#/$defs/first'
                .
              .
            '$defs':
              first:
                '$ref': '#/$defs/missing'
                title: first
        """.trimIndent()
        assertEquals("first" to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun stopsAtARefWithAMalformedPointer() {
        // `~2` is no JSON Pointer escape, so `first`'s `$ref` names no schema and the chain ends at `first`
        val schema = $$"""
            properties:
              field:
                '$ref': '#/$defs/first'
                .
              .
            '$defs':
              first:
                '$ref': '#/$defs/a~2b'
                title: first
        """.trimIndent()
        assertEquals("first" to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun distinguishesNotFoundFromMalformedPointer() {
        // `~2` is not a JSON Pointer escape, so the pointer is malformed even though its document is missing
        val schema = $$"""
            '$defs':
              target:
                title: target
        """.trimIndent()
        val malformed = RefResolution.MalformedPointer(MessageType.JSON_POINTER_INVALID_ESCAPE.create("2"))

        assertIs<RefResolution.Found>(resolveRef(schema, $$"#/$defs/target"))
        assertEquals(RefResolution.NotFound, resolveRef(schema, $$"#/$defs/missing"))
        assertEquals(RefResolution.NotFound, resolveRef(schema, "missing.json"))
        assertEquals(malformed, resolveRef(schema, $$"#/$defs/a~2b"))
        assertEquals(malformed, resolveRef(schema, "missing.json#/a~2b"))
    }

    @Test
    fun resolvesAPercentEncodedUtf8Pointer() {
        val schema = $$"""
            properties:
              field:
                '$ref': '#/$defs/caf%C3%A9'
                .
              .
            '$defs':
              café:
                title: café
        """.trimIndent()
        assertEquals("café" to "", resolveField(schema))
    }

    @Test
    fun resolvesAPointerToAPropertyNamedByDigitsWithALeadingZero() {
        val schema = $$"""
            properties:
              field:
                '$ref': '#/properties/01'
                .
              '01':
                title: '01'
        """.trimIndent()
        assertEquals("01" to "", resolveField(schema))
    }

    @Test
    fun doesNotIndexAValueAgainThroughARefByANonCanonicalIndex() {
        // indexing `x` again through the `$ref` would resolve its `$id` against the root instead of `second/`
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            allOf:
              - '$ref': '#/items/01/properties/x'
                .
            items:
              - {}
              - '$id': 'http://example.com/second/'
                properties:
                  x:
                    '$id': 'x.json'
        """.trimIndent()
        assertIs<RefResolution.Found>(resolveRef(schema, "http://example.com/second/x.json"))
        assertEquals(RefResolution.NotFound, resolveRef(schema, "http://example.com/x.json"))
    }

    @Test
    fun resolvesAPointerWithinTheSchemaItsRefNames() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'other.json#/definitions/target'
                .
              .
            definitions:
              target:
                title: 'root target'
                .
              other:
                '$id': 'other.json'
                definitions:
                  target:
                    title: 'other target'
        """.trimIndent()
        assertEquals("other target" to "other.json", resolveField(schema))
    }

    @Test
    fun readsASchemaFoundByItsIdUnderTheBaseUriItSitsUnder() {
        // `anchored` sits in other.json, so it is read under other.json, not under root.json, which refers to it
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              field:
                '$ref': 'other.json#anchored'
                .
              .
            definitions:
              other:
                '$id': 'other.json'
                definitions:
                  anchored:
                    '$id': '#anchored'
                    title: anchored
        """.trimIndent()
        assertEquals("anchored" to "http://example.com/other.json", resolveField(schema))
    }

    @Test
    fun doesNotResolveAPlainNameFragmentToAnIdWithoutOne() {
        // `#target` names a fragment of this document, which no `$id` declares; the `$id` `target` names another
        // document
        val schema = $$"""
            properties:
              field:
                '$ref': '#target'
                .
              .
            definitions:
              target:
                '$id': target
                title: target
        """.trimIndent()
        assertEquals(null to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun doesNotReadAColonInARelativePathAsAScheme() {
        // against no base, `./a:b` is the relative path `a:b`, while the `$id` `a:b` is a URI with the scheme `a`
        val schema = $$"""
            properties:
              field:
                '$ref': './a:b'
                .
              .
            definitions:
              target:
                '$id': 'a:b'
                title: target
        """.trimIndent()
        assertEquals(null to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun doesNotResolveARefToAnIdUnderDefs() {
        // Draft 7 has no `$defs`, so `target` is data, whose `$id` identifies no schema
        val schema = $$"""
            properties:
              field:
                '$ref': 'http://example.com/target.json'
                .
              .
            '$defs':
              target:
                '$id': 'http://example.com/target.json'
                title: target
        """.trimIndent()
        assertEquals(null to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun doesNotResolveARefToAnIdBesideARef() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'http://example.com/target.json'
                .
              .
            definitions:
              target:
                '$id': 'http://example.com/target.json'
                '$ref': '#/definitions/other'
                .
              other:
                title: other
        """.trimIndent()
        assertEquals(null to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun doesNotLetAnIdBesideARefReplaceTheSchemaWithThatId() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'http://example.com/target.json'
                .
              .
            definitions:
              target:
                '$id': 'http://example.com/target.json'
                title: target
                .
              impostor:
                '$id': 'http://example.com/target.json'
                '$ref': '#/definitions/other'
                .
              other:
                title: other
        """.trimIndent()
        assertEquals("target" to "", resolveField(schema))
    }

    @Test
    fun resolvesAnIdSharedByReferencedValuesToTheDeepestOne() {
        // `shallow` is referenced after `deep`, through `hook`
        val schema = $$"""
            allOf:
              - '$ref': '#/extension/deep/a/b'
              - '$ref': '#/extension/hook'
                .
            properties:
              field:
                '$ref': 'http://x.test/shared.json'
                .
              .
            extension:
              hook:
                '$ref': '#/extension/shallow'
                .
              shallow:
                '$id': 'http://x.test/shared.json'
                title: shallow
                .
              deep:
                a:
                  b:
                    '$id': 'http://x.test/shared.json'
                    title: deep
        """.trimIndent()
        assertEquals("deep" to "", resolveField(schema))
    }

    @Test
    fun resolvesAnIdSharedByADeeperAndALaterSchemaToTheDeeperOne() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'http://x.test/shared.json'
                .
              .
            definitions:
              a:
                definitions:
                  b:
                    '$id': 'http://x.test/shared.json'
                    title: deep
                    .
                  .
                .
              c:
                '$id': 'http://x.test/shared.json'
                title: 'shallow later'
        """.trimIndent()
        assertEquals("deep" to "", resolveField(schema))
    }

    @Test
    fun resolvesAnIdSharedByEquallyDeepValuesToTheLastOne() {
        // whichever is referenced first, `second` comes after `first` in the document
        for (refs in listOf(listOf("first", "second"), listOf("second", "first"))) {
            val schema = $$"""
                allOf:
                  - '$ref': '#/extension/$${refs[0]}'
                  - '$ref': '#/extension/$${refs[1]}'
                    .
                properties:
                  field:
                    '$ref': 'http://x.test/shared.json'
                    .
                  .
                extension:
                  first:
                    '$id': 'http://x.test/shared.json'
                    title: first
                    .
                  second:
                    '$id': 'http://x.test/shared.json'
                    title: second
            """.trimIndent()
            assertEquals("second" to "", resolveField(schema), "refs in order $refs")
        }
    }

    @Test
    fun resolvesAnIdSharedByDocumentRootsToTheLastDocument() {
        val documents = listOf("first", "second").map { name ->
            val source = $$"""
                '$id': 'http://x.test/shared.json'
                title: $$name
            """.trimIndent()
            SchemaDocument(value(source), UriReference.parse("http://x.test/$name.json"))
        }
        val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(value("{}")), documents)

        val resolution = indexedDocuments.resolveRef("http://x.test/shared.json", indexedDocuments.rootBaseUri)

        val shared = assertIs<RefResolution.Found>(resolution).target
        assertEquals("second" to "http://x.test/second.json", titleAndBaseUri(shared))
    }

    @Test
    fun resolvesAUriSharedByADocumentAndADeeperIdToTheDocument() {
        val root = value($$"""
            definitions:
              a:
                definitions:
                  b:
                    '$id': 'file:///other.json'
                    title: embedded
        """.trimIndent())
        val indexedDocuments = IndexedSchemaDocuments(
            SchemaDocument(root, UriReference.parse("file:///root.json")),
            listOf(SchemaDocument(value("title: supplied"), UriReference.parse("file:///other.json")))
        )

        val resolution = indexedDocuments.resolveRef("file:///other.json", indexedDocuments.rootBaseUri)

        val other = assertIs<RefResolution.Found>(resolution).target
        assertEquals("supplied" to "file:///other.json", titleAndBaseUri(other))
    }

    @Test
    fun resolvesAUriSharedByADocumentAndALaterIdToTheDocument() {
        val other = value($$"""
            '$id': 'file:///root.json'
            title: other
        """.trimIndent())
        val indexedDocuments = IndexedSchemaDocuments(
            SchemaDocument(value("title: root"), UriReference.parse("file:///root.json")),
            listOf(SchemaDocument(other, UriReference.parse("file:///other.json")))
        )

        val resolution = indexedDocuments.resolveRef("file:///root.json", indexedDocuments.rootBaseUri)

        val root = assertIs<RefResolution.Found>(resolution).target
        assertEquals("root" to "file:///root.json", titleAndBaseUri(root))
    }

    @Test
    fun resolvesTheMetaSchemaUriToTheBuiltInOne() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'http://json-schema.org/draft-07/schema#'
        """.trimIndent()
        assertSame(KsonDraft7MetaSchema.schemaValue, resolve(schema).value)
    }

    @Test
    fun resolvesRefsWithinARootWithTheMetaSchemaIdToThatRoot() {
        val root = value($$"""
            '$id': 'http://json-schema.org/draft-07/schema#'
            title: root
            definitions:
              mine:
                title: mine
        """.trimIndent())
        val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(root))
        val baseUri = indexedDocuments.rootSubschemaBaseUri

        val mine = assertIs<RefResolution.Found>(indexedDocuments.resolveRef("#/definitions/mine", baseUri)).target
        val self = assertIs<RefResolution.Found>(indexedDocuments.resolveRef("#", baseUri)).target

        assertEquals("mine" to "http://json-schema.org/draft-07/schema", titleAndBaseUri(mine))
        assertEquals("root" to "", titleAndBaseUri(self))
    }

    @Test
    fun resolvesTheMetaSchemaUriToADocumentSuppliedUnderIt() {
        val indexedDocuments = IndexedSchemaDocuments(
            SchemaDocument(value("{}")),
            listOf(SchemaDocument(value("title: custom"), UriReference.parse(KsonDraft7MetaSchema.ID)))
        )

        val resolution = indexedDocuments.resolveRef(KsonDraft7MetaSchema.ID, indexedDocuments.rootBaseUri)

        val metaSchema = assertIs<RefResolution.Found>(resolution).target
        assertEquals("custom" to KsonDraft7MetaSchema.ID, titleAndBaseUri(metaSchema))
    }

    @Test
    fun resolvesTheMetaSchemaUriToADocumentWithThatId() {
        val vendored = value($$"""
            '$id': 'http://json-schema.org/draft-07/schema#'
            definitions:
              custom:
                title: custom
        """.trimIndent())
        val indexedDocuments = IndexedSchemaDocuments(
            SchemaDocument(value("{}")),
            listOf(SchemaDocument(vendored, UriReference.parse("file:///vendored.json")))
        )

        val resolution = indexedDocuments.resolveRef(
            "http://json-schema.org/draft-07/schema#/definitions/custom",
            indexedDocuments.rootBaseUri
        )

        val custom = assertIs<RefResolution.Found>(resolution).target
        assertEquals("custom" to "http://json-schema.org/draft-07/schema", titleAndBaseUri(custom))
    }

    @Test
    fun indexesAPointerTargetWithinTheDeepestSchemaSharingAnId() {
        // `s1` is read before the deeper `s2`, whichever key comes first
        val definitions = $$"""
            definitions: {
              s1: { '$id': 'http://x.test/shared.json' }
              a: {
                definitions: {
                  s2: { '$id': 'http://x.test/shared.json', extension: { t: { '$ref': 'dep.json' } } }
                }
              }
            }
        """.trimIndent()
        val allOf = $$"""allOf: [{ '$ref': 'http://x.test/shared.json#/extension/t' }]"""
        for (keys in listOf(listOf(definitions, allOf), listOf(allOf, definitions))) {
            val root = value(keys.joinToString("\n"))
            val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(root, UriReference.parse("file:///root.json")))

            assertEquals(
                listOf(UriReference.parse("http://x.test/dep.json")),
                indexedDocuments.missingDocumentUris,
                "keys in order ${root.propertyLookup.keys}"
            )
        }
    }

    @Test
    fun indexesAPointerTargetWithinTheSchemaASharedIdFallsBackTo() {
        // `a` is read under root.json before `hook` makes `e` a schema, so s.json identifies `a`, deeper than `b`,
        // until `a` is read again under `e`'s `$id` and the pointer into s.json finds `t` in `b`
        val allOf = $$"""
            allOf: [
              { '$ref': '#/extension/e/a' },
              { '$ref': 'file:///s.json#/extension/t' },
              { '$ref': '#/extension/hook' }
            ]
        """.trimIndent()
        val definitions = $$"""
            definitions: {
              b: { '$id': 'file:///s.json', extension: { t: { '$ref': 'dep.json' } } }
            }
        """.trimIndent()
        val extension = $$"""
            extension: {
              hook: { '$ref': '#/extension/e' }
              e: { '$id': 'http://e.test/', a: { '$id': 's.json' } }
            }
        """.trimIndent()
        for (keys in listOf(listOf(allOf, definitions, extension), listOf(definitions, allOf, extension))) {
            val root = value(keys.joinToString("\n"))
            val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(root, UriReference.parse("file:///root.json")))

            assertEquals(
                listOf(UriReference.parse("file:///dep.json")),
                indexedDocuments.missingDocumentUris,
                "keys in order ${root.propertyLookup.keys}"
            )
        }
    }

    @Test
    fun doesNotResolveARefToAnIdInAKeywordBesideARef() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'http://example.com/nested.json'
                .
              .
            definitions:
              holder:
                '$ref': '#/definitions/other'
                definitions:
                  nested:
                    '$id': 'http://example.com/nested.json'
                    title: nested
                    .
                  .
                .
              other:
                title: other
        """.trimIndent()
        assertEquals(null to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun resolvesARefToAnIdBesideARefThatIsNotAString() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'http://example.com/holder/nested.json'
                .
              .
            definitions:
              holder:
                '$id': 'http://example.com/holder/'
                '$ref': 42
                definitions:
                  nested:
                    '$id': 'nested.json'
                    title: nested
        """.trimIndent()
        assertEquals("nested" to "http://example.com/holder/", resolveField(schema))
    }

    @Test
    fun resolvesAPointerIntoAKeywordBesideARefUnderTheBaseUriIgnoringItsId() {
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              field:
                '$ref': '#/allOf/0/definitions/target'
                .
              .
            allOf:
              - '$id': 'http://example.com/ignored.json'
                '$ref': '#/definitions/other'
                definitions:
                  target:
                    title: target
                    .
                  .
                .
            definitions:
              other: {}
        """.trimIndent()
        assertEquals("target" to "http://example.com/root.json", resolveField(schema))
    }

    @Test
    fun resolvesAPointerIntoDefsUnderTheRootsBaseUri() {
        // Draft 7 has no `$defs`, so `outer` is data, whose `$id` sets no base URI for `inner`
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              field:
                '$ref': '#/$defs/outer/$defs/inner'
                .
              .
            '$defs':
              outer:
                '$id': 'http://example.com/outer.json'
                '$defs':
                  inner:
                    title: inner
        """.trimIndent()
        assertEquals("inner" to "http://example.com/root.json", resolveField(schema))
    }

    @Test
    fun ignoresAnIdOfDataAlongAPointer() {
        // only `x` is referenced, so `deep` is data: its `$id` neither sets the base URI for `x` nor identifies `deep`
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              field:
                '$ref': '#/extension/deep/x'
                .
              .
            extension:
              deep:
                '$id': 'http://example.com/deep.json'
                x:
                  title: x
        """.trimIndent()
        assertEquals("x" to "http://example.com/root.json", resolveField(schema))
        assertEquals(RefResolution.NotFound, resolveRef(schema, "http://example.com/deep.json"))
    }

    @Test
    fun readsValuesUnderTheIdOfAnEnclosingSchemaFoundAfterThem() {
        // only `hook` makes `outer` a schema, so `inner` and `link` may be read before `outer`'s `$id` applies
        val deepRefs = listOf("#/extension/outer/inner", "#/extension/outer/link")
        for (refs in listOf(deepRefs + "#/extension/hook", listOf("#/extension/hook") + deepRefs)) {
            val schema = $$"""
                allOf:
                  - '$ref': '$${refs[0]}'
                  - '$ref': '$${refs[1]}'
                  - '$ref': '$${refs[2]}'
                    .
                properties:
                  field:
                    '$ref': 'http://example.com/outer/target.json'
                    .
                  .
                extension:
                  hook:
                    '$ref': '#/extension/outer'
                    .
                  outer:
                    '$id': 'http://example.com/outer/'
                    inner:
                      '$id': 'inner.json'
                      title: inner
                      .
                    link:
                      '$ref': '#/extension/target'
                      .
                    extension:
                      target:
                        '$id': 'target.json'
                        title: target
            """.trimIndent()
            val order = "refs in order $refs"
            assertEquals("target" to "http://example.com/outer/", resolveField(schema), order)
            val inner = assertIs<RefResolution.Found>(resolveRef(schema, "http://example.com/outer/inner.json"), order)
            assertEquals("inner" to "http://example.com/outer/", titleAndBaseUri(inner.target), order)
            assertEquals(RefResolution.NotFound, resolveRef(schema, "inner.json"), order)
        }
    }

    @Test
    fun resolvesAPointerWithinASchemaWithARelativeId() {
        // `other`'s `$id` resolves against the root's once, so `a` is read under sub/other.json rather than
        // sub/sub/other.json
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              field:
                '$ref': 'sub/other.json#/definitions/a'
                .
              .
            definitions:
              other:
                '$id': 'sub/other.json'
                definitions:
                  a:
                    title: a
        """.trimIndent()
        assertEquals("a" to "http://example.com/sub/other.json", resolveField(schema))
    }

    @Test
    fun resolvesAPointerWithinASchemaWithARelativeIdWithoutARootId() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'sub/other.json#/definitions/a'
                .
              .
            definitions:
              other:
                '$id': 'sub/other.json'
                definitions:
                  a:
                    title: a
        """.trimIndent()
        assertEquals("a" to "sub/other.json", resolveField(schema))
    }

    @Test
    fun resolvesARefUnderARelativeRootId() {
        // the ref and `other`'s `$id` both resolve against the root's `$id`, to sub/other.json
        val schema = $$"""
            '$id': 'sub/root.json'
            properties:
              field:
                '$ref': 'other.json'
                .
              .
            definitions:
              other:
                '$id': 'other.json'
                title: other
        """.trimIndent()
        assertEquals("other" to "sub/root.json", resolveField(schema))
    }

    @Test
    fun resolvesARootRefBesideTheRootIdInTheRootDocument() {
        // `target` is read under the retrieval URI, which is empty here
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            '$ref': '#/$defs/target'
            '$defs':
              target:
                title: target
        """.trimIndent()
        assertEquals("target" to "", resolveRoot(schema))
    }

    @Test
    fun resolvesARefWithoutAFragmentToAnIdWithAnEmptyOne() {
        // `other.json#` identifies the same schema as `other.json`
        val schema = $$"""
            properties:
              field:
                '$ref': 'other.json'
                .
              .
            definitions:
              other:
                '$id': 'other.json#'
                title: other
        """.trimIndent()
        assertEquals("other" to "", resolveField(schema))
    }

    @Test
    fun resolvesAPointerWithinASchemaWhoseIdHasAnEmptyFragment() {
        val schema = $$"""
            properties:
              field:
                '$ref': 'other.json#/definitions/target'
                .
              .
            definitions:
              other:
                '$id': 'other.json#'
                definitions:
                  target:
                    title: target
        """.trimIndent()
        assertEquals("target" to "other.json", resolveField(schema))
    }

    @Test
    fun resolvesARefWithDotSegments() {
        val schema = $$"""
            '$id': 'http://example.com/schemas/root.json'
            properties:
              field:
                '$ref': '../shared/defs.json#/definitions/target'
                .
              .
            definitions:
              shared:
                '$id': 'http://example.com/shared/defs.json'
                definitions:
                  target:
                    title: target
        """.trimIndent()
        assertEquals("target" to "http://example.com/shared/defs.json", resolveField(schema))
    }

    @Test
    fun resolvesANetworkPathRef() {
        // the ref names a host of its own, keeping only the base's scheme
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              field:
                '$ref': '//other.example/target.json'
                .
              .
            definitions:
              target:
                '$id': 'http://other.example/target.json'
                title: target
        """.trimIndent()
        assertEquals("target", resolveField(schema).first)
    }

    @Test
    fun resolvesAUrnRefToAUrnIdReadUnderAnotherBase() {
        // a URN takes nothing from the base it is read under, so the ref and the `$id` name the same schema
        val schema = $$"""
            '$id': 'http://example.com/root.json'
            properties:
              field:
                '$ref': 'urn:example:target'
                .
              .
            definitions:
              nested:
                '$id': 'http://example.com/nested/nested.json'
                definitions:
                  target:
                    '$id': 'urn:example:target'
                    title: target
        """.trimIndent()
        assertEquals("target", resolveField(schema).first)
    }

    @Test
    fun resolvedRootSchemaFollowsTheRootsRef() {
        val root = KsonCore.parseToAst(
            $$"""
                '$ref': '#/$defs/target'
                '$defs':
                  target:
                    title: target
            """.trimIndent()
        ).ksonValue as KsonObject
        val target = (root.propertyLookup.getValue($$"$defs") as KsonObject).propertyLookup.getValue("target")

        assertSame(target, IndexedSchemaDocuments(SchemaDocument(root)).resolvedRootSchema.value)
    }
}

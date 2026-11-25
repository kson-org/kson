package org.kson.schema

import org.kson.KsonCore
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.uri.UriReference
import org.kson.validation.SourceContext
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class ExternalSchemaRefsTest {
    private fun value(source: String): KsonValue = assertNotNull(KsonCore.parseToAst(source).ksonValue)

    private fun title(ref: ResolvedRef?): String? {
        val schema = ref?.resolvedValue as? KsonObject
        return (schema?.propertyLookup?.get("title") as? KsonString)?.value
    }

    /** The messages from validating [source] against the schema [indexedDocuments] was built for */
    private fun validationMessages(indexedDocuments: IndexedSchemaDocuments, source: String): List<LoggedMessage> {
        val schema = assertNotNull(SchemaParser.parseSchemaRoot(indexedDocuments, MessageSink()))
        val messageSink = MessageSink()
        schema.validate(value(source), messageSink, SourceContext())
        return messageSink.loggedMessages()
    }

    @Test
    fun retrievalUriResolvesRelativePointersAndTransitiveReferences() {
        val root = value($$"""'$ref': '../common/first.json#/definitions/entry'""")
        val first = value($$"""
            definitions:
              entry:
                '$ref': 'second.json#/definitions/value'
        """.trimIndent())
        val second = value("""
            definitions:
              value:
                title: Target
                type: string
        """.trimIndent())
        val documents = listOf(
            SchemaDocument(first, UriReference.parse("file:///schemas/common/first.json")),
            SchemaDocument(second, UriReference.parse("file:///schemas/common/second.json"))
        )
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/project/root.json"))
        val indexedDocuments = IndexedSchemaDocuments(rootDocument, documents)

        val resolved = indexedDocuments.resolveRefIfPresent(root, indexedDocuments.rootBaseUri)

        assertEquals("Target", title(resolved))
        assertEquals("file:///schemas/common/second.json", resolved.resolvedValueBaseUri.toString())
        assertEquals(emptyList(), indexedDocuments.missingDocumentUris)
        val messageTypes = validationMessages(indexedDocuments, "42").map { it.message.type }
        assertEquals(listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH), messageTypes)
    }

    @Test
    fun nestedRelativeIdsApplyOnce() {
        val root = value($$"""
            '$id': 'canonical/root.json'
            definitions:
              nested:
                '$id': '../nested/'
                properties:
                  field:
                    '$ref': 'value.json'
        """.trimIndent())
        val external = value("title: External")
        val documents = listOf(
            SchemaDocument(external, UriReference.parse("https://example.test/schemas/nested/value.json"))
        )
        val rootDocument = SchemaDocument(root, UriReference.parse("https://example.test/schemas/root.json"))
        val indexedDocuments = IndexedSchemaDocuments(rootDocument, documents)
        val canonicalRootUri = UriReference.parse("https://example.test/schemas/canonical/root.json")
        val nestedBaseUri = UriReference.parse("https://example.test/schemas/nested/")

        val viaPointer =
            assertIs<RefResolution.Found>(indexedDocuments.resolveRef("#/definitions/nested", canonicalRootUri)).target
        val viaId = assertIs<RefResolution.Found>(indexedDocuments.resolveRef("../nested/", canonicalRootUri)).target
        val viaNestedBaseUri =
            assertIs<RefResolution.Found>(indexedDocuments.resolveRef("value.json", nestedBaseUri)).target

        assertEquals(canonicalRootUri, viaPointer.resolvedValueBaseUri)
        assertSame(viaPointer.resolvedValue, viaId.resolvedValue)
        assertEquals("External", title(viaNestedBaseUri))
        assertEquals(emptyList(), indexedDocuments.missingDocumentUris)
    }

    @Test
    fun discoversUniqueMissingResourcesButNotDataOrLocalMissingFragments() {
        val root = value($$"""
            properties:
              a:
                '$ref': 'shared.json#/first'
                .
              b:
                '$ref': 'shared.json#/second'
                .
              c:
                '$ref': '#/missing'
                .
              d:
                '$id': 'ignored/'
                '$ref': 'other.json'
                .
              .
            default:
              '$ref': 'data.json'
              .
            enum:
              - '$id': 'data-id.json'
                '$ref': 'enum.json'
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument)

        assertEquals(
            listOf(UriReference.parse("file:///schemas/shared.json"), UriReference.parse("file:///schemas/other.json")),
            indexedDocuments.missingDocumentUris
        )
    }

    @Test
    fun doesNotDiscoverResourcesReferencedBesideARef() {
        val root = value($$"""
            '$ref': 'other.json'
            properties:
              field:
                '$ref': 'ignored.json'
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument)

        assertEquals(listOf(UriReference.parse("file:///schemas/other.json")), indexedDocuments.missingDocumentUris)
    }

    @Test
    fun doesNotDiscoverTheEmptyUriAsAMissingResource() {
        // against the relative retrieval URI root.json, `.` resolves to the empty URI
        val root = value($$"""
            properties:
              field:
                '$ref': '.'
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument)

        // quoted, as an empty URI prints as nothing
        assertEquals(emptyList(), indexedDocuments.missingDocumentUris.map { "'$it'" })
    }

    @Test
    fun discoversTheDependenciesOfAValueSuppliedAsTwoDocuments() {
        val shared = value($$"""
            properties:
              field:
                '$ref': 'dependency.json'
        """.trimIndent())
        val documents = listOf("a", "b").map { SchemaDocument(shared, UriReference.parse("file:///$it/shared.json")) }

        val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(value("{}")), documents)

        assertEquals(
            listOf(UriReference.parse("file:///a/dependency.json"), UriReference.parse("file:///b/dependency.json")),
            indexedDocuments.missingDocumentUris
        )
    }

    @Test
    fun loadedSharedAndCyclicDependenciesTerminateDiscoveryAndRefResolution() {
        val root = value($$"""'$ref': 'a.json'""")
        val a = value($$"""'$ref': 'b.json'""")
        val b = value($$"""'$ref': 'a.json'""")
        val documents = listOf(
            SchemaDocument(a, UriReference.parse("https://example.test/a.json")),
            SchemaDocument(b, UriReference.parse("https://example.test/b.json"))
        )
        val rootDocument = SchemaDocument(root, UriReference.parse("https://example.test/root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument, documents)

        assertEquals(emptyList(), indexedDocuments.missingDocumentUris)
        val chainEnd = indexedDocuments.resolveRefIfPresent(root, indexedDocuments.rootBaseUri)
        val cycle = assertIs<UnresolvedRef.Cycle>(chainEnd.unresolvedRef)
        assertSame((b as KsonObject).propertyLookup[$$"$ref"], cycle.ref)
        val messageTypes = validationMessages(indexedDocuments, "42").map { it.message.type }
        assertEquals(listOf(MessageType.SCHEMA_REF_CYCLE), messageTypes)
    }

    @Test
    fun retrievalAliasesAndCanonicalIdsFindTheSameExternalPointer() {
        val external = value($$"""
            '$id': 'https://canonical.test/types.json'
            definitions:
              str:
                type: string
        """.trimIndent())
        val documents = listOf(SchemaDocument(external, UriReference.parse("file:///types.json")))
        val rootDocument = SchemaDocument(value("{}"), UriReference.parse("file:///root.json"))
        val indexedDocuments = IndexedSchemaDocuments(rootDocument, documents)
        val pointer = "#/definitions/str"

        val viaRetrievalUri = assertIs<RefResolution.Found>(
            indexedDocuments.resolveRef("file:///types.json$pointer", indexedDocuments.rootBaseUri)
        ).target
        val viaId = assertIs<RefResolution.Found>(
            indexedDocuments.resolveRef("https://canonical.test/types.json$pointer", indexedDocuments.rootBaseUri)
        ).target

        assertSame(viaRetrievalUri.resolvedValue, viaId.resolvedValue)
        assertEquals("https://canonical.test/types.json", viaRetrievalUri.resolvedValueBaseUri.toString())
    }

    @Test
    fun productiveRecursiveExternalSchemasValidateFiniteDocuments() {
        val root = value($$"""'$ref': 'a.json'""")
        val a = value($$"""
            type: object
            properties:
              value:
                type: number
                .
              next:
                '$ref': 'b.json'
        """.trimIndent())
        val b = value($$"""
            type: object
            properties:
              value:
                type: number
                .
              next:
                '$ref': 'a.json'
        """.trimIndent())
        val documents = listOf(
            SchemaDocument(a, UriReference.parse("file:///a.json")),
            SchemaDocument(b, UriReference.parse("file:///b.json"))
        )
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument, documents)

        assertEquals(emptyList(), indexedDocuments.missingDocumentUris)
        val finiteChain = """
            value: 1
            next:
              value: 2
              next:
                value: 3
        """.trimIndent()
        assertEquals(emptyList(), validationMessages(indexedDocuments, finiteChain))
        val badThirdValue = """
            next:
              next:
                value: bad
        """.trimIndent()
        val messages = validationMessages(indexedDocuments, badThirdValue)
        assertEquals(listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH), messages.map { it.message.type })
        assertEquals(listOf(Location(Coordinates(2, 11), Coordinates(2, 14), 25, 28)), messages.map { it.location })
    }

    @Test
    fun pointersIntoExtensionDataResolveWithoutInterpretingDataIds() {
        val external = value($$"""
            extension:
              '$id': 'ignored/'
              target:
                type: number
        """.trimIndent())
        val documents = listOf(SchemaDocument(external, UriReference.parse("file:///types.json")))
        val rootDocument = SchemaDocument(value("{}"), UriReference.parse("file:///root.json"))
        val indexedDocuments = IndexedSchemaDocuments(rootDocument, documents)

        val target = assertIs<RefResolution.Found>(
            indexedDocuments.resolveRef("types.json#/extension/target", indexedDocuments.rootBaseUri)
        ).target

        assertEquals("file:///types.json", target.resolvedValueBaseUri.toString())
    }

    @Test
    fun rootRefIgnoresTheRootIdBesideIt() {
        val root = value($$"""
            '$id': 'canonical/root.json'
            '$ref': 'types.json#/definitions/str'
        """.trimIndent())
        val types = value("""
            definitions:
              str:
                type: string
                title: String
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))
        val typesDocument = SchemaDocument(types, UriReference.parse("file:///schemas/types.json"))

        val incomplete = IndexedSchemaDocuments(rootDocument)
        val complete = IndexedSchemaDocuments(rootDocument, listOf(typesDocument))

        assertEquals(listOf(UriReference.parse("file:///schemas/types.json")), incomplete.missingDocumentUris)
        assertEquals(emptyList(), complete.missingDocumentUris)
        assertEquals("file:///schemas/root.json", complete.rootBaseUri.toString())
        assertEquals("file:///schemas/root.json", complete.rootSubschemaBaseUri.toString())
        assertEquals("String", title(complete.resolveRefIfPresent(root, complete.rootBaseUri)))
        val messageTypes = validationMessages(complete, "42").map { it.message.type }
        assertEquals(listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH), messageTypes)
    }

    @Test
    fun rootIdWithAnEmptyFragmentSetsTheBaseWithoutIt() {
        // `canonical/root.json#` identifies the same schema as `canonical/root.json`
        val root = value($$"""
            '$id': 'canonical/root.json#'
            properties:
              field:
                '$ref': '#/definitions/str'
                .
              .
            definitions:
              str:
                type: string
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument)

        assertEquals("file:///schemas/canonical/root.json", indexedDocuments.rootSubschemaBaseUri.toString())
        val messageTypes = validationMessages(indexedDocuments, "field: 42").map { it.message.type }
        assertEquals(listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH), messageTypes)
    }

    @Test
    fun rootIdAppliesOnceToReferencedExtensionData() {
        val root = value($$"""
            '$id': 'canonical/root.json'
            allOf:
              - '$ref': '#/extension/target'
                .
            extension:
              target:
                '$ref': 'types.json'
        """.trimIndent())
        val types = value("type: string")
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))
        val typesDocument = SchemaDocument(types, UriReference.parse("file:///schemas/canonical/types.json"))

        val incomplete = IndexedSchemaDocuments(rootDocument)
        val complete = IndexedSchemaDocuments(rootDocument, listOf(typesDocument))

        assertEquals(listOf(UriReference.parse("file:///schemas/canonical/types.json")), incomplete.missingDocumentUris)
        assertEquals(emptyList(), complete.missingDocumentUris)
        assertEquals(emptyList(), validationMessages(complete, "'text'"))
        val messageTypes = validationMessages(complete, "42").map { it.message.type }
        assertEquals(listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH), messageTypes)
    }

    @Test
    fun rootIdBesideRootRefDoesNotApplyToReferencedExtensionData() {
        val root = value($$"""
            '$id': 'canonical/root.json'
            '$ref': '#/extension/target'
            extension:
              target:
                '$ref': 'types.json'
        """.trimIndent())
        val types = value("type: string")
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))
        val typesDocument = SchemaDocument(types, UriReference.parse("file:///schemas/types.json"))

        val incomplete = IndexedSchemaDocuments(rootDocument)
        val complete = IndexedSchemaDocuments(rootDocument, listOf(typesDocument))

        assertEquals(listOf(UriReference.parse("file:///schemas/types.json")), incomplete.missingDocumentUris)
        assertEquals(emptyList(), complete.missingDocumentUris)
        assertEquals(emptyList(), validationMessages(complete, "'text'"))
        val messageTypes = validationMessages(complete, "42").map { it.message.type }
        assertEquals(listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH), messageTypes)
    }

    @Test
    fun refThroughAnIdIdentifiedLaterDiscoversItsTargetsDependencies() {
        // `a` is extension data, so `a.json` identifies it only once the first `$ref` leads there
        val root = value($$"""
            allOf:
              - '$ref': '#/extension/a'
              - '$ref': 'a.json#/extension/x'
                .
            extension:
              a:
                '$id': 'a.json'
                extension:
                  x:
                    '$ref': 'x-dependency.json'
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument)

        assertEquals(
            listOf(UriReference.parse("file:///schemas/x-dependency.json")),
            indexedDocuments.missingDocumentUris
        )
    }

    @Test
    fun referencedCustomPointerTargetsDiscoverTheirOwnDependencies() {
        val root = value($$"""'$ref': 'child.json#/custom'""")
        val child = value($$"""
            custom:
              '$ref': 'next.json'
              .
            unreferenced:
              '$ref': 'unused.json'
        """.trimIndent())
        val next = value("type: string")
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))
        val childDocument = SchemaDocument(child, UriReference.parse("file:///schemas/child.json"))
        val nextDocument = SchemaDocument(next, UriReference.parse("file:///schemas/next.json"))

        val incomplete = IndexedSchemaDocuments(rootDocument, listOf(childDocument))
        val complete = IndexedSchemaDocuments(rootDocument, listOf(childDocument, nextDocument))

        assertEquals(listOf(UriReference.parse("file:///schemas/next.json")), incomplete.missingDocumentUris)
        assertEquals(emptyList(), complete.missingDocumentUris)
        assertEquals(emptyList(), validationMessages(complete, "'text'"))
        val messageTypes = validationMessages(complete, "42").map { it.message.type }
        assertEquals(listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH), messageTypes)
    }

    @Test
    fun onlyReferencedDefsEntriesDiscoverTheirDependencies() {
        // Draft 7 has no `$defs`, so what it holds is data, read as a schema only where a `$ref` leads
        val root = value($$"""
            '$ref': '#/$defs/used'
            '$defs':
              used:
                '$ref': 'used.json'
                .
              unused:
                '$ref': 'unused.json'
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument)

        assertEquals(listOf(UriReference.parse("file:///schemas/used.json")), indexedDocuments.missingDocumentUris)
    }

    @Test
    fun referencedCustomSchemaIdsApplyWithoutInterpretingUnreferencedDataIds() {
        val root = value($$"""'$ref': 'child.json#/custom/selected'""")
        val child = value($$"""
            custom:
              '$id': 'ignored/'
              selected:
                '$id': 'types/'
                properties:
                  field:
                    '$ref': 'next.json#/custom'
                    .
                  .
                .
              .
            default:
              '$ref': 'unused.json'
        """.trimIndent())
        val next = value($$"""
            custom:
              type: object
              properties:
                next:
                  '$ref': '../child.json#/custom/selected'
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))
        val childDocument = SchemaDocument(child, UriReference.parse("file:///schemas/child.json"))
        val nextDocument = SchemaDocument(next, UriReference.parse("file:///schemas/types/next.json"))

        val incomplete = IndexedSchemaDocuments(rootDocument, listOf(childDocument))
        val complete = IndexedSchemaDocuments(rootDocument, listOf(childDocument, nextDocument))

        assertEquals(listOf(UriReference.parse("file:///schemas/types/next.json")), incomplete.missingDocumentUris)
        assertEquals(emptyList(), complete.missingDocumentUris)
        val goodThirdField = """
            field:
              next:
                field: {}
        """.trimIndent()
        assertEquals(emptyList(), validationMessages(complete, goodThirdField))
        val badThirdField = """
            field:
              next:
                field: 42
        """.trimIndent()
        val messages = validationMessages(complete, badThirdField)
        assertEquals(listOf(MessageType.SCHEMA_VALUE_TYPE_MISMATCH), messages.map { it.message.type })
        assertEquals(listOf(Location(Coordinates(2, 11), Coordinates(2, 13), 26, 28)), messages.map { it.location })
    }

    @Test
    fun referencedCustomSchemaScopesItsSubschemasWhicheverReferenceComesFirst() {
        val targetRef = "#/custom/target"
        val nestedRef = "#/custom/target/definitions/nested"
        val documents = listOf(
            SchemaDocument(value("type: number"), UriReference.parse("file:///s/y.json")),
            SchemaDocument(value("type: string"), UriReference.parse("file:///s/x/y.json"))
        )

        for (refs in listOf(listOf(targetRef, nestedRef), listOf(nestedRef, targetRef))) {
            val root = value($$"""
                allOf:
                  - '$ref': '$${refs[0]}'
                  - '$ref': '$${refs[1]}'
                    .
                custom:
                  target:
                    '$id': 'x/'
                    definitions:
                      nested:
                        '$ref': 'y.json'
            """.trimIndent())
            val rootDocument = SchemaDocument(root, UriReference.parse("file:///s/root.json"))

            val incomplete = IndexedSchemaDocuments(rootDocument)
            val complete = IndexedSchemaDocuments(rootDocument, documents)

            assertEquals(
                listOf(UriReference.parse("file:///s/x/y.json")),
                incomplete.missingDocumentUris,
                "refs in order $refs"
            )
            assertEquals(emptyList(), validationMessages(complete, "'text'"), "refs in order $refs")
        }
    }

    @Test
    fun customSchemaReferencedLaterScopesTheSubschemasReferencedBefore() {
        // only `hook` leads to `target`, so it is referenced later than `nested`
        val root = value($$"""
            allOf:
              - '$ref': '#/custom/target/definitions/nested'
              - '$ref': '#/custom/hook'
                .
            custom:
              hook:
                '$ref': '#/custom/target'
                .
              target:
                '$id': 'x/'
                definitions:
                  nested:
                    '$ref': 'y.json'
        """.trimIndent())
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///s/root.json"))

        val indexedDocuments = IndexedSchemaDocuments(rootDocument)

        assertEquals(listOf(UriReference.parse("file:///s/x/y.json")), indexedDocuments.missingDocumentUris)
    }

    @Test
    fun additionalDocumentsMustSayWhereTheyWereRetrievedFrom() {
        val root = value($$"""
            '$ref': '#/definitions/text'
            definitions:
              text:
                type: string
        """.trimIndent())
        val document = value("""
            definitions:
              text:
                type: number
        """.trimIndent())

        for (retrievalUri in listOf(null, UriReference.parse(""), UriReference.parse("#/definitions"))) {
            assertFailsWith<IllegalArgumentException>("retrieval URI '$retrievalUri'") {
                IndexedSchemaDocuments(SchemaDocument(root), listOf(SchemaDocument(document, retrievalUri)))
            }
        }
    }
}

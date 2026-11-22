package org.kson.schema

import org.kson.KsonCore
import org.kson.uri.UriReference
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchemaProgressTest {
    private fun kson(source: String): KsonValue =
        assertNotNull(KsonCore.parseToAst(source).ksonValue, "should parse: $source")

    private fun progressTowards(schema: String): SchemaProgress {
        val node = kson(schema)
        return SchemaProgress(node, UriReference.parse(""), IndexedSchemaDocuments(SchemaDocument(node)))
    }

    private fun locationOf(property: String, document: KsonValue) =
        (document as KsonObject).propertyLookup.getValue(property).location

    @Test
    fun aRefIsFollowed() {
        val progress = progressTowards(
            $$"""
                '$ref': '#/$defs/count'
                '$defs':
                  count:
                    type: number
            """.trimIndent()
        )

        assertFalse(progress.rejects(kson("3")))
        assertTrue(progress.rejects(kson("three")))
    }

    @Test
    fun refsAreReadUnderTheBaseUriItIsGiven() {
        val root = kson(
            $$"""
                '$ref': '#/$defs/count'
                '$defs':
                  count:
                    type: number
            """.trimIndent()
        )
        val retrievalUri = UriReference.parse("https://example.org/root")
        val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(root, retrievalUri))

        val underItsRetrievalUri = SchemaProgress(root, retrievalUri, indexedDocuments)
        assertTrue(underItsRetrievalUri.accepts(kson("3")))
        assertTrue(underItsRetrievalUri.rejects(kson("three")))

        val elsewhere = SchemaProgress(root, UriReference.parse("https://example.org/elsewhere"), indexedDocuments)
        assertFalse(elsewhere.accepts(kson("3")))
        assertFalse(elsewhere.rejects(kson("three")))
    }

    @Test
    fun whatIsNoSchemaNeitherAcceptsNorRejects() {
        val circle = kson("circle")
        val noSchemas = listOf(
            "3",
            $$"'$ref': '#/$defs/missing'",
            // `~2` is no JSON Pointer escape
            $$"'$ref': '#/$defs/a~2b'"
        )

        for (noSchema in noSchemas) {
            val progress = progressTowards(noSchema)

            assertFalse(progress.accepts(circle), "accepts: $noSchema")
            assertFalse(progress.rejects(circle), "rejects: $noSchema")
        }
    }

    @Test
    fun aKeywordItCannotReadIsIgnored() {
        val progress = progressTowards(
            """
                type: 5
                const: circle
            """.trimIndent()
        )

        assertFalse(progress.rejects(kson("circle")))
        assertTrue(progress.rejects(kson("square")))
    }

    @Test
    fun aBooleanSchemaIsASchema() {
        assertTrue(progressTowards("true").accepts(kson("anything")))
        assertFalse(progressTowards("true").rejects(kson("anything")))
        assertTrue(progressTowards("false").rejects(kson("anything")))
    }

    @Test
    fun aValueThatFitsIsNotRejected() {
        val progress = progressTowards(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertFalse(progress.rejects(kson("kind: circle")))
    }

    @Test
    fun whatIsOnlyMissingIsNotRejected() {
        val progress = progressTowards(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertFalse(progress.rejects(kson("radius: 3")))
    }

    @Test
    fun aPresentValueTheSchemaForbidsIsRejected() {
        assertTrue(progressTowards("type: object").rejects(kson("[1, 2]")))
        assertTrue(progressTowards("properties: { kind: { const: circle } }").rejects(kson("kind: square")))
        assertTrue(progressTowards("additionalProperties: false").rejects(kson("radius: 3")))
    }

    @Test
    fun aProblemInsideTheForgivenSpanIsNotRejected() {
        val progress = progressTowards(
            """
                properties:
                  kind:
                    const: circle
            """.trimIndent()
        )
        val document = kson("kind: cir")

        assertTrue(progress.rejects(document))
        assertFalse(progress.rejects(document, forgiving = locationOf("kind", document)))
    }

    @Test
    fun aProblemOutsideTheForgivenSpanStillRejects() {
        val progress = progressTowards(
            """
                properties:
                  kind:
                    const: circle
                    .
                  radius:
                    type: number
            """.trimIndent()
        )
        val document = kson(
            """
                kind: cir
                radius: big
            """.trimIndent()
        )

        assertTrue(progress.rejects(document, forgiving = locationOf("kind", document)))
    }

    @Test
    fun aValueThatFitsIsAccepted() {
        val progress = progressTowards(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertTrue(progress.accepts(kson("kind: circle")))
    }

    @Test
    fun aPresentValueTheSchemaForbidsIsNotAccepted() {
        assertFalse(progressTowards("type: object").accepts(kson("[1, 2]")))
        assertFalse(progressTowards("properties: { kind: { const: circle } }").accepts(kson("kind: square")))
        assertFalse(progressTowards("additionalProperties: false").accepts(kson("radius: 3")))
    }

    @Test
    fun whatIsOnlyUnfinishedIsNeitherRejectedNorAccepted() {
        val unfinished = listOf(
            "required: [kind]" to "radius: 3",
            "minProperties: 2" to "radius: 3",
            "minItems: 2" to "[1]",
            "minLength: 2" to "a",
            "contains: { const: 1 }" to "[2]",
            "dependencies: { radius: [kind] }" to "radius: 3",
            "not: { type: string }" to "a",
            "oneOf: [true, true]" to "a"
        )

        for ((source, document) in unfinished) {
            val progress = progressTowards(source)
            val value = kson(document)

            assertFalse(progress.rejects(value), "rejects: $source")
            assertFalse(progress.accepts(value), "accepts: $source")
        }
    }
}

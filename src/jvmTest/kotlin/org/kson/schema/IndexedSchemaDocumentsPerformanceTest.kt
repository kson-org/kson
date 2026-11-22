package org.kson.schema

import org.kson.KsonCore
import org.kson.uri.UriReference
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Indexing a long chain of `$ref`s settles quickly
 *
 * TODO: This is not a reliable timing test.  It would probably benefit from being replaced by a benchmark test.
 */
class IndexedSchemaDocumentsPerformanceTest {
    private val chainLength = 2000

    private fun chainOfRefs(): KsonValue {
        val links = (0 until chainLength).joinToString(", ") { index ->
            val next = if (index < chainLength - 1) "#/definitions/link${index + 1}" else "end.json"
            $$""""link$$index": { "$ref": "$$next" }"""
        }
        val source = $$"""{ "$ref": "#/definitions/link0", "definitions": { $$links } }"""
        return assertNotNull(KsonCore.parseToAst(source).ksonValue)
    }

    @Test
    fun indexesALongChainOfRefsBesideARootRefQuickly() {
        val rootDocument = SchemaDocument(chainOfRefs(), UriReference.parse("file:///schemas/root.json"))

        val (indexedDocuments, elapsed) = measureTimedValue { IndexedSchemaDocuments(rootDocument) }

        assertEquals(listOf(UriReference.parse("file:///schemas/end.json")), indexedDocuments.missingDocumentUris)
        assertTrue(elapsed < 5.seconds, "Indexing a chain of $chainLength `\$ref`s took $elapsed")
    }
}

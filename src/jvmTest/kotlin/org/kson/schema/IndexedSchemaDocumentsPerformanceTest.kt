package org.kson.schema

import org.kson.KsonCore
import org.kson.uri.UriReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Indexing a long chain of `$ref`s stays fast
 *
 * TODO: This is not a reliable timing test.  It would probably benefit from being replaced by a benchmark test.
 */
class IndexedSchemaDocumentsPerformanceTest {
    private val chainLength = 2000
    private val limit = 5.seconds

    /** The properties `link0`, `link1`, ... of [container], each holding a `$ref` to the next, the last to end.json */
    private fun chainLinks(container: String): String = (0 until chainLength).joinToString(", ") { index ->
        val next = if (index < chainLength - 1) "#/$container/link${index + 1}" else "end.json"
        $$"""link$$index: { '$ref': '$$next' }"""
    }

    /** Asserts that indexing the schema [source] finds the missing end.json, and does so quickly */
    private fun assertIndexesChainQuickly(source: String) {
        val root = assertNotNull(KsonCore.parseToAst(source).ksonValue)
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))

        val (indexedDocuments, elapsed) = measureTimedValue { IndexedSchemaDocuments(rootDocument) }

        assertEquals(listOf(UriReference.parse("file:///schemas/end.json")), indexedDocuments.missingDocumentUris)
        assertTrue(
            elapsed < limit,
            $$"Indexing a chain of $$chainLength `$ref`s took $$elapsed, over the $$limit limit.  " +
                "It normally takes well under a second, and rebuilding the index for each link took over 30 s, " +
                "so the limit is loose enough that a slow machine doesn't trip it."
        )
    }

    @Test
    fun indexesALongChainOfRefsBesideARootRefQuickly() {
        assertIndexesChainQuickly(
            $$"""{ '$ref': '#/definitions/link0', definitions: { $${chainLinks("definitions")} } }"""
        )
    }

    @Test
    fun indexesALongChainOfRefsBesideADeeperRefQuickly() {
        assertIndexesChainQuickly($$"""
            {
              allOf: [{ '$ref': '#/extension/deep/x' }, { '$ref': '#/extension/link0' }],
              extension: { deep: { x: { type: string } }, $${chainLinks("extension")} }
            }
        """)
    }
}

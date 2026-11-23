package org.kson.schema

import org.kson.KsonCore
import org.kson.uri.UriReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Checks how the time to index a chain of `$ref`s grows with the chain's length.  The test divides the time for one
 * length by the time for another on the same machine, which cancels out how fast the machine is overall.
 *
 * TODO: This is not a reliable timing test.  It would probably benefit from being replaced by a benchmark test.
 */
class IndexedSchemaDocumentsPerformanceTest {
    private val shortChainLength = 250
    private val longChainLength = 2000

    /** The properties `link0`, `link1`, ... of [container], each holding a `$ref` to the next, the last to end.json */
    private fun chainLinks(container: String, length: Int): String = (0 until length).joinToString(", ") { index ->
        val next = if (index < length - 1) "#/$container/link${index + 1}" else "end.json"
        $$"""link$$index: { '$ref': '$$next' }"""
    }

    /** The quickest of five times to index the chain [source], once warmed up */
    private fun timeToIndex(source: String): Duration {
        val root = assertNotNull(KsonCore.parseToAst(source).ksonValue)
        val rootDocument = SchemaDocument(root, UriReference.parse("file:///schemas/root.json"))
        assertEquals(
            listOf(UriReference.parse("file:///schemas/end.json")),
            IndexedSchemaDocuments(rootDocument).missingDocumentUris
        )
        repeat(3) { IndexedSchemaDocuments(rootDocument) }
        return (1..5).minOf { measureTime { IndexedSchemaDocuments(rootDocument) } }
    }

    /** Asserts that indexing time grows in proportion to the length of the chain that [chainSource] writes */
    private fun assertIndexingTakesTimeInProportionToTheChain(chainSource: (Int) -> String) {
        val growth = timeToIndex(chainSource(longChainLength)) / timeToIndex(chainSource(shortChainLength))

        val chainGrowth = longChainLength / shortChainLength
        val limit = 2 * chainGrowth
        assertTrue(
            growth < limit,
            "$chainGrowth times the links took $growth times as long to index, past the limit of $limit: linear " +
                "indexing grows up to $chainGrowth times, quadratic up to " +
                "${chainGrowth * chainGrowth}."
        )
    }

    @Test
    fun indexingTakesTimeInProportionToAChainOfRefsBesideARootRef() {
        assertIndexingTakesTimeInProportionToTheChain { length ->
            $$"""{ '$ref': '#/definitions/link0', definitions: { $${chainLinks("definitions", length)} } }"""
        }
    }

    @Test
    fun indexingTakesTimeInProportionToAChainOfRefsBesideADeeperRef() {
        assertIndexingTakesTimeInProportionToTheChain { length ->
            $$"""
                {
                  allOf: [{ '$ref': '#/extension/deep/x' }, { '$ref': '#/extension/link0' }],
                  extension: { deep: { x: { type: string } }, $${chainLinks("extension", length)} }
                }
            """
        }
    }
}

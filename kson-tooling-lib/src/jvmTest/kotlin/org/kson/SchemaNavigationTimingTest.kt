package org.kson

import org.kson.ast.AstNode
import org.kson.schema.IndexedSchemaDocuments
import org.kson.schema.SchemaDocument
import org.kson.tooling.navigation.SchemaNavigator
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.walker.TreePointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Checks how navigation time grows with the size of the schema.  Each test compares two sizes on the same machine,
 * so the machine's speed doesn't matter.
 */
class SchemaNavigationTimingTest {
    /** A schema of properties whose `$ref`s each lead through a chain of [chainLength] more, except `target`'s */
    private fun schemaOfChainedProperties(chainLength: Int): String {
        val properties = (0 until 2000).joinToString(",") { "\"p$it\": {\"\$ref\": \"#/definitions/c0\"}" }
        val chain = (0 until chainLength).joinToString(",") { "\"c$it\": {\"\$ref\": \"#/definitions/c${it + 1}\"}" }
        return "{\"properties\": {$properties, \"target\": {\"\$ref\": \"#/definitions/leaf\"}}, " +
            "\"definitions\": {$chain, \"c$chainLength\": {}, \"leaf\": {\"properties\": {\"q\": {}}}}}"
    }

    /**
     * The quickest of ten navigations into `target`, once warmed up.  Each is the first navigation over a newly indexed
     * schema with chains [chainLength] long, as later navigations reuse the `$ref`s the first one resolved.
     */
    private fun timeToNavigateIntoTarget(chainLength: Int): Duration {
        val root = assertNotNull(KsonCore.parseToAst(schemaOfChainedProperties(chainLength)).ksonValue)
        val pointer = TreePointer<AstNode>(JsonPointer.fromTokens(listOf("target", "q")))
        fun newNavigator() = SchemaNavigator(IndexedSchemaDocuments(SchemaDocument(root)))
        repeat(100) { assertEquals(1, newNavigator().navigate(pointer).size) }
        return (1..10).minOf {
            val navigator = newNavigator()
            measureTime { navigator.navigate(pointer) }
        }
    }

    @Test
    fun navigatingIntoOnePropertyResolvesNoOthersRef() {
        val growth = timeToNavigateIntoTarget(40) / timeToNavigateIntoTarget(5)

        // growth measured 0.9 to 1.1, and 7.4 to 7.8 when every property's `$ref` was resolved
        assertTrue(growth < 3, "chains eight times as long made navigating $growth times as long")
    }
}

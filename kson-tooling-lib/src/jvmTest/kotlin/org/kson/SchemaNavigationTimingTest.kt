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
 * Checks how completion and navigation time grow with the size of the schema.  Each test divides the time for one
 * size by the time for another on the same machine, which cancels out how fast the machine is overall.
 *
 * TODO: These are not reliable timing tests.  They would probably benefit from being replaced by benchmark tests.
 */
class SchemaNavigationTimingTest : SchemaCompletionTest {
    private fun schemaOfRefProperties(count: Int): String {
        val properties = (0 until count).joinToString(",") { "\"p$it\": {\"\$ref\": \"#/definitions/d$it\"}" }
        val definitions = (0 until count).joinToString(",") {
            "\"d$it\": {\"type\": \"object\", \"properties\": {\"q\": {\"minimum\": 1}}}"
        }
        return "{\"type\": \"object\", \"properties\": {$properties}, \"definitions\": {$definitions}}"
    }

    /** The quickest of three completions of a new key in a schema of [count] `$ref` properties, once warmed up */
    private fun timeToComplete(count: Int): Duration {
        val schema = schemaOfRefProperties(count)
        repeat(3) { assertEquals(count, getCompletionsAtCaret(schema, "{\n  <caret>\n}")?.size) }
        return (1..3).minOf { measureTime { getCompletionsAtCaret(schema, "{\n  <caret>\n}") } }
    }

    @Test
    fun completingANewKeyTakesTimeInProportionToTheProperties() {
        val growth = timeToComplete(2400) / timeToComplete(300)

        assertTrue(growth < 24, "eight times the properties took $growth times as long")
    }

    /**
     * A schema with many properties whose `$ref`s lead through a chain of [chainLength] more `$ref`s, and a property
     * `target` whose `$ref` leads straight to a schema with a property `q`.
     */
    private fun schemaOfChainedProperties(chainLength: Int): String {
        val properties = (0 until 2000).joinToString(",") { "\"p$it\": {\"\$ref\": \"#/definitions/c0\"}" }
        val chain = (0 until chainLength).joinToString(",") { "\"c$it\": {\"\$ref\": \"#/definitions/c${it + 1}\"}" }
        return "{\"properties\": {$properties, \"target\": {\"\$ref\": \"#/definitions/leaf\"}}, " +
            "\"definitions\": {$chain, \"c$chainLength\": {}, \"leaf\": {\"properties\": {\"q\": {}}}}}"
    }

    /**
     * Times the first navigation into `target` over a newly indexed schema with `$ref` chains [chainLength] long, and
     * returns the shortest of ten such timings.  Later navigations over the same index reuse the `$ref`s that the
     * first navigation resolved.
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

        assertTrue(growth < 3, "chains eight times as long made navigating $growth times as long")
    }
}

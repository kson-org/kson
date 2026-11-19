package org.kson.schema

import org.kson.KsonCore
import org.kson.parser.MessageSink
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Checks how the time to index and compile a schema grows with its number of pointer `$ref`s.  The test divides the
 * time for one size by the time for another on the same machine, which cancels out how fast the machine is overall.
 *
 * TODO: This is not a reliable timing test.  It would probably benefit from being replaced by a benchmark test.
 */
class SchemaCompileTimingTest {
    /** A schema of [count] properties, each a JSON Pointer `$ref` to its own definition */
    private fun schemaOfPointerRefs(count: Int): String {
        val properties = (0 until count).joinToString(",") { "\"p$it\": {\"\$ref\": \"#/definitions/d$it\"}" }
        val definitions = (0 until count).joinToString(",") { "\"d$it\": {\"type\": \"object\"}" }
        return "{\"properties\": {$properties}, \"definitions\": {$definitions}}"
    }

    /** The quickest of five times to index and compile a schema of [count] pointer `$ref`s, once warmed up */
    private fun timeToIndexAndCompile(count: Int): Duration {
        val root = assertNotNull(KsonCore.parseToAst(schemaOfPointerRefs(count)).ksonValue)
        fun indexAndCompile() =
            assertNotNull(SchemaParser.parseSchemaRoot(IndexedSchemaDocuments(SchemaDocument(root)), MessageSink()))
        repeat(3) { indexAndCompile() }
        return (1..5).minOf { measureTime { indexAndCompile() } }
    }

    @Test
    fun indexingAndCompilingTakeTimeInProportionToThePointerRefs() {
        val growth = timeToIndexAndCompile(8000) / timeToIndexAndCompile(1000)

        // growth measured 7.3 to 9.5, and 54 to 81 when each `$ref`'s target was compared with every indexed schema
        assertTrue(growth < 24, "eight times the pointer `\$ref`s took $growth times as long")
    }
}

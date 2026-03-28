package org.kson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * End-to-end tests for bidirectional if/then completion narrowing.
 *
 * Validates the full completions pipeline: schema navigation with partial
 * document values, if/then condition evaluation, enum intersection, and the
 * fallback to full enum when no sibling is set.
 *
 * The schema uses bidirectional if/then clauses where property A narrows
 * property B and vice versa — a common pattern for interdependent enums.
 */
class BidirectionalIfThenCompletionTest : SchemaCompletionTest {

    private fun assertCompletionLabels(schema: String, doc: String, expected: List<String>, message: String) {
        val completions = getCompletionsAtCaret(schema, doc)
        assertNotNull(completions, "$message: should return completions")
        assertEquals(expected, completions.map { it.label }.sorted(), message)
    }

    private val schema = """
        {
            "type": "object",
            "properties": {
                "integration": {
                    "type": "string",
                    "enum": ["SNOWFLAKE", "DBT"]
                },
                "job": {
                    "type": "string",
                    "enum": ["SNOW_QUERY", "SNOW_TEST", "DBT_RUN", "DBT_TEST"]
                }
            },
            "allOf": [
                {
                    "if": { "properties": { "integration": { "const": "SNOWFLAKE" } }, "required": ["integration"] },
                    "then": { "properties": { "job": { "enum": ["SNOW_QUERY", "SNOW_TEST"] } } }
                },
                {
                    "if": { "properties": { "integration": { "const": "DBT" } }, "required": ["integration"] },
                    "then": { "properties": { "job": { "enum": ["DBT_RUN", "DBT_TEST"] } } }
                },
                {
                    "if": { "properties": { "job": { "const": "SNOW_QUERY" } }, "required": ["job"] },
                    "then": { "properties": { "integration": { "const": "SNOWFLAKE" } } }
                },
                {
                    "if": { "properties": { "job": { "const": "SNOW_TEST" } }, "required": ["job"] },
                    "then": { "properties": { "integration": { "const": "SNOWFLAKE" } } }
                },
                {
                    "if": { "properties": { "job": { "const": "DBT_RUN" } }, "required": ["job"] },
                    "then": { "properties": { "integration": { "const": "DBT" } } }
                },
                {
                    "if": { "properties": { "job": { "const": "DBT_TEST" } }, "required": ["job"] },
                    "then": { "properties": { "integration": { "const": "DBT" } } }
                }
            ]
        }
    """

    @Test
    fun testJobNarrowedByIntegrationSnowflake() {
        assertCompletionLabels(schema,
            """{"integration": "SNOWFLAKE", "job": "<caret>"}""",
            listOf("SNOW_QUERY", "SNOW_TEST"),
            "SNOWFLAKE integration should narrow job to SNOW_* values")
    }

    @Test
    fun testJobNarrowedByIntegrationDbt() {
        assertCompletionLabels(schema,
            """{"integration": "DBT", "job": "<caret>"}""",
            listOf("DBT_RUN", "DBT_TEST"),
            "DBT integration should narrow job to DBT_* values")
    }

    @Test
    fun testJobNarrowedWithEmptyKsonValue() {
        assertCompletionLabels(schema,
            "integration: SNOWFLAKE\njob: <caret>",
            listOf("SNOW_QUERY", "SNOW_TEST"),
            "KSON empty value should still narrow via partial AST")
    }

    @Test
    fun testJobNarrowedWithPartialKsonValue() {
        assertCompletionLabels(schema,
            "integration: SNOWFLAKE\njob: <caret>S",
            listOf("SNOW_QUERY", "SNOW_TEST"),
            "KSON partial value should narrow")
    }

    @Test
    fun testIntegrationNarrowedByJobSnowQuery() {
        assertCompletionLabels(schema,
            """{"job": "SNOW_QUERY", "integration": "<caret>"}""",
            listOf("SNOWFLAKE"),
            "SNOW_QUERY job should narrow integration to SNOWFLAKE")
    }

    @Test
    fun testIntegrationNarrowedByJobDbtRun() {
        assertCompletionLabels(schema,
            """{"job": "DBT_RUN", "integration": "<caret>"}""",
            listOf("DBT"),
            "DBT_RUN job should narrow integration to DBT")
    }

    @Test
    fun testIntegrationNarrowedWithEmptyKsonValue() {
        assertCompletionLabels(schema,
            "job: SNOW_QUERY\nintegration: <caret>",
            listOf("SNOWFLAKE"),
            "Reverse narrowing should work with KSON empty value")
    }

    @Test
    fun testIntegrationShowsAllWithoutSibling() {
        assertCompletionLabels(schema,
            """{"integration": "<caret>"}""",
            listOf("DBT", "SNOWFLAKE"),
            "Without job sibling, all integrations should be offered")
    }

    @Test
    fun testJobShowsAllWithoutSibling() {
        assertCompletionLabels(schema,
            """{"job": "<caret>"}""",
            listOf("DBT_RUN", "DBT_TEST", "SNOW_QUERY", "SNOW_TEST"),
            "Without integration sibling, all jobs should be offered")
    }
}

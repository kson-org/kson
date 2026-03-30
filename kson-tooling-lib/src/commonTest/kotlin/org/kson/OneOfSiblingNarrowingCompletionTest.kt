package org.kson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests that oneOf branches are filtered by sibling property values during
 * completion.
 *
 * When a schema uses `allOf: [base, oneOf: [...]]` to couple interdependent
 * properties (e.g., integration determines which job values are valid), setting
 * one property should narrow completions for the other — even though the
 * constraint lives on the parent oneOf branch, not on the property itself.
 */
class OneOfSiblingNarrowingCompletionTest : SchemaCompletionTest {

    private fun assertCompletionLabels(schema: String, doc: String, expected: List<String>, message: String) {
        val completions = getCompletionsAtCaret(schema, doc)
        assertNotNull(completions, "$message: should return completions")
        assertEquals(expected, completions.map { it.label }.sorted(), message)
    }

    /**
     * Schema that couples integration+job+parameters via oneOf, with shared
     * properties in a base schema — the same pattern used for pipeline task
     * definitions with many integration variants.
     */
    private val schema = """
        {
            "${'$'}defs": {
                "Base": {
                    "type": "object",
                    "properties": {
                        "name": { "type": "string" }
                    }
                },
                "AlphaParams": {
                    "type": "object",
                    "properties": { "url": { "type": "string" } }
                },
                "BetaParams": {
                    "type": "object",
                    "properties": { "query": { "type": "string" } }
                },
                "GammaRunParams": {
                    "type": "object",
                    "properties": { "script": { "type": "string" } }
                },
                "GammaTestParams": {
                    "type": "object",
                    "properties": { "suite": { "type": "string" } }
                }
            },
            "allOf": [
                { "${'$'}ref": "#/${'$'}defs/Base" },
                {
                    "oneOf": [
                        {
                            "properties": {
                                "integration": { "const": "ALPHA" },
                                "job": { "const": "ALPHA_SYNC" },
                                "parameters": { "${'$'}ref": "#/${'$'}defs/AlphaParams" }
                            },
                            "required": ["integration", "job", "parameters"]
                        },
                        {
                            "properties": {
                                "integration": { "const": "BETA" },
                                "job": { "const": "BETA_QUERY" },
                                "parameters": { "${'$'}ref": "#/${'$'}defs/BetaParams" }
                            },
                            "required": ["integration", "job", "parameters"]
                        },
                        {
                            "properties": {
                                "integration": { "const": "GAMMA" },
                                "job": { "const": "GAMMA_RUN" },
                                "parameters": { "${'$'}ref": "#/${'$'}defs/GammaRunParams" }
                            },
                            "required": ["integration", "job", "parameters"]
                        },
                        {
                            "properties": {
                                "integration": { "const": "GAMMA" },
                                "job": { "const": "GAMMA_TEST" },
                                "parameters": { "${'$'}ref": "#/${'$'}defs/GammaTestParams" }
                            },
                            "required": ["integration", "job", "parameters"]
                        }
                    ]
                }
            ]
        }
    """

    @Test
    fun testJobNarrowedByIntegrationSingleMatch() {
        assertCompletionLabels(schema,
            """{"integration": "ALPHA", "job": "<caret>"}""",
            listOf("ALPHA_SYNC"),
            "ALPHA integration should narrow job to ALPHA_SYNC only")
    }

    @Test
    fun testJobNarrowedByIntegrationMultipleMatches() {
        assertCompletionLabels(schema,
            """{"integration": "GAMMA", "job": "<caret>"}""",
            listOf("GAMMA_RUN", "GAMMA_TEST"),
            "GAMMA integration should narrow job to both GAMMA_* values")
    }

    @Test
    fun testIntegrationNarrowedByJob() {
        assertCompletionLabels(schema,
            """{"job": "BETA_QUERY", "integration": "<caret>"}""",
            listOf("BETA"),
            "BETA_QUERY job should narrow integration to BETA only")
    }

    @Test
    fun testJobShowsAllWithoutSibling() {
        assertCompletionLabels(schema,
            """{"job": "<caret>"}""",
            listOf("ALPHA_SYNC", "BETA_QUERY", "GAMMA_RUN", "GAMMA_TEST"),
            "Without integration sibling, all jobs should be offered")
    }

    @Test
    fun testIntegrationShowsAllWithoutSibling() {
        assertCompletionLabels(schema,
            """{"integration": "<caret>"}""",
            listOf("ALPHA", "BETA", "GAMMA"),
            "Without job sibling, all integrations should be offered")
    }

    @Test
    fun testParametersNarrowedByIntegrationAndJob() {
        val completions = getCompletionsAtCaret(schema,
            """{"integration": "ALPHA", "job": "ALPHA_SYNC", "parameters": { "<caret>" }}""")
        assertNotNull(completions, "Should return parameter completions")
        assertEquals(listOf("url"), completions.map { it.label }.sorted(),
            "Should offer AlphaParams properties, not all parameter types")
    }

    @Test
    fun testNarrowingWithKsonSyntax() {
        assertCompletionLabels(schema,
            "integration: ALPHA\njob: <caret>",
            listOf("ALPHA_SYNC"),
            "Narrowing should work with KSON syntax via partial value")
    }

    @Test
    fun testPropertyNameCompletionsIncludeOneOfProperties() {
        val completions = getCompletionsAtCaret(schema,
            "name: foo\n<caret>")
        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }.sorted()
        assertEquals(
            listOf("integration", "job", "parameters"),
            labels,
            "Property completions should include oneOf branch properties (name filtered out as already filled)"
        )
    }
}

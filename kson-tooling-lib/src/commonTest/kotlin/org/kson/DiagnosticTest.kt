package org.kson

import kotlin.test.*

class DiagnosticTest {

    private fun validateDocument(content: String, schemaContent: String? = null): List<DiagnosticMessage> {
        val document = KsonTooling.parse(content)
        val schema = schemaContent?.let { KsonTooling.parse(it) }
        return KsonTooling.validateDocument(document, schema)
    }

    @Test
    fun testEmptyDocumentReportsError() {
        val diagnostics = validateDocument("")
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
    }

    @Test
    fun testValidDocumentNoDiagnostics() {
        val diagnostics = validateDocument("key: \"value\"")
        assertEquals(0, diagnostics.size)
    }

    @Test
    fun testValidObjectNoDiagnostics() {
        val diagnostics = validateDocument("{ \"name\": \"test\", \"age\": 30 }")
        assertEquals(0, diagnostics.size)
    }

    @Test
    fun testValidArrayNoDiagnostics() {
        val diagnostics = validateDocument("[1, 2, 3]")
        assertEquals(0, diagnostics.size)
    }

    @Test
    fun testExtraTokensAfterValue() {
        val diagnostics = validateDocument("key: \"value\" extraValue")
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
    }

    @Test
    fun testUnclosedBrace() {
        val diagnostics = validateDocument("{ \"name\": \"test\"")
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
    }

    @Test
    fun testErrorsAndWarnings() {
        val content = """
            - {list_item: false false}
                - deceptive_indent_list_item
        """.trimIndent()
        val diagnostics = validateDocument(content)
        assertEquals(2, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
        assertEquals(DiagnosticSeverity.WARNING, diagnostics[1].severity)
    }

    @Test
    fun testDiagnosticsHaveRangeInformation() {
        val diagnostics = validateDocument("")
        assertEquals(1, diagnostics.size)
        val range = diagnostics[0].range
        assertEquals(0, range.startLine)
        assertEquals(0, range.startColumn)
        assertEquals(0, range.endLine)
        assertEquals(0, range.endColumn)
    }

    @Test
    fun testDiagnosticsHaveMessageText() {
        val diagnostics = validateDocument("")
        assertEquals(1, diagnostics.size)
        assertTrue(diagnostics[0].message.isNotEmpty(), "Diagnostic message should not be empty")
    }

    @Test
    fun testSchemaTypeMismatch() {
        val schema = """
            {
                type: object
                properties: {
                    age: { type: number }
                }
            }
        """.trimIndent()
        val diagnostics = validateDocument("{ age: \"not a number\" }", schema)
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.WARNING, diagnostics[0].severity)
    }

    @Test
    fun testSchemaMissingRequiredProperty() {
        val schema = """
            {
                type: object
                properties: {
                    name: { type: string }
                }
                required: ["name"]
            }
        """.trimIndent()
        val diagnostics = validateDocument("{ age: 30 }", schema)
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.WARNING, diagnostics[0].severity)
    }

    @Test
    fun testValidDocumentMatchingSchema() {
        val schema = """
            {
                type: object
                properties: {
                    name: { type: string }
                    age: { type: number }
                }
            }
        """.trimIndent()
        val diagnostics = validateDocument("{ name: \"Alice\", age: 30 }", schema)
        assertEquals(0, diagnostics.size)
    }

    @Test
    fun testInvalidSchemaStillReturnsParseErrors() {
        val invalidSchema = "{ this is not valid : : : }}}"
        val diagnostics = validateDocument("key: \"value\" extra", invalidSchema)
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
    }

    @Test
    fun testValidDocumentWithBrokenSchemaReturnsNoDiagnostics() {
        val invalidSchema = "{ broken schema {{{{"
        val diagnostics = validateDocument("key: \"value\"", invalidSchema)
        assertEquals(0, diagnostics.size)
    }

    @Test
    fun testNoSchemaReturnsOnlyParseErrors() {
        val diagnostics = validateDocument("key: \"value\" extra")
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
    }
}

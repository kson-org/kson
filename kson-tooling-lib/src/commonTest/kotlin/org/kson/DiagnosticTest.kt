package org.kson

import org.kson.tooling.DiagnosticMessage
import org.kson.tooling.DiagnosticSeverity
import org.kson.tooling.KsonTooling
import org.kson.tooling.Range
import kotlin.test.*

class DiagnosticTest {

    /** An unparseable schema: it has an unclosed object */
    private val schemaWithError = """
        {
          type: object
          properties: { age: { type: number } }
    """.trimIndent()

    /** A schema that parses as Kson, but describes no schema: `type` must name a type */
    private val schemaWithBadType = "{ type: 5 }"

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

    /**
     * Regression test for an issue where warnings were not included in diagnostics when schema validation was
     * requested
     */
    @Test
    fun testSchemaViolationsFollowTheDocumentsOwnMessages() {
        val schema = """
            {
                type: object
                properties: { name: { type: string } }
                required: ["age"]
            }
        """.trimIndent()
        val diagnostics = validateDocument("{ name: \"Alice\", name: \"Bob\" }", schema)
        assertEquals(2, diagnostics.size, "expected the duplicate key and the schema violation, got: $diagnostics")
        assertTrue(diagnostics[0].message.contains("Duplicate key"), diagnostics[0].message)
        assertTrue(diagnostics[1].message.contains("age"), diagnostics[1].message)
    }

    /**
     * Regression test for an issue seen in development where attempting to validate a document
     * with errors against a schema resulted in the document's parse error messages being duplicated
     */
    @Test
    fun testUnparseableDocumentMessagesNotAffectedBySchemaCheck() {
        val schema = "{ type: object }"
        val diagnostics = validateDocument("key: \"value\" extra", schema)
        assertEquals(1, diagnostics.size, "the parse error must be reported once, got: $diagnostics")
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
    }

    @Test
    fun testUnusableSchemaIsReportedAlongsideDocumentParseErrors() {
        val ksonDocWithError = "key: \"value\" extra"
        val diagnostics = validateDocument(ksonDocWithError)
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity, "should be the Kson doc's parse error")

        val diagnosticsWithBrokenSchema = validateDocument(ksonDocWithError, schemaWithError)
        assertEquals(2, diagnosticsWithBrokenSchema.size)
        assertEquals(DiagnosticSeverity.WARNING, diagnosticsWithBrokenSchema[0].severity, "the unusable schema")
        assertEquals(
            diagnostics,
            diagnosticsWithBrokenSchema.drop(1),
            "the Kson doc's own diagnostics should be untouched by the schema report"
        )
    }

    @Test
    fun testValidDocumentWithUnusableSchemaReportsTheSchemaProblem() {
        val validKsonDoc = "key: \"value\""
        assertEquals(0, validateDocument(validKsonDoc).size, "this doc has nothing to report on its own")

        val diagnostics = validateDocument(validKsonDoc, schemaWithError)
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.WARNING, diagnostics[0].severity)
        assertEquals(
            Range(0, 0, 0, 0),
            diagnostics[0].range,
            "The problem is in the schema, so this diagnostic is anchored at the start of the document"
        )
    }

    /**
     * A schema can parse perfectly well and still describe no schema, which leaves the document just
     * as unvalidated as an unparseable schema does.
     */
    @Test
    fun testSchemaThatParsesButDescribesNoSchemaIsReported() {
        val diagnostics = validateDocument("key: \"value\"", schemaWithBadType)
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.WARNING, diagnostics[0].severity)
    }

    /**
     * A schema with KSON warnings that describes a legal schema may be used
     *
     * The document breaks the schema twice on purpose: an unusable schema is reported once
     * ([testSchemaWithSeveralProblemsIsReportedOnce]), so a second violation can only come from a
     * schema that ran.
     */
    @Test
    fun testSchemaWithWarningsInItsSourceIsStillUsed() {
        val schemaWithDuplicateKey =
            "{ type: object, type: object, properties: { a: { type: number }, b: { type: number } } }"
        val diagnostics = validateDocument("""{ a: "x", b: "y" }""", schemaWithDuplicateKey)
        assertEquals(
            2, diagnostics.size,
            "expected the document's schema violations to be reported, got: $diagnostics"
        )
    }

    /**
     * An empty schema file is a routine state on the way to writing one, and every document it governs
     * is unvalidated until it says something.
     */
    @Test
    fun testEmptySchemaIsReported() {
        val diagnostics = validateDocument("key: \"value\"", "")
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.WARNING, diagnostics[0].severity)
    }

    /**
     * However many problems a schema has, the document it governs gets a single report of it: the rest
     * are the schema author's business, and belong to the schema document rather than this one.
     */
    @Test
    fun testSchemaWithSeveralProblemsIsReportedOnce() {
        val schemaWithThreeProblems = "{ type: 5, minLength: \"nope\", required: 7 }"
        val diagnostics = validateDocument("key: \"value\"", schemaWithThreeProblems)
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.WARNING, diagnostics[0].severity)
    }

    @Test
    fun testNoSchemaReturnsOnlyParseErrors() {
        val diagnostics = validateDocument("key: \"value\" extra")
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
    }
}

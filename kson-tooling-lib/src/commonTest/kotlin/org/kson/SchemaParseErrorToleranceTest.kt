package org.kson

import org.kson.tooling.CompletionKind
import org.kson.tooling.KsonTooling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A localized parse error anywhere in the schema must not disable schema-driven
 * tooling.  The entry points read the schema via
 * [org.kson.tooling.ToolingDocument.partialKsonValue], so completions and hover
 * still flow from the schema's successfully-parsed parts.
 */
class SchemaParseErrorToleranceTest : SchemaCompletionTest {

    /** Dangling `format:` (missing value) errors inside `name`'s subschema only. */
    private val schemaWithLocalizedError = """
        { type: object, properties: { name: { type: string, format: }, role: { enum: [admin, user] } } }
    """.trimIndent()

    private fun getInfoAtCaret(schema: String, documentWithCaret: String): String? {
        val caretMarker = "<caret>"
        val caretIndex = documentWithCaret.indexOf(caretMarker)
        require(caretIndex >= 0) { "Document must contain $caretMarker marker" }

        val beforeCaret = documentWithCaret.take(caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)
        val document = documentWithCaret.replace(caretMarker, "")

        return KsonTooling.getSchemaInfoAtLocation(KsonTooling.parse(document), KsonTooling.parse(schema), line, column)
    }

    @Test
    fun testPropertyCompletionsSurviveSchemaParseError() {
        val completions = getCompletionsAtCaret(schemaWithLocalizedError, "<caret>")
        assertNotNull(completions, "Errored schema should still drive property completions")
        assertEquals(
            listOf("name", "role"),
            completions.map { it.label }.sorted(),
            "Intact property names should be offered despite the dangling format:"
        )
        assertTrue(completions.all { it.kind == CompletionKind.PROPERTY }, "All should be PROPERTY completions")
    }

    @Test
    fun testEnumValueCompletionsSurviveSchemaParseError() {
        val completions = getCompletionsAtCaret(schemaWithLocalizedError, "role: <caret>")
        assertNotNull(completions, "Errored schema should still drive enum value completions")
        assertEquals(
            listOf("admin", "user"),
            completions.map { it.label }.sorted(),
            "role's enum values should survive the sibling subschema's parse error"
        )
        assertTrue(completions.all { it.kind == CompletionKind.VALUE }, "All should be VALUE completions")
    }

    @Test
    fun testHoverSurvivesSchemaParseError() {
        // role is fully intact; its enum info still resolves through the errored schema.
        val roleInfo = getInfoAtCaret(schemaWithLocalizedError, "role: <caret>admin")
        assertNotNull(roleInfo, "Hover should resolve through a schema with a localized parse error")
        assertTrue(roleInfo.contains("*Allowed values:*"), "Expected enum info, got: $roleInfo")
        assertTrue(roleInfo.contains("`admin`") && roleInfo.contains("`user`"), "Expected enum values, got: $roleInfo")

        // name's subschema contained the error; its surviving type: string still resolves.
        val nameInfo = getInfoAtCaret(schemaWithLocalizedError, "name: <caret>foo")
        assertNotNull(nameInfo, "Hover should resolve the recovered parts of the errored subschema")
        assertTrue(nameInfo.contains("*Type:* `string`"), "Expected recovered type, got: $nameInfo")
    }
}

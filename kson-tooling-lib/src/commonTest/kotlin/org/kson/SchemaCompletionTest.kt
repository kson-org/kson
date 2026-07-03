package org.kson

import org.kson.tooling.CompletionItem
import org.kson.tooling.KsonTooling

/**
 * Shared test interface for schema-driven completion tests.
 *
 * Provides a helper that parses a document with a `<caret>` marker, computes
 * the cursor position, and returns the completions at that position.
 */
interface SchemaCompletionTest {

    fun getCompletionsAtCaret(schema: String, documentWithCaret: String): List<CompletionItem>? {
        val caretMarker = "<caret>"
        val caretIndex = documentWithCaret.indexOf(caretMarker)
        require(caretIndex >= 0) { "Document must contain $caretMarker marker" }

        val beforeCaret = documentWithCaret.substring(0, caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)
        val document = documentWithCaret.replace(caretMarker, "")

        return KsonTooling.getCompletionsAtLocation(KsonTooling.parse(document), KsonTooling.parse(schema), line, column)
    }
}

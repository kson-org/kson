package org.kson

import kotlin.test.*

class ToolingDocumentTest {

    @Test
    fun testParseCreatesReusableDocument() {
        val doc = KsonTooling.parse("name: John")
        assertNotNull(doc.ksonValue, "Valid document should have a non-null ksonValue")
    }

    @Test
    fun testMultipleBuilderCallsOnSameDocument() {
        val content = """
            {
              name: "Alice"
              age: 30
            }
        """.trimIndent()
        val doc = KsonTooling.parse(content)

        // All five non-diagnostic builders produce correct results from the same document
        val symbols = KsonTooling.getDocumentSymbols(doc)
        assertEquals(1, symbols.size)
        assertEquals(DocumentSymbolKind.OBJECT, symbols[0].kind)

        val tokens = KsonTooling.getSemanticTokens(doc)
        assertTrue(tokens.isNotEmpty(), "Should produce semantic tokens")

        val foldingRanges = KsonTooling.getStructuralRanges(doc)
        assertEquals(1, foldingRanges.size, "Multi-line object should have one folding range")

        val selectionRanges = KsonTooling.getEnclosingRanges(doc, 1, 3)
        assertTrue(selectionRanges.isNotEmpty(), "Should produce selection ranges for cursor on key")

        val siblingKeys = KsonTooling.getSiblingKeys(doc, 1, 3)
        assertEquals(2, siblingKeys.size, "Should find 2 sibling keys (name, age)")
    }

    @Test
    fun testDocumentSymbolsCached() {
        val doc = KsonTooling.parse("name: John\nage: 30")
        val first = KsonTooling.getDocumentSymbols(doc)
        val second = KsonTooling.getDocumentSymbols(doc)
        assertSame(first, second, "Repeated calls should return the same cached list instance")
    }

    @Test
    fun testEmptyDocumentHasNullKsonValue() {
        val doc = KsonTooling.parse("")
        assertNull(doc.ksonValue, "Empty document should have null ksonValue")
    }

    @Test
    fun testBrokenDocumentGracefulDegradation() {
        val doc = KsonTooling.parse("{ unclosed")

        // Token-based builders still work on broken documents
        val tokens = KsonTooling.getSemanticTokens(doc)
        assertTrue(tokens.isNotEmpty(), "Semantic tokens should work on broken documents")

        val foldingRanges = KsonTooling.getStructuralRanges(doc)
        assertEquals(0, foldingRanges.size, "Broken single-line document should have no folding ranges")

        // Value-based builders return empty results gracefully
        val symbols = KsonTooling.getDocumentSymbols(doc)
        assertEquals(0, symbols.size, "Document symbols should be empty for broken documents")

        val selectionRanges = KsonTooling.getEnclosingRanges(doc, 0, 2)
        // Still returns the full-document range even when ksonValue is null
        assertEquals(1, selectionRanges.size, "Selection ranges should have document range for broken documents")

        val siblingKeys = KsonTooling.getSiblingKeys(doc, 0, 2)
        assertEquals(0, siblingKeys.size, "Sibling keys should be empty for broken documents")
    }
}

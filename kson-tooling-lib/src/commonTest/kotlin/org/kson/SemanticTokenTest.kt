package org.kson

import kotlin.test.*

class SemanticTokenTest {

    @Test
    fun testKeyVsStringDistinction() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("key: string"))

        val keyTokens = tokens.filter { it.tokenType == tooling.SemanticTokenKind.KEY }
        val stringTokens = tokens.filter { it.tokenType == tooling.SemanticTokenKind.STRING }

        assertEquals(1, keyTokens.size, "Should have 1 key token")
        assertEquals(1, stringTokens.size, "Should have 1 string token")
        assertEquals(3, keyTokens[0].length) // "key"
        assertEquals(6, stringTokens[0].length) // "string"
    }

    @Test
    fun testQuotedKeyVsQuotedString() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("""{ "key": "value" }"""))

        val keyTokens = tokens.filter { it.tokenType == tooling.SemanticTokenKind.KEY }
        val stringTokens = tokens.filter { it.tokenType == tooling.SemanticTokenKind.STRING }

        assertEquals(3, keyTokens.size, "Should have 3 key tokens (open quote, content, close quote)")
        assertEquals(3, stringTokens.size, "Should have 3 string tokens (open quote, content, close quote)")
    }

    @Test
    fun testNumberToken() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("number: 42"))

        val numberTokens = tokens.filter { it.tokenType == tooling.SemanticTokenKind.NUMBER }
        assertEquals(1, numberTokens.size, "Should have 1 number token")
        assertEquals(2, numberTokens[0].length) // "42"
    }

    @Test
    fun testBooleanAndNullKeywords() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("flag: true\nempty: null\ndisabled: false"))

        val keywords = tokens.filter { it.tokenType == tooling.SemanticTokenKind.KEYWORD }
        assertEquals(3, keywords.size, "Should have 3 keyword tokens (true, null, false)")
    }

    @Test
    fun testOperators() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("key: value"))

        val operators = tokens.filter { it.tokenType == tooling.SemanticTokenKind.OPERATOR }
        assertEquals(1, operators.size, "Should have 1 operator (colon)")
    }

    @Test
    fun testAllPunctuation() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("""{ "key": [ "v1", "v2" ] }"""))

        val operators = tokens.filter { it.tokenType == tooling.SemanticTokenKind.OPERATOR }
        // { [ , ] } = 5 operators + : = 6 total
        assertEquals(6, operators.size, "Should have operators for { : [ , ] }")
    }

    @Test
    fun testNestedObjectKeys() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("outer:\n  inner:\n    deep: value"))

        val keyTokens = tokens.filter { it.tokenType == tooling.SemanticTokenKind.KEY }
        assertEquals(3, keyTokens.size, "Should have 3 key tokens for outer, inner, deep")
    }

    @Test
    fun testSkipsWhitespaceAndEof() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("key: value"))

        assertTrue(tokens.all { it.length > 0 }, "All tokens should have positive length")
    }

    @Test
    fun testEmptyDocument() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse(""))
        assertTrue(tokens.isEmpty(), "Empty document should produce no tokens")
    }

    @Test
    fun testEmbedBlock() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("embedBlock: \$tag\n    content\n   \$\$"))

        val embedTags = tokens.filter { it.tokenType == tooling.SemanticTokenKind.EMBED_TAG }
        val embedDelims = tokens.filter { it.tokenType == tooling.SemanticTokenKind.EMBED_DELIM }

        assertEquals(1, embedTags.size, "Should have 1 embed tag token")
        assertEquals(3, embedDelims.size, "Should have 3 embed delimiter tokens (preamble newline, open delim, close delim)")

        val embedContent = tokens.filter { it.tokenType == tooling.SemanticTokenKind.EMBED_CONTENT }
        assertEquals(1, embedContent.size, "Should have 1 embed content token")
    }

    @Test
    fun testAbsolutePositions() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("key: value"))

        // "key" should start at line 0, column 0
        val keyToken = tokens.first { it.tokenType == tooling.SemanticTokenKind.KEY }
        assertEquals(0, keyToken.line)
        assertEquals(0, keyToken.column)

        // ":" should be at column 3
        val colonToken = tokens.first { it.tokenType == tooling.SemanticTokenKind.OPERATOR }
        assertEquals(0, colonToken.line)
        assertEquals(3, colonToken.column)
    }

    @Test
    fun testArrayWithDashes() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("items:\n  - first\n  - second"))

        val operators = tokens.filter { it.tokenType == tooling.SemanticTokenKind.OPERATOR }
        // colons (1) + dashes (2) = 3
        assertEquals(3, operators.size, "Should have operators for : and two -")

        val strings = tokens.filter { it.tokenType == tooling.SemanticTokenKind.STRING }
        assertEquals(2, strings.size, "Should have 2 string tokens (first, second)")
    }

    @Test
    fun testCommentTokens() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("# this is a comment\nkey: value"))

        val comments = tokens.filter { it.tokenType == tooling.SemanticTokenKind.COMMENT }
        assertEquals(1, comments.size, "Should have 1 comment token")
        assertEquals(0, comments[0].line, "Comment should be on line 0")
    }

    @Test
    fun testMixedContent() {
        val tokens = tooling.KsonTooling.getSemanticTokens(tooling.KsonTooling.parse("{\n  name: \"Alice\"\n  age: 30\n  active: true\n  role: null\n}"))

        val kinds = tokens.map { it.tokenType }.toSet()
        assertTrue(tooling.SemanticTokenKind.KEY in kinds, "Should have key tokens")
        assertTrue(tooling.SemanticTokenKind.STRING in kinds, "Should have string tokens")
        assertTrue(tooling.SemanticTokenKind.NUMBER in kinds, "Should have number tokens")
        assertTrue(tooling.SemanticTokenKind.KEYWORD in kinds, "Should have keyword tokens")
        assertTrue(tooling.SemanticTokenKind.OPERATOR in kinds, "Should have operator tokens")
    }
}

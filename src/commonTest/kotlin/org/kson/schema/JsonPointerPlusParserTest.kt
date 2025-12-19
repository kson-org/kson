package org.kson.schema

import org.kson.schema.JsonPointerPlusParser.ParsedToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonPointerPlusParserTest {

    // Basic literal token tests
    @Test
    fun `root pointer has empty token list`() {
        val result = JsonPointerPlusParser("").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertTrue(result.tokens.isEmpty())
    }

    @Test
    fun `simple literal property`() {
        val result = JsonPointerPlusParser("/foo").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(ParsedToken.Literal("foo"), result.tokens[0])
    }

    @Test
    fun `nested literal properties`() {
        val result = JsonPointerPlusParser("/users/john/email").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(ParsedToken.Literal("users"), result.tokens[0])
        assertEquals(ParsedToken.Literal("john"), result.tokens[1])
        assertEquals(ParsedToken.Literal("email"), result.tokens[2])
    }

    // Wildcard token tests
    @Test
    fun `exact asterisk is wildcard token`() {
        val result = JsonPointerPlusParser("/users/*/email").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(ParsedToken.Literal("users"), result.tokens[0])
        assertEquals(ParsedToken.Wildcard, result.tokens[1])
        assertEquals(ParsedToken.Literal("email"), result.tokens[2])
    }

    @Test
    fun `multiple wildcards`() {
        val result = JsonPointerPlusParser("/*/*/value").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(ParsedToken.Wildcard, result.tokens[0])
        assertEquals(ParsedToken.Wildcard, result.tokens[1])
        assertEquals(ParsedToken.Literal("value"), result.tokens[2])
    }

    // Glob pattern token tests
    @Test
    fun `prefix pattern with asterisk`() {
        val result = JsonPointerPlusParser("/users/admin*/role").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(ParsedToken.Literal("users"), result.tokens[0])
        assertEquals(ParsedToken.GlobPattern("admin*"), result.tokens[1])
        assertEquals(ParsedToken.Literal("role"), result.tokens[2])
    }

    @Test
    fun `suffix pattern with asterisk`() {
        val result = JsonPointerPlusParser("/users/*admin/role").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(ParsedToken.GlobPattern("*admin"), result.tokens[1])
    }

    @Test
    fun `contains pattern with asterisk`() {
        val result = JsonPointerPlusParser("/users/*admin*/role").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(ParsedToken.GlobPattern("*admin*"), result.tokens[1])
    }

    @Test
    fun `pattern with question mark`() {
        val result = JsonPointerPlusParser("/files/file?.txt").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(ParsedToken.Literal("files"), result.tokens[0])
        assertEquals(ParsedToken.GlobPattern("file?.txt"), result.tokens[1])
    }

    @Test
    fun `pattern with multiple wildcards`() {
        val result = JsonPointerPlusParser("/data/*test*case?").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(ParsedToken.GlobPattern("*test*case?"), result.tokens[1])
    }

    // Backslash escape tests
    @Test
    fun `escaped asterisk is literal`() {
        val result = JsonPointerPlusParser("/config/\\*value").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(ParsedToken.Literal("config"), result.tokens[0])
        assertEquals(ParsedToken.Literal("*value"), result.tokens[1])
    }

    @Test
    fun `escaped question mark is literal`() {
        val result = JsonPointerPlusParser("/what/\\?").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(ParsedToken.Literal("?"), result.tokens[1])
    }

    @Test
    fun `escaped backslash is literal`() {
        val result = JsonPointerPlusParser("/path/to\\\\from").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(ParsedToken.Literal("to\\from"), result.tokens[1])
    }

    @Test
    fun `mixed escaped and unescaped wildcards`() {
        val result = JsonPointerPlusParser("/prefix\\*suffix*").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        // Should be a pattern because it contains unescaped *
        assertEquals(ParsedToken.GlobPattern("prefix*suffix*"), result.tokens[0])
    }

    @Test
    fun `all wildcards escaped becomes literal`() {
        val result = JsonPointerPlusParser("/\\*\\?\\*").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(ParsedToken.Literal("*?*"), result.tokens[0])
    }

    // RFC 6901 escape compatibility tests
    @Test
    fun `RFC 6901 tilde escape still works`() {
        val result = JsonPointerPlusParser("/m~0n").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(ParsedToken.Literal("m~n"), result.tokens[0])
    }

    @Test
    fun `RFC 6901 slash escape still works`() {
        val result = JsonPointerPlusParser("/a~1b").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(ParsedToken.Literal("a/b"), result.tokens[0])
    }

    @Test
    fun `mixed RFC 6901 and backslash escapes`() {
        val result = JsonPointerPlusParser("/a~1b/c\\*d").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(ParsedToken.Literal("a/b"), result.tokens[0])
        assertEquals(ParsedToken.Literal("c*d"), result.tokens[1])
    }

    // Empty token tests
    @Test
    fun `empty tokens are allowed`() {
        val result = JsonPointerPlusParser("//").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(ParsedToken.Literal(""), result.tokens[0])
        assertEquals(ParsedToken.Literal(""), result.tokens[1])
    }

    // Error cases
    @Test
    fun `invalid start without leading slash`() {
        val result = JsonPointerPlusParser("foo/bar").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Error)
    }

    @Test
    fun `incomplete RFC escape at end`() {
        val result = JsonPointerPlusParser("/foo~").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Error)
    }

    @Test
    fun `incomplete backslash escape at end`() {
        val result = JsonPointerPlusParser("/foo\\").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Error)
    }

    @Test
    fun `invalid RFC escape sequence`() {
        val result = JsonPointerPlusParser("/foo~2bar").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Error)
    }

    @Test
    fun `invalid backslash escape sequence`() {
        val result = JsonPointerPlusParser("/foo\\x").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Error)
    }

    // Complex real-world examples
    @Test
    fun `complex example with mixed token types`() {
        val result = JsonPointerPlusParser("/users/*/roles/*admin*/permissions").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(5, result.tokens.size)
        assertEquals(ParsedToken.Literal("users"), result.tokens[0])
        assertEquals(ParsedToken.Wildcard, result.tokens[1])
        assertEquals(ParsedToken.Literal("roles"), result.tokens[2])
        assertEquals(ParsedToken.GlobPattern("*admin*"), result.tokens[3])
        assertEquals(ParsedToken.Literal("permissions"), result.tokens[4])
    }

    @Test
    fun `complex example with escapes and patterns`() {
        val result = JsonPointerPlusParser("/config/\\*special/prefix*/normal").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(4, result.tokens.size)
        assertEquals(ParsedToken.Literal("config"), result.tokens[0])
        assertEquals(ParsedToken.Literal("*special"), result.tokens[1])
        assertEquals(ParsedToken.GlobPattern("prefix*"), result.tokens[2])
        assertEquals(ParsedToken.Literal("normal"), result.tokens[3])
    }

    @Test
    fun `unicode characters in tokens`() {
        val result = JsonPointerPlusParser("/你好/*/世界").parse()
        assertTrue(result is JsonPointerPlusParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(ParsedToken.Literal("你好"), result.tokens[0])
        assertEquals(ParsedToken.Wildcard, result.tokens[1])
        assertEquals(ParsedToken.Literal("世界"), result.tokens[2])
    }
}

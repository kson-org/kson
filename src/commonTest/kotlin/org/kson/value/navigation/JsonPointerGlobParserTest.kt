package org.kson.value.navigation

import org.kson.value.navigation.json_pointer.PointerParser
import org.kson.value.navigation.json_pointer.PointerParser.Tokens
import org.kson.value.navigation.json_pointer.JsonPointerGlobParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonPointerGlobParserTest {

    // Basic literal token tests
    @Test
    fun `root pointer has empty token list`() {
        val result = JsonPointerGlobParser("").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertTrue(result.tokens.isEmpty())
    }

    @Test
    fun `simple literal property`() {
        val result = JsonPointerGlobParser("/foo").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(Tokens.Literal("foo"), result.tokens[0])
    }

    @Test
    fun `nested literal properties`() {
        val result = JsonPointerGlobParser("/users/john/email").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(Tokens.Literal("users"), result.tokens[0])
        assertEquals(Tokens.Literal("john"), result.tokens[1])
        assertEquals(Tokens.Literal("email"), result.tokens[2])
    }

    // Wildcard token tests
    @Test
    fun `exact asterisk is wildcard token`() {
        val result = JsonPointerGlobParser("/users/*/email").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(Tokens.Literal("users"), result.tokens[0])
        assertEquals(Tokens.Wildcard, result.tokens[1])
        assertEquals(Tokens.Literal("email"), result.tokens[2])
    }

    @Test
    fun `multiple wildcards`() {
        val result = JsonPointerGlobParser("/*/*/value").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(Tokens.Wildcard, result.tokens[0])
        assertEquals(Tokens.Wildcard, result.tokens[1])
        assertEquals(Tokens.Literal("value"), result.tokens[2])
    }

    // Glob pattern token tests
    @Test
    fun `prefix pattern with asterisk`() {
        val result = JsonPointerGlobParser("/users/admin*/role").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(Tokens.Literal("users"), result.tokens[0])
        assertEquals(Tokens.GlobPattern("admin*"), result.tokens[1])
        assertEquals(Tokens.Literal("role"), result.tokens[2])
    }

    @Test
    fun `suffix pattern with asterisk`() {
        val result = JsonPointerGlobParser("/users/*admin/role").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(Tokens.GlobPattern("*admin"), result.tokens[1])
    }

    @Test
    fun `contains pattern with asterisk`() {
        val result = JsonPointerGlobParser("/users/*admin*/role").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(Tokens.GlobPattern("*admin*"), result.tokens[1])
    }

    @Test
    fun `pattern with question mark`() {
        val result = JsonPointerGlobParser("/files/file?.txt").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("files"), result.tokens[0])
        assertEquals(Tokens.GlobPattern("file?.txt"), result.tokens[1])
    }

    @Test
    fun `pattern with multiple wildcards`() {
        val result = JsonPointerGlobParser("/data/*test*case?").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.GlobPattern("*test*case?"), result.tokens[1])
    }

    // Backslash escape tests
    @Test
    fun `escaped asterisk is literal`() {
        val result = JsonPointerGlobParser("/config/\\*value").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("config"), result.tokens[0])
        assertEquals(Tokens.Literal("*value"), result.tokens[1])
    }

    @Test
    fun `escaped question mark is literal`() {
        val result = JsonPointerGlobParser("/what/\\?").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("?"), result.tokens[1])
    }

    @Test
    fun `escaped backslash is literal`() {
        val result = JsonPointerGlobParser("/path/to\\\\from").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("to\\from"), result.tokens[1])
    }

    @Test
    fun `mixed escaped and unescaped wildcards`() {
        val result = JsonPointerGlobParser("/prefix\\*suffix*").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        // Should be a pattern because it contains unescaped *
        assertEquals(Tokens.GlobPattern("prefix*suffix*"), result.tokens[0])
    }

    @Test
    fun `all wildcards escaped becomes literal`() {
        val result = JsonPointerGlobParser("/\\*\\?\\*").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(Tokens.Literal("*?*"), result.tokens[0])
    }

    // RFC 6901 escape compatibility tests
    @Test
    fun `RFC 6901 tilde escape still works`() {
        val result = JsonPointerGlobParser("/m~0n").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(Tokens.Literal("m~n"), result.tokens[0])
    }

    @Test
    fun `RFC 6901 slash escape still works`() {
        val result = JsonPointerGlobParser("/a~1b").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(Tokens.Literal("a/b"), result.tokens[0])
    }

    @Test
    fun `mixed RFC 6901 and backslash escapes`() {
        val result = JsonPointerGlobParser("/a~1b/c\\*d").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("a/b"), result.tokens[0])
        assertEquals(Tokens.Literal("c*d"), result.tokens[1])
    }

    // Empty token tests
    @Test
    fun `empty tokens are allowed`() {
        val result = JsonPointerGlobParser("//").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal(""), result.tokens[0])
        assertEquals(Tokens.Literal(""), result.tokens[1])
    }

    // Error cases
    @Test
    fun `invalid start without leading slash`() {
        val result = JsonPointerGlobParser("foo/bar").parse()
        assertTrue(result is PointerParser.ParseResult.Error)
    }

    @Test
    fun `incomplete RFC escape at end`() {
        val result = JsonPointerGlobParser("/foo~").parse()
        assertTrue(result is PointerParser.ParseResult.Error)
    }

    @Test
    fun `incomplete backslash escape at end`() {
        val result = JsonPointerGlobParser("/foo\\").parse()
        assertTrue(result is PointerParser.ParseResult.Error)
    }

    @Test
    fun `invalid RFC escape sequence`() {
        val result = JsonPointerGlobParser("/foo~2bar").parse()
        assertTrue(result is PointerParser.ParseResult.Error)
    }

    @Test
    fun `invalid backslash escape sequence`() {
        val result = JsonPointerGlobParser("/foo\\x").parse()
        assertTrue(result is PointerParser.ParseResult.Error)
    }

    // Complex real-world examples
    @Test
    fun `complex example with mixed token types`() {
        val result = JsonPointerGlobParser("/users/*/roles/*admin*/permissions").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(5, result.tokens.size)
        assertEquals(Tokens.Literal("users"), result.tokens[0])
        assertEquals(Tokens.Wildcard, result.tokens[1])
        assertEquals(Tokens.Literal("roles"), result.tokens[2])
        assertEquals(Tokens.GlobPattern("*admin*"), result.tokens[3])
        assertEquals(Tokens.Literal("permissions"), result.tokens[4])
    }

    @Test
    fun `complex example with escapes and patterns`() {
        val result = JsonPointerGlobParser("/config/\\*special/prefix*/normal").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(4, result.tokens.size)
        assertEquals(Tokens.Literal("config"), result.tokens[0])
        assertEquals(Tokens.Literal("*special"), result.tokens[1])
        assertEquals(Tokens.GlobPattern("prefix*"), result.tokens[2])
        assertEquals(Tokens.Literal("normal"), result.tokens[3])
    }

    @Test
    fun `unicode characters in tokens`() {
        val result = JsonPointerGlobParser("/你好/*/世界").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(Tokens.Literal("你好"), result.tokens[0])
        assertEquals(Tokens.Wildcard, result.tokens[1])
        assertEquals(Tokens.Literal("世界"), result.tokens[2])
    }

    // RecursiveDescent token tests
    @Test
    fun `exact double asterisk is recursive descent token`() {
        val result = JsonPointerGlobParser("/users/**/email").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(Tokens.Literal("users"), result.tokens[0])
        assertEquals(Tokens.RecursiveDescent, result.tokens[1])
        assertEquals(Tokens.Literal("email"), result.tokens[2])
    }

    @Test
    fun `recursive descent at beginning`() {
        val result = JsonPointerGlobParser("/**/email").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.RecursiveDescent, result.tokens[0])
        assertEquals(Tokens.Literal("email"), result.tokens[1])
    }

    @Test
    fun `recursive descent at end`() {
        val result = JsonPointerGlobParser("/config/**").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("config"), result.tokens[0])
        assertEquals(Tokens.RecursiveDescent, result.tokens[1])
    }

    @Test
    fun `only recursive descent`() {
        val result = JsonPointerGlobParser("/**").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(1, result.tokens.size)
        assertEquals(Tokens.RecursiveDescent, result.tokens[0])
    }

    @Test
    fun `multiple recursive descent tokens`() {
        val result = JsonPointerGlobParser("/a/**/b/**/c").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(5, result.tokens.size)
        assertEquals(Tokens.Literal("a"), result.tokens[0])
        assertEquals(Tokens.RecursiveDescent, result.tokens[1])
        assertEquals(Tokens.Literal("b"), result.tokens[2])
        assertEquals(Tokens.RecursiveDescent, result.tokens[3])
        assertEquals(Tokens.Literal("c"), result.tokens[4])
    }

    @Test
    fun `recursive descent combined with wildcard`() {
        val result = JsonPointerGlobParser("/data/**/items/*").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(4, result.tokens.size)
        assertEquals(Tokens.Literal("data"), result.tokens[0])
        assertEquals(Tokens.RecursiveDescent, result.tokens[1])
        assertEquals(Tokens.Literal("items"), result.tokens[2])
        assertEquals(Tokens.Wildcard, result.tokens[3])
    }

    @Test
    fun `recursive descent combined with pattern`() {
        val result = JsonPointerGlobParser("/users/**/*admin*").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(3, result.tokens.size)
        assertEquals(Tokens.Literal("users"), result.tokens[0])
        assertEquals(Tokens.RecursiveDescent, result.tokens[1])
        assertEquals(Tokens.GlobPattern("*admin*"), result.tokens[2])
    }

    @Test
    fun `escaped double asterisk is literal`() {
        val result = JsonPointerGlobParser("/path/\\*\\*").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("path"), result.tokens[0])
        assertEquals(Tokens.Literal("**"), result.tokens[1])
    }

    @Test
    fun `triple asterisk is a pattern not recursive descent`() {
        val result = JsonPointerGlobParser("/path/***").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("path"), result.tokens[0])
        assertEquals(Tokens.GlobPattern("***"), result.tokens[1])
    }

    @Test
    fun `double asterisk with prefix is pattern`() {
        val result = JsonPointerGlobParser("/path/test**").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("path"), result.tokens[0])
        assertEquals(Tokens.GlobPattern("test**"), result.tokens[1])
    }

    @Test
    fun `double asterisk with suffix is pattern`() {
        val result = JsonPointerGlobParser("/path/**test").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(2, result.tokens.size)
        assertEquals(Tokens.Literal("path"), result.tokens[0])
        assertEquals(Tokens.GlobPattern("**test"), result.tokens[1])
    }

    @Test
    fun `complex example with all token types`() {
        val result = JsonPointerGlobParser("/api/**/v1/*/admin*/endpoints").parse()
        assertTrue(result is PointerParser.ParseResult.Success)
        assertEquals(6, result.tokens.size)
        assertEquals(Tokens.Literal("api"), result.tokens[0])
        assertEquals(Tokens.RecursiveDescent, result.tokens[1])
        assertEquals(Tokens.Literal("v1"), result.tokens[2])
        assertEquals(Tokens.Wildcard, result.tokens[3])
        assertEquals(Tokens.GlobPattern("admin*"), result.tokens[4])
        assertEquals(Tokens.Literal("endpoints"), result.tokens[5])
    }
}

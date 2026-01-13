package org.kson.value.navigation

import org.kson.value.navigation.json_pointer.JsonPointerGlob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonPointerGlobTest {

    @Test
    fun `root pointer has empty token list`() {
        val pointer = JsonPointerGlob("")
        assertTrue(pointer.tokens.isEmpty())
        assertEquals("", pointer.pointerString)
    }

    @Test
    fun `root pointer constant`() {
        assertEquals(JsonPointerGlob(""), JsonPointerGlob.ROOT)
        assertTrue(JsonPointerGlob.ROOT.tokens.isEmpty())
    }

    @Test
    fun `simple property pointer`() {
        val pointer = JsonPointerGlob("/foo")
        assertEquals(listOf("foo"), pointer.tokens)
        assertEquals("/foo", pointer.pointerString)
    }

    @Test
    fun `nested property pointer`() {
        val pointer = JsonPointerGlob("/foo/bar/baz")
        assertEquals(listOf("foo", "bar", "baz"), pointer.tokens)
    }

    @Test
    fun `wildcard token`() {
        val pointer = JsonPointerGlob("/users/*/email")
        assertEquals(listOf("users", "*", "email"), pointer.tokens)
    }

    @Test
    fun `multiple wildcards`() {
        val pointer = JsonPointerGlob("/*/*/value")
        assertEquals(listOf("*", "*", "value"), pointer.tokens)
    }

    @Test
    fun `glob pattern with asterisk prefix`() {
        val pointer = JsonPointerGlob("/users/admin*/role")
        assertEquals(listOf("users", "admin*", "role"), pointer.tokens)
    }

    @Test
    fun `glob pattern with asterisk suffix`() {
        val pointer = JsonPointerGlob("/users/*admin/role")
        assertEquals(listOf("users", "*admin", "role"), pointer.tokens)
    }

    @Test
    fun `glob pattern with question mark`() {
        val pointer = JsonPointerGlob("/files/file?.txt")
        assertEquals(listOf("files", "file?.txt"), pointer.tokens)
    }

    @Test
    fun `escaped asterisk becomes literal`() {
        val pointer = JsonPointerGlob("/config/\\*value")
        assertEquals(listOf("config", "*value"), pointer.tokens)
    }

    @Test
    fun `escaped question mark becomes literal`() {
        val pointer = JsonPointerGlob("/what/\\?")
        assertEquals(listOf("what", "?"), pointer.tokens)
    }

    @Test
    fun `escaped backslash`() {
        val pointer = JsonPointerGlob("/path/to\\\\from")
        assertEquals(listOf("path", "to\\from"), pointer.tokens)
    }

    @Test
    fun `RFC 6901 escaped slash`() {
        val pointer = JsonPointerGlob("/a~1b")
        assertEquals(listOf("a/b"), pointer.tokens)
    }

    @Test
    fun `RFC 6901 escaped tilde`() {
        val pointer = JsonPointerGlob("/m~0n")
        assertEquals(listOf("m~n"), pointer.tokens)
    }

    @Test
    fun `complex escaped pointer`() {
        val pointer = JsonPointerGlob("/foo~1bar~0baz")
        assertEquals(listOf("foo/bar~baz"), pointer.tokens)
    }

    @Test
    fun `pointer with empty tokens`() {
        val pointer = JsonPointerGlob("//")
        assertEquals(listOf("", ""), pointer.tokens)
    }

    @Test
    fun `unicode characters in pointer`() {
        val pointer = JsonPointerGlob("/你好/世界")
        assertEquals(listOf("你好", "世界"), pointer.tokens)
    }

    @Test
    fun `invalid pointer without leading slash throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointerGlob("foo/bar")
        }
        assertTrue(exception.message!!.contains("Invalid JsonPointerGlob"))
        assertTrue(exception.message!!.contains("foo/bar"))
    }

    @Test
    fun `invalid escape sequence throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointerGlob("/foo~2bar")
        }
        assertTrue(exception.message!!.contains("Invalid JsonPointerGlob"))
    }

    @Test
    fun `incomplete RFC escape at end throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointerGlob("/foo~")
        }
        assertTrue(exception.message!!.contains("Invalid JsonPointerGlob"))
    }

    @Test
    fun `incomplete backslash escape at end throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointerGlob("/foo\\")
        }
        assertTrue(exception.message!!.contains("Invalid JsonPointerGlob"))
    }

    @Test
    fun `toString returns pointer string`() {
        val pointer = JsonPointerGlob("/users/*/email")
        assertEquals("/users/*/email", pointer.toString())
    }

    @Test
    fun `equality works correctly`() {
        val pointer1 = JsonPointerGlob("/users/*/email")
        val pointer2 = JsonPointerGlob("/users/*/email")
        val pointer3 = JsonPointerGlob("/users/*/name")

        assertEquals(pointer1, pointer2)
        assertTrue(pointer1 != pointer3)
    }

    @Test
    fun `hashCode works correctly`() {
        val pointer1 = JsonPointerGlob("/users/*/email")
        val pointer2 = JsonPointerGlob("/users/*/email")

        assertEquals(pointer1.hashCode(), pointer2.hashCode())
    }

    @Test
    fun `pointers can be used in collections`() {
        val set = setOf(
            JsonPointerGlob("/users/*"),
            JsonPointerGlob("/data/*"),
            JsonPointerGlob("/users/*")  // Duplicate
        )
        assertEquals(2, set.size)
    }

    // Tests for JsonPointerGlob.fromTokens()

    @Test
    fun `fromTokens with empty list returns ROOT`() {
        val pointer = JsonPointerGlob.fromTokens(emptyList())
        assertEquals(JsonPointerGlob.ROOT, pointer)
        assertEquals("", pointer.pointerString)
    }

    @Test
    fun `fromTokens creates simple pointer`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("foo"))
        assertEquals("/foo", pointer.pointerString)
        assertEquals(listOf("foo"), pointer.tokens)
    }

    @Test
    fun `fromTokens creates nested pointer`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("users", "0", "name"))
        assertEquals("/users/0/name", pointer.pointerString)
        assertEquals(listOf("users", "0", "name"), pointer.tokens)
    }

    @Test
    fun `fromTokens with asterisk creates wildcard`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("users", "*", "email"))
        assertEquals("/users/*/email", pointer.pointerString)
        assertEquals(listOf("users", "*", "email"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes slash characters`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("a/b"))
        assertEquals("/a~1b", pointer.pointerString)
        assertEquals(listOf("a/b"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes tilde characters`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("m~n"))
        assertEquals("/m~0n", pointer.pointerString)
        assertEquals(listOf("m~n"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes asterisk in non-wildcard strings`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("*value"))
        assertEquals("/\\*value", pointer.pointerString)
        assertEquals(listOf("*value"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes question mark`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("what?"))
        assertEquals("/what\\?", pointer.pointerString)
        assertEquals(listOf("what?"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes backslash`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("to\\from"))
        assertEquals("/to\\\\from", pointer.pointerString)
        assertEquals(listOf("to\\from"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes both slash and tilde`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("a/b", "m~n"))
        assertEquals("/a~1b/m~0n", pointer.pointerString)
        assertEquals(listOf("a/b", "m~n"), pointer.tokens)
    }

    @Test
    fun `fromTokens handles empty tokens`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("", ""))
        assertEquals("//", pointer.pointerString)
        assertEquals(listOf("", ""), pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with tokens property`() {
        val originalTokens = listOf("users", "0", "roles", "admin")
        val pointer = JsonPointerGlob.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with wildcard`() {
        val originalTokens = listOf("users", "*", "email")
        val pointer = JsonPointerGlob.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with special characters`() {
        val originalTokens = listOf("a/b", "m~n", "x*y", "w?z")
        val pointer = JsonPointerGlob.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }

    // Tests for RecursiveDescent (**) token

    @Test
    fun `recursive descent token`() {
        val pointer = JsonPointerGlob("/users/**/email")
        assertEquals(listOf("users", "**", "email"), pointer.tokens)
    }

    @Test
    fun `recursive descent at beginning`() {
        val pointer = JsonPointerGlob("/**/email")
        assertEquals(listOf("**", "email"), pointer.tokens)
    }

    @Test
    fun `recursive descent at end`() {
        val pointer = JsonPointerGlob("/config/**")
        assertEquals(listOf("config", "**"), pointer.tokens)
    }

    @Test
    fun `only recursive descent token`() {
        val pointer = JsonPointerGlob("/**")
        assertEquals(listOf("**"), pointer.tokens)
    }

    @Test
    fun `multiple recursive descent tokens`() {
        val pointer = JsonPointerGlob("/a/**/b/**/c")
        assertEquals(listOf("a", "**", "b", "**", "c"), pointer.tokens)
    }

    @Test
    fun `recursive descent combined with wildcards`() {
        val pointer = JsonPointerGlob("/users/**/roles/*")
        assertEquals(listOf("users", "**", "roles", "*"), pointer.tokens)
    }

    @Test
    fun `recursive descent combined with patterns`() {
        val pointer = JsonPointerGlob("/data/**/*_test")
        assertEquals(listOf("data", "**", "*_test"), pointer.tokens)
    }

    @Test
    fun `escaped double asterisk becomes literal`() {
        val pointer = JsonPointerGlob("/path/\\*\\*")
        assertEquals(listOf("path", "**"), pointer.tokens)
    }

    @Test
    fun `fromTokens with double asterisk creates recursive descent`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("data", "**", "name"))
        assertEquals("/data/**/name", pointer.pointerString)
        assertEquals(listOf("data", "**", "name"), pointer.tokens)
    }

    @Test
    fun `fromTokens with multiple double asterisks`() {
        val pointer = JsonPointerGlob.fromTokens(listOf("a", "**", "b", "**", "c"))
        assertEquals("/a/**/b/**/c", pointer.pointerString)
        assertEquals(listOf("a", "**", "b", "**", "c"), pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with recursive descent`() {
        val originalTokens = listOf("users", "**", "profile", "*", "email")
        val pointer = JsonPointerGlob.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }

    @Test
    fun `fromTokens escapes literal double asterisk pattern`() {
        // When the string contains "**" as literal text (not as a token), it should be escaped
        val pointer = JsonPointerGlob.fromTokens(listOf("file**name"))
        // This should create a glob pattern since it contains unescaped *
        assertEquals("/file\\*\\*name", pointer.pointerString)
        assertEquals(listOf("file**name"), pointer.tokens)
    }
}
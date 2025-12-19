package org.kson.value.navigation

import org.kson.value.navigation.jsonPointer.JsonPointerPlus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonPointerPlusTest {

    @Test
    fun `root pointer has empty token list`() {
        val pointer = JsonPointerPlus("")
        assertTrue(pointer.tokens.isEmpty())
        assertEquals("", pointer.pointerString)
    }

    @Test
    fun `root pointer constant`() {
        assertEquals(JsonPointerPlus(""), JsonPointerPlus.ROOT)
        assertTrue(JsonPointerPlus.ROOT.tokens.isEmpty())
    }

    @Test
    fun `simple property pointer`() {
        val pointer = JsonPointerPlus("/foo")
        assertEquals(listOf("foo"), pointer.tokens)
        assertEquals("/foo", pointer.pointerString)
    }

    @Test
    fun `nested property pointer`() {
        val pointer = JsonPointerPlus("/foo/bar/baz")
        assertEquals(listOf("foo", "bar", "baz"), pointer.tokens)
    }

    @Test
    fun `wildcard token`() {
        val pointer = JsonPointerPlus("/users/*/email")
        assertEquals(listOf("users", "*", "email"), pointer.tokens)
    }

    @Test
    fun `multiple wildcards`() {
        val pointer = JsonPointerPlus("/*/*/value")
        assertEquals(listOf("*", "*", "value"), pointer.tokens)
    }

    @Test
    fun `glob pattern with asterisk prefix`() {
        val pointer = JsonPointerPlus("/users/admin*/role")
        assertEquals(listOf("users", "admin*", "role"), pointer.tokens)
    }

    @Test
    fun `glob pattern with asterisk suffix`() {
        val pointer = JsonPointerPlus("/users/*admin/role")
        assertEquals(listOf("users", "*admin", "role"), pointer.tokens)
    }

    @Test
    fun `glob pattern with question mark`() {
        val pointer = JsonPointerPlus("/files/file?.txt")
        assertEquals(listOf("files", "file?.txt"), pointer.tokens)
    }

    @Test
    fun `escaped asterisk becomes literal`() {
        val pointer = JsonPointerPlus("/config/\\*value")
        assertEquals(listOf("config", "*value"), pointer.tokens)
    }

    @Test
    fun `escaped question mark becomes literal`() {
        val pointer = JsonPointerPlus("/what/\\?")
        assertEquals(listOf("what", "?"), pointer.tokens)
    }

    @Test
    fun `escaped backslash`() {
        val pointer = JsonPointerPlus("/path/to\\\\from")
        assertEquals(listOf("path", "to\\from"), pointer.tokens)
    }

    @Test
    fun `RFC 6901 escaped slash`() {
        val pointer = JsonPointerPlus("/a~1b")
        assertEquals(listOf("a/b"), pointer.tokens)
    }

    @Test
    fun `RFC 6901 escaped tilde`() {
        val pointer = JsonPointerPlus("/m~0n")
        assertEquals(listOf("m~n"), pointer.tokens)
    }

    @Test
    fun `complex escaped pointer`() {
        val pointer = JsonPointerPlus("/foo~1bar~0baz")
        assertEquals(listOf("foo/bar~baz"), pointer.tokens)
    }

    @Test
    fun `pointer with empty tokens`() {
        val pointer = JsonPointerPlus("//")
        assertEquals(listOf("", ""), pointer.tokens)
    }

    @Test
    fun `unicode characters in pointer`() {
        val pointer = JsonPointerPlus("/你好/世界")
        assertEquals(listOf("你好", "世界"), pointer.tokens)
    }

    @Test
    fun `invalid pointer without leading slash throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointerPlus("foo/bar")
        }
        assertTrue(exception.message!!.contains("Invalid JsonPointerPlus"))
        assertTrue(exception.message!!.contains("foo/bar"))
    }

    @Test
    fun `invalid escape sequence throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointerPlus("/foo~2bar")
        }
        assertTrue(exception.message!!.contains("Invalid JsonPointerPlus"))
    }

    @Test
    fun `incomplete RFC escape at end throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointerPlus("/foo~")
        }
        assertTrue(exception.message!!.contains("Invalid JsonPointerPlus"))
    }

    @Test
    fun `incomplete backslash escape at end throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointerPlus("/foo\\")
        }
        assertTrue(exception.message!!.contains("Invalid JsonPointerPlus"))
    }

    @Test
    fun `toString returns pointer string`() {
        val pointer = JsonPointerPlus("/users/*/email")
        assertEquals("/users/*/email", pointer.toString())
    }

    @Test
    fun `equality works correctly`() {
        val pointer1 = JsonPointerPlus("/users/*/email")
        val pointer2 = JsonPointerPlus("/users/*/email")
        val pointer3 = JsonPointerPlus("/users/*/name")

        assertEquals(pointer1, pointer2)
        assertTrue(pointer1 != pointer3)
    }

    @Test
    fun `hashCode works correctly`() {
        val pointer1 = JsonPointerPlus("/users/*/email")
        val pointer2 = JsonPointerPlus("/users/*/email")

        assertEquals(pointer1.hashCode(), pointer2.hashCode())
    }

    @Test
    fun `pointers can be used in collections`() {
        val set = setOf(
            JsonPointerPlus("/users/*"),
            JsonPointerPlus("/data/*"),
            JsonPointerPlus("/users/*")  // Duplicate
        )
        assertEquals(2, set.size)
    }

    // Tests for JsonPointerPlus.fromTokens()

    @Test
    fun `fromTokens with empty list returns ROOT`() {
        val pointer = JsonPointerPlus.fromTokens(emptyList())
        assertEquals(JsonPointerPlus.ROOT, pointer)
        assertEquals("", pointer.pointerString)
    }

    @Test
    fun `fromTokens creates simple pointer`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("foo"))
        assertEquals("/foo", pointer.pointerString)
        assertEquals(listOf("foo"), pointer.tokens)
    }

    @Test
    fun `fromTokens creates nested pointer`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("users", "0", "name"))
        assertEquals("/users/0/name", pointer.pointerString)
        assertEquals(listOf("users", "0", "name"), pointer.tokens)
    }

    @Test
    fun `fromTokens with asterisk creates wildcard`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("users", "*", "email"))
        assertEquals("/users/*/email", pointer.pointerString)
        assertEquals(listOf("users", "*", "email"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes slash characters`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("a/b"))
        assertEquals("/a~1b", pointer.pointerString)
        assertEquals(listOf("a/b"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes tilde characters`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("m~n"))
        assertEquals("/m~0n", pointer.pointerString)
        assertEquals(listOf("m~n"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes asterisk in non-wildcard strings`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("*value"))
        assertEquals("/\\*value", pointer.pointerString)
        assertEquals(listOf("*value"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes question mark`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("what?"))
        assertEquals("/what\\?", pointer.pointerString)
        assertEquals(listOf("what?"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes backslash`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("to\\from"))
        assertEquals("/to\\\\from", pointer.pointerString)
        assertEquals(listOf("to\\from"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes both slash and tilde`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("a/b", "m~n"))
        assertEquals("/a~1b/m~0n", pointer.pointerString)
        assertEquals(listOf("a/b", "m~n"), pointer.tokens)
    }

    @Test
    fun `fromTokens handles empty tokens`() {
        val pointer = JsonPointerPlus.fromTokens(listOf("", ""))
        assertEquals("//", pointer.pointerString)
        assertEquals(listOf("", ""), pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with tokens property`() {
        val originalTokens = listOf("users", "0", "roles", "admin")
        val pointer = JsonPointerPlus.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with wildcard`() {
        val originalTokens = listOf("users", "*", "email")
        val pointer = JsonPointerPlus.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with special characters`() {
        val originalTokens = listOf("a/b", "m~n", "x*y", "w?z")
        val pointer = JsonPointerPlus.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }
}
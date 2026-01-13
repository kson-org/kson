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
}
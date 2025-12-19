package org.kson.value.navigation

import org.kson.value.navigation.jsonPointer.JsonPointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonPointerTest {

    @Test
    fun `root pointer has empty token list`() {
        val pointer = JsonPointer("")
        assertTrue(pointer.tokens.isEmpty())
        assertEquals("", pointer.pointerString)
    }

    @Test
    fun `root pointer constant`() {
        assertEquals(JsonPointer(""), JsonPointer.ROOT)
        assertTrue(JsonPointer.ROOT.tokens.isEmpty())
    }

    @Test
    fun `simple property pointer`() {
        val pointer = JsonPointer("/foo")
        assertEquals(listOf("foo"), pointer.tokens)
        assertEquals("/foo", pointer.pointerString)
    }

    @Test
    fun `nested property pointer`() {
        val pointer = JsonPointer("/foo/bar/baz")
        assertEquals(listOf("foo", "bar", "baz"), pointer.tokens)
    }

    @Test
    fun `array index pointer`() {
        val pointer = JsonPointer("/users/0")
        assertEquals(listOf("users", "0"), pointer.tokens)
    }

    @Test
    fun `escaped slash in pointer`() {
        val pointer = JsonPointer("/a~1b")
        assertEquals(listOf("a/b"), pointer.tokens)
    }

    @Test
    fun `escaped tilde in pointer`() {
        val pointer = JsonPointer("/m~0n")
        assertEquals(listOf("m~n"), pointer.tokens)
    }

    @Test
    fun `complex escaped pointer`() {
        val pointer = JsonPointer("/foo~1bar~0baz")
        assertEquals(listOf("foo/bar~baz"), pointer.tokens)
    }

    @Test
    fun `pointer with empty tokens`() {
        val pointer = JsonPointer("//")
        assertEquals(listOf("", ""), pointer.tokens)
    }

    @Test
    fun `unicode characters in pointer`() {
        val pointer = JsonPointer("/你好/世界")
        assertEquals(listOf("你好", "世界"), pointer.tokens)
    }

    @Test
    fun `schema definition pointer`() {
        val pointer = JsonPointer("/definitions/address/properties/street")
        assertEquals(listOf("definitions", "address", "properties", "street"), pointer.tokens)
    }

    @Test
    fun `invalid pointer without leading slash throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointer("foo/bar")
        }
        assertTrue(exception.message!!.contains("Invalid JSON Pointer"))
        assertTrue(exception.message!!.contains("foo/bar"))
    }

    @Test
    fun `invalid escape sequence throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointer("/foo~2bar")
        }
        assertTrue(exception.message!!.contains("Invalid JSON Pointer"))
    }

    @Test
    fun `incomplete escape at end throws exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonPointer("/foo~")
        }
        assertTrue(exception.message!!.contains("Invalid JSON Pointer"))
    }

    @Test
    fun `toString returns pointer string`() {
        val pointer = JsonPointer("/foo/bar")
        assertEquals("/foo/bar", pointer.toString())
    }

    @Test
    fun `data class equality works correctly`() {
        val pointer1 = JsonPointer("/foo/bar")
        val pointer2 = JsonPointer("/foo/bar")
        val pointer3 = JsonPointer("/foo/baz")

        assertEquals(pointer1, pointer2)
        assertTrue(pointer1 != pointer3)
    }

    @Test
    fun `data class hashCode works correctly`() {
        val pointer1 = JsonPointer("/foo/bar")
        val pointer2 = JsonPointer("/foo/bar")

        assertEquals(pointer1.hashCode(), pointer2.hashCode())
    }

    @Test
    fun `pointers can be used in collections`() {
        val set = setOf(
            JsonPointer("/foo"),
            JsonPointer("/bar"),
            JsonPointer("/foo")  // Duplicate
        )
        assertEquals(2, set.size)
    }

    // Tests for JsonPointer.fromTokens()

    @Test
    fun `fromTokens with empty list returns ROOT`() {
        val pointer = JsonPointer.fromTokens(emptyList())
        assertEquals(JsonPointer.ROOT, pointer)
        assertEquals("", pointer.pointerString)
    }

    @Test
    fun `fromTokens creates simple pointer`() {
        val pointer = JsonPointer.fromTokens(listOf("foo"))
        assertEquals("/foo", pointer.pointerString)
        assertEquals(listOf("foo"), pointer.tokens)
    }

    @Test
    fun `fromTokens creates nested pointer`() {
        val pointer = JsonPointer.fromTokens(listOf("users", "0", "name"))
        assertEquals("/users/0/name", pointer.pointerString)
        assertEquals(listOf("users", "0", "name"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes slash characters`() {
        val pointer = JsonPointer.fromTokens(listOf("a/b"))
        assertEquals("/a~1b", pointer.pointerString)
        assertEquals(listOf("a/b"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes tilde characters`() {
        val pointer = JsonPointer.fromTokens(listOf("m~n"))
        assertEquals("/m~0n", pointer.pointerString)
        assertEquals(listOf("m~n"), pointer.tokens)
    }

    @Test
    fun `fromTokens escapes both slash and tilde`() {
        val pointer = JsonPointer.fromTokens(listOf("a/b", "m~n"))
        assertEquals("/a~1b/m~0n", pointer.pointerString)
        assertEquals(listOf("a/b", "m~n"), pointer.tokens)
    }

    @Test
    fun `fromTokens handles empty tokens`() {
        val pointer = JsonPointer.fromTokens(listOf("", ""))
        assertEquals("//", pointer.pointerString)
        assertEquals(listOf("", ""), pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with tokens property`() {
        val originalTokens = listOf("users", "0", "roles", "admin")
        val pointer = JsonPointer.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }

    @Test
    fun `fromTokens round-trips with special characters`() {
        val originalTokens = listOf("a/b", "m~n", "x~1y")
        val pointer = JsonPointer.fromTokens(originalTokens)
        assertEquals(originalTokens, pointer.tokens)
    }
}

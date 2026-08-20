package org.kson.stdlibx.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ImmutableTest {
    @Test
    fun testImmutableListSymmetricallyEqualsAnEqualList() {
        val immutable = listOf("a", "b").toImmutableList()

        assertEquals(listOf("a", "b"), immutable)
        assertEquals(immutable, listOf("a", "b"))
        assertEquals(listOf("a", "b").hashCode(), immutable.hashCode())
    }

    @Test
    fun testEmptyImmutableListSymmetricallyEqualsAnEmptyList() {
        val immutable = emptyList<String>().toImmutableList()

        assertEquals(emptyList(), immutable)
        assertEquals(immutable, emptyList())
    }

    @Test
    fun testImmutableListDiffersFromADifferentList() {
        assertNotEquals(listOf("a"), listOf("a", "b").toImmutableList())
    }

    @Test
    fun testImmutableListRendersItsContentsRatherThanItsIdentity() {
        assertEquals(listOf("a", "b").toString(), listOf("a", "b").toImmutableList().toString())
    }

    @Test
    fun testImmutableMapSymmetricallyEqualsAnEqualMap() {
        val immutable = mapOf("a" to 1).toImmutableMap()

        assertEquals(mapOf("a" to 1), immutable)
        assertEquals(immutable, mapOf("a" to 1))
        assertEquals(mapOf("a" to 1).hashCode(), immutable.hashCode())
    }

    @Test
    fun testImmutableMapDiffersFromADifferentMap() {
        assertNotEquals(mapOf("a" to 2), mapOf("a" to 1).toImmutableMap())
    }

    @Test
    fun testImmutableMapRendersItsContentsRatherThanItsIdentity() {
        assertEquals(mapOf("a" to 1).toString(), mapOf("a" to 1).toImmutableMap().toString())
    }
}

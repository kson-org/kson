package org.kson

import kotlin.test.Test
import kotlin.test.assertEquals

class KsonTest {
    @Test
    fun sanityCheck() {
        // TODO make this placeholder a real test
        val source = "some: source"
        val result = Kson().parse(source)
        assertEquals("""
            {
              some: source
            }
        """.trimIndent(), result.debugPrint())
    }
}
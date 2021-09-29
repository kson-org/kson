package org.kson

import kotlin.test.Test
import kotlin.test.assertEquals

class KsonTest {
    @Test
    fun sanityCheck() {
        // TODO make this placeholder a real test
        val src = "some source"
        val result = Kson().parse(src)
        assertEquals("placeholder parse: $src", result)
    }
}
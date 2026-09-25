package org.kson.walker

import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.JsonPointer
import kotlin.test.*

class TreePointerTest {

    private fun pointer(pointerString: String) = TreePointer<KsonValue>(JsonPointer(pointerString))

    @Test
    fun testChildAppendsToken() {
        assertEquals(pointer("/tags/0"), pointer("/tags").child("0"))
    }

    @Test
    fun testChildEscapesToken() {
        assertEquals(pointer("/a~1b"), pointer("").child("a/b"))
    }

    @Test
    fun testParentDropsLastToken() {
        assertEquals(pointer("/tags"), pointer("/tags/0").parent())
    }

    @Test
    fun testRootHasNoParent() {
        assertNull(pointer("").parent())
    }
}

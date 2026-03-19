package org.kson.walker

import org.kson.KsonCore
import org.kson.value.*
import kotlin.test.*

/**
 * Tests that [KsonValueWalker] correctly delegates to the [KsonValue] sealed hierarchy.
 */
class KsonValueWalkerTest {

    private val walker = KsonValueWalker

    private fun parse(source: String): KsonValue =
        KsonCore.parseToAst(source).ksonValue
            ?: error("Parse failed")

    @Test
    fun testIsObject() {
        val value = parse("name: Alice")
        assertTrue(walker.isObject(value))
        assertFalse(walker.isArray(value))
    }

    @Test
    fun testIsArray() {
        val value = parse("- one\n- two")
        assertTrue(walker.isArray(value))
        assertFalse(walker.isObject(value))
    }

    @Test
    fun testPrimitivesAreLeaves() {
        val value = parse("str: hello\nnum: 42\nbool: true\nnullVal: null")
        val props = walker.getObjectProperties(value)
        for ((_, child) in props) {
            assertFalse(walker.isObject(child))
            assertFalse(walker.isArray(child))
            assertEquals(emptyList(), walker.getObjectProperties(child))
            assertEquals(emptyList(), walker.getArrayElements(child))
        }
    }

    @Test
    fun testGetObjectProperties() {
        val value = parse("name: Alice\nage: 30")
        val props = walker.getObjectProperties(value)
        assertEquals(2, props.size)
        assertEquals("name", props[0].name)
        assertEquals("age", props[1].name)
        assertIs<KsonString>(props[0].value)
        assertIs<KsonNumber>(props[1].value)
    }

    @Test
    fun testGetObjectPropertiesOnNonObject() {
        val value = parse("- one\n- two")
        assertEquals(emptyList(), walker.getObjectProperties(value))
    }

    @Test
    fun testGetArrayElements() {
        val value = parse("- one\n- two\n- three")
        val elements = walker.getArrayElements(value)
        assertEquals(
            listOf("one", "two", "three"),
            elements.map { walker.getStringValue(it) }
        )
    }

    @Test
    fun testGetArrayElementsOnNonArray() {
        val value = parse("key: value")
        assertEquals(emptyList(), walker.getArrayElements(value))
    }

    @Test
    fun testGetStringValue() {
        val obj = parse("key: hello") as KsonObject
        val stringValue = obj.propertyLookup["key"]!!
        assertEquals("hello", walker.getStringValue(stringValue))
    }

    @Test
    fun testGetStringValueOnNonString() {
        val obj = parse("key: 42") as KsonObject
        val numValue = obj.propertyLookup["key"]!!
        assertNull(walker.getStringValue(numValue))
    }

    @Test
    fun testEmbedBlockIsTreatedAsLeaf() {
        val obj = parse("content: %\n  hello\n%%") as KsonObject
        val embedValue = obj.propertyLookup["content"]!!
        // EmbedBlock is not an object or array — it's a leaf from the walker's perspective
        assertFalse(walker.isObject(embedValue))
        assertFalse(walker.isArray(embedValue))
        assertIs<EmbedBlock>(embedValue)
        // No children to enumerate
        assertEquals(emptyList(), walker.getObjectProperties(embedValue))
        assertEquals(emptyList(), walker.getArrayElements(embedValue))
    }

    @Test
    fun testGetLocation() {
        val value = parse("name: Alice")
        val location = walker.getLocation(value)
        assertEquals(0, location.start.line)
        assertEquals(0, location.start.column)
    }
}

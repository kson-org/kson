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
    fun testObjectNode() {
        val value = parse("name: Alice")
        assertIs<NodeChildren.Object<KsonValue>>(walker.getChildren(value))
    }

    @Test
    fun testArrayNode() {
        val value = parse("- one\n- two")
        assertIs<NodeChildren.Array<KsonValue>>(walker.getChildren(value))
    }

    @Test
    fun testPrimitivesAreLeaves() {
        val value = parse("str: hello\nnum: 42\nbool: true\nnullVal: null")
        val children = walker.getChildren(value)
        assertIs<NodeChildren.Object<KsonValue>>(children)
        for ((_, child) in children.properties) {
            assertIs<NodeChildren.Leaf>(walker.getChildren(child))
        }
    }

    @Test
    fun testGetObjectProperties() {
        val value = parse("name: Alice\nage: 30")
        val children = walker.getChildren(value)
        assertIs<NodeChildren.Object<KsonValue>>(children)
        assertEquals(2, children.properties.size)
        assertEquals("name", children.properties[0].name)
        assertEquals("age", children.properties[1].name)
        assertIs<KsonString>(children.properties[0].value)
        assertIs<KsonNumber>(children.properties[1].value)
    }

    @Test
    fun testArrayNodeIsNotObject() {
        val value = parse("- one\n- two")
        assertIsNot<NodeChildren.Object<KsonValue>>(walker.getChildren(value))
    }

    @Test
    fun testGetArrayElements() {
        val value = parse("- one\n- two\n- three")
        val children = walker.getChildren(value)
        assertIs<NodeChildren.Array<KsonValue>>(children)
        assertEquals(
            listOf("one", "two", "three"),
            children.elements.map { (it as KsonString).value }
        )
    }

    @Test
    fun testObjectNodeIsNotArray() {
        val value = parse("key: value")
        assertIsNot<NodeChildren.Array<KsonValue>>(walker.getChildren(value))
    }

    @Test
    fun testEmbedBlockIsTreatedAsLeaf() {
        val obj = parse("content: %\n  hello\n%%") as KsonObject
        val embedValue = obj.propertyLookup["content"]!!
        assertIs<EmbedBlock>(embedValue)
        assertIs<NodeChildren.Leaf>(walker.getChildren(embedValue))
    }

    @Test
    fun testGetLocation() {
        val value = parse("name: Alice")
        val location = walker.getLocation(value)
        assertEquals(0, location.start.line)
        assertEquals(0, location.start.column)
    }
}

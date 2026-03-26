package org.kson.walker

import org.kson.CoreCompileConfig
import org.kson.KsonCore
import org.kson.ast.*
import kotlin.test.*

/**
 * Tests that [AstNodeWalker] correctly navigates AST nodes, including
 * [AstNodeError] nodes produced by error-tolerant parsing.
 *
 * Parallels [KsonValueWalkerTest] for the `KsonValue` walker, but also
 * covers the error-node scenarios that only apply to the AST layer.
 */
class AstNodeWalkerTest {

    private val walker = AstNodeWalker

    /** Parse with ignoreErrors to get an AST that may contain error nodes. */
    private fun parseAst(source: String): AstNode? =
        (KsonCore.parseToAst(source, CoreCompileConfig(ignoreErrors = true)).ast as? KsonRootImpl)?.rootNode

    /** Parse strictly — asserts no error nodes. */
    private fun parseValidAst(source: String): AstNode =
        (KsonCore.parseToAst(source).ast as KsonRootImpl).rootNode

    @Test
    fun testObjectNode() {
        val node = parseValidAst("name: Alice")
        assertIs<NodeChildren.Object<AstNode>>(walker.getChildren(node))
    }

    @Test
    fun testArrayNode() {
        val node = parseValidAst("- one\n- two")
        assertIs<NodeChildren.Array<AstNode>>(walker.getChildren(node))
    }

    @Test
    fun testStringValueNode() {
        val obj = parseValidAst("key: hello")
        val children = walker.getChildren(obj)
        assertIs<NodeChildren.Object<AstNode>>(children)
        assertIs<StringNodeImpl>(children.properties[0].value)
    }

    @Test
    fun testNumberValueNode() {
        val obj = parseValidAst("key: 42")
        val children = walker.getChildren(obj)
        assertIs<NodeChildren.Object<AstNode>>(children)
        assertIs<NumberNode>(children.properties[0].value)
    }

    @Test
    fun testBooleanValueNode() {
        val obj = parseValidAst("key: true")
        val children = walker.getChildren(obj)
        assertIs<NodeChildren.Object<AstNode>>(children)
        assertIs<BooleanNode>(children.properties[0].value)
    }

    @Test
    fun testNullValueNode() {
        val obj = parseValidAst("key: null")
        val children = walker.getChildren(obj)
        assertIs<NodeChildren.Object<AstNode>>(children)
        assertIs<NullNode>(children.properties[0].value)
    }

    @Test
    fun testGetObjectProperties() {
        val node = parseValidAst("name: Alice\nage: 30")
        val children = walker.getChildren(node)
        assertIs<NodeChildren.Object<AstNode>>(children)
        assertEquals(2, children.properties.size)
        assertEquals("name", children.properties[0].name)
        assertEquals("age", children.properties[1].name)
        assertIs<StringNodeImpl>(children.properties[0].value)
        assertIs<NumberNode>(children.properties[1].value)
    }

    @Test
    fun testArrayNodeIsNotObject() {
        val node = parseValidAst("- one\n- two")
        assertIsNot<NodeChildren.Object<AstNode>>(walker.getChildren(node))
    }

    @Test
    fun testGetArrayElements() {
        val node = parseValidAst("- one\n- two\n- three")
        val children = walker.getChildren(node)
        assertIs<NodeChildren.Array<AstNode>>(children)
        assertEquals(
            listOf("one", "two", "three"),
            children.elements.map { (it as StringNodeImpl).processedStringContent }
        )
    }

    @Test
    fun testObjectNodeIsNotArray() {
        val node = parseValidAst("key: value")
        assertIsNot<NodeChildren.Array<AstNode>>(walker.getChildren(node))
    }

    @Test
    fun testQuotedPropertyKey() {
        val obj = parseValidAst(""""my key": value""")
        val children = walker.getChildren(obj)
        assertIs<NodeChildren.Object<AstNode>>(children)
        assertEquals(1, children.properties.size)
        assertEquals("my key", children.properties[0].name)
    }

    @Test
    fun testQuotedPropertyKeyWithEscapes() {
        val obj = parseValidAst(""""key\twith\ttabs": value""")
        val children = walker.getChildren(obj)
        assertIs<NodeChildren.Object<AstNode>>(children)
        assertEquals(1, children.properties.size)
        assertEquals("key\twith\ttabs", children.properties[0].name)
    }

    @Test
    fun testGetLocation() {
        val node = parseValidAst("name: Alice")
        val location = walker.getLocation(node)
        assertEquals(0, location.start.line)
        assertEquals(0, location.start.column)
    }

    @Test
    fun testErrorPropertySkippedValidSiblingAccessible() {
        // {"key": , "other": 42} — first property is malformed (missing value),
        // produces ObjectPropertyNodeError which the walker skips
        val node = parseAst("""{"key": , "other": 42}""")
        assertNotNull(node)
        val children = walker.getChildren(node)
        assertIs<NodeChildren.Object<AstNode>>(children)
        assertEquals(1, children.properties.size)
        assertEquals("other", children.properties[0].name)
        assertIs<NumberNode>(children.properties[0].value)
    }

    @Test
    fun testNestedErrorPropertyPreservesOuterStructure() {
        // Error inside a nested object — outer structure is intact
        val node = parseAst("""{"outer": {"inner": }}""")
        assertNotNull(node)
        val outerChildren = walker.getChildren(node)
        assertIs<NodeChildren.Object<AstNode>>(outerChildren)
        assertEquals(1, outerChildren.properties.size)
        assertEquals("outer", outerChildren.properties[0].name)
        // The inner object has an error property, so getChildren returns empty properties
        val innerChildren = walker.getChildren(outerChildren.properties[0].value)
        assertIs<NodeChildren.Object<AstNode>>(innerChildren)
        assertEquals(0, innerChildren.properties.size)
    }

    @Test
    fun testCompletelyUnparseableDocumentReturnsNull() {
        // Some inputs produce KsonRootError — parseAst returns null
        val node = parseAst("[1, , 3]")
        assertNull(node)
    }

    @Test
    fun testErrorNodeIsLeaf() {
        val errorNode = AstNodeError(emptyList())
        assertIs<NodeChildren.Leaf>(walker.getChildren(errorNode))
    }
}

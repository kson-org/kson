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
    fun testIsObject() {
        val node = parseValidAst("name: Alice")
        assertTrue(walker.isObject(node))
        assertFalse(walker.isArray(node))
    }

    @Test
    fun testIsArray() {
        val node = parseValidAst("- one\n- two")
        assertTrue(walker.isArray(node))
        assertFalse(walker.isObject(node))
    }

    @Test
    fun testIsString() {
        val obj = parseValidAst("key: hello")
        val props = walker.getObjectProperties(obj)
        assertIs<StringNodeImpl>(props[0].value)
    }

    @Test
    fun testIsNumber() {
        val obj = parseValidAst("key: 42")
        val props = walker.getObjectProperties(obj)
        assertIs<NumberNode>(props[0].value)
    }

    @Test
    fun testIsBoolean() {
        val obj = parseValidAst("key: true")
        val props = walker.getObjectProperties(obj)
        assertIs<BooleanNode>(props[0].value)
    }

    @Test
    fun testIsNull() {
        val obj = parseValidAst("key: null")
        val props = walker.getObjectProperties(obj)
        assertIs<NullNode>(props[0].value)
    }

    @Test
    fun testGetObjectProperties() {
        val node = parseValidAst("name: Alice\nage: 30")
        val props = walker.getObjectProperties(node)
        assertEquals(2, props.size)
        assertEquals("name", props[0].name)
        assertEquals("age", props[1].name)
        assertIs<StringNodeImpl>(props[0].value)
        assertIs<NumberNode>(props[1].value)
    }

    @Test
    fun testGetObjectPropertiesOnNonObject() {
        val node = parseValidAst("- one\n- two")
        assertEquals(emptyList(), walker.getObjectProperties(node))
    }

    @Test
    fun testGetArrayElements() {
        val node = parseValidAst("- one\n- two\n- three")
        val elements = walker.getArrayElements(node)
        assertEquals(
            listOf("one", "two", "three"),
            elements.map { walker.getStringValue(it) }
        )
    }

    @Test
    fun testGetArrayElementsOnNonArray() {
        val node = parseValidAst("key: value")
        assertEquals(emptyList(), walker.getArrayElements(node))
    }

    @Test
    fun testGetStringValueUnquoted() {
        val obj = parseValidAst("key: hello")
        val value = walker.getObjectProperties(obj)[0].value
        assertEquals("hello", walker.getStringValue(value))
    }

    @Test
    fun testGetStringValueQuoted() {
        val obj = parseValidAst("""key: "hello world"""")
        val value = walker.getObjectProperties(obj)[0].value
        assertEquals("hello world", walker.getStringValue(value))
    }

    @Test
    fun testGetStringValueOnNonString() {
        val obj = parseValidAst("key: 42")
        val value = walker.getObjectProperties(obj)[0].value
        assertNull(walker.getStringValue(value))
    }

    @Test
    fun testGetStringValueWithEscapes() {
        val obj = parseValidAst("""key: "hello\nworld"""")
        val value = walker.getObjectProperties(obj)[0].value
        assertEquals("hello\nworld", walker.getStringValue(value))
    }

    @Test
    fun testQuotedPropertyKey() {
        val obj = parseValidAst(""""my key": value""")
        val props = walker.getObjectProperties(obj)
        assertEquals(1, props.size)
        assertEquals("my key", props[0].name)
    }

    @Test
    fun testQuotedPropertyKeyWithEscapes() {
        val obj = parseValidAst(""""key\twith\ttabs": value""")
        val props = walker.getObjectProperties(obj)
        assertEquals(1, props.size)
        assertEquals("key\twith\ttabs", props[0].name)
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
        assertTrue(walker.isObject(node))
        val props = walker.getObjectProperties(node)
        assertEquals(1, props.size)
        assertEquals("other", props[0].name)
        assertIs<NumberNode>(props[0].value)
    }

    @Test
    fun testNestedErrorPropertyPreservesOuterStructure() {
        // Error inside a nested object — outer structure is intact
        val node = parseAst("""{"outer": {"inner": }}""")
        assertNotNull(node)
        val outerProps = walker.getObjectProperties(node)
        assertEquals(1, outerProps.size)
        assertEquals("outer", outerProps[0].name)
        // The inner object has an error property, so getObjectProperties returns empty
        assertTrue(walker.isObject(outerProps[0].value))
        val innerProps = walker.getObjectProperties(outerProps[0].value)
        assertEquals(0, innerProps.size)
    }

    @Test
    fun testCompletelyUnparseableDocumentReturnsNull() {
        // Some inputs produce KsonRootError — parseAst returns null
        val node = parseAst("[1, , 3]")
        assertNull(node)
    }

    @Test
    fun testErrorNodeIsLeaf() {
        // Direct AstNodeError: not an object or array, no children
        val errorNode = AstNodeError(emptyList())
        assertFalse(walker.isObject(errorNode))
        assertFalse(walker.isArray(errorNode))
        assertEquals(emptyList(), walker.getObjectProperties(errorNode))
        assertEquals(emptyList(), walker.getArrayElements(errorNode))
        assertNull(walker.getStringValue(errorNode))
    }
}

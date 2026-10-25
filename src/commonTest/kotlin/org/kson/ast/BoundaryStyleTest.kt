package org.kson.ast

import org.kson.KsonCore
import org.kson.ast.BoundaryStyle.DELIMITED
import org.kson.ast.BoundaryStyle.PLAIN
import org.kson.parser.LoggedMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoundaryStyleTest {

    @Test
    fun testObjectBoundaryStyle() {
        assertEquals(PLAIN, parseObject("key: value").style)
        assertEquals(DELIMITED, parseObject("{key: value}").style)
        assertEquals(
            DELIMITED, parseObject("{}").style,
            "the only way to write an empty object is with delimiters"
        )
    }

    @Test
    fun testListBoundaryStyle() {
        assertEquals(PLAIN, parseList("- item").style)
        assertEquals(DELIMITED, parseList("<- item>").style)
        assertEquals(DELIMITED, parseList("[item]").style)
        assertEquals(
            DELIMITED, parseList("[]").style,
            "the only way to write an empty list is with delimiters"
        )
    }

    @Test
    fun testNestedBoundaryStyleIsTrackedPerObjectAndList() {
        val plainOuter = parseObject(
            """
                delimited_object: {nested: value}
            """.trimIndent()
        )
        assertEquals(PLAIN, plainOuter.style)
        assertEquals(DELIMITED, assertIs<ObjectNode>(firstPropertyValue(plainOuter)).style)

        val delimitedOuter = parseObject(
            """
                {plain_object: nested: value}
            """.trimIndent()
        )
        assertEquals(DELIMITED, delimitedOuter.style)
        assertEquals(PLAIN, assertIs<ObjectNode>(firstPropertyValue(delimitedOuter)).style)

        val plainOuterWithList = parseObject(
            """
                delimited_list: [nested]
            """.trimIndent()
        )
        assertEquals(PLAIN, plainOuterWithList.style)
        assertEquals(DELIMITED, assertIs<ListNode>(firstPropertyValue(plainOuterWithList)).style)
    }

    private fun firstPropertyValue(objectNode: ObjectNode): KsonValueNode =
        assertIs<ObjectPropertyNodeImpl>(objectNode.properties.first()).value

    private fun parseObject(source: String): ObjectNode = assertIs<ObjectNode>(parseRootNode(source))

    private fun parseList(source: String): ListNode = assertIs<ListNode>(parseRootNode(source))

    private fun parseRootNode(source: String): KsonValueNode {
        val parseResult = KsonCore.parseToAst(source)
        assertTrue(
            parseResult.messages.isEmpty(),
            "this test's sources should parse cleanly, but this one did not:\n" +
                    LoggedMessage.print(parseResult.messages)
        )
        return assertIs<KsonRootImpl>(parseResult.ast).rootNode
    }
}

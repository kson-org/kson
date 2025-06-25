package org.kson.ast

import org.kson.Kson
import org.kson.parser.NumberParser.ParsedNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KsonApiTest {
    @Test
    fun testKsonApiBasicUsage() {
        val source = """
          host: "0.0.0.0"
          port: 8080
        """.trimIndent()

        val parseResult = Kson.parseToAst(source)
        val ast = parseResult.ast
        assertNotNull(ast, "should not be null for this test's valid Kson")

        val serverConfig = ast.toKsonApi()
        assertTrue(serverConfig is KsonObject)

        assertTrue(serverConfig.propertyMap["host"] is KsonString)
        assertTrue(serverConfig.propertyMap["port"] is KsonNumber)
    }

    /**
     * Exercise all [KsonApi] classes by parsing and inspecting a document featuring all [KsonValue]s
     * with some non-trivial nesting
     */
    @Test
    fun testKsonApiComprehensive() {
        val source = """
            {
              stringVal: "hello world"
              numberVal: 42.5
              booleanVal: true
              nullVal: null
              embedBlock: %
              This is an embed block
              with multiple lines
              %%
              nestedObject: {
                key1: "value1"
                key2: {
                  deepKey: "deep value"
                }
              }
              nestedList: [
                "string element",
                42,
                true,
                null,
                {
                  objectInList: "value"
                },
                [
                  "nested",
                  "list",
                  "elements"
                ]
              ]
            }
        """.trimIndent()

        val parseResult = Kson.parseToAst(source)
        assertTrue(!parseResult.hasErrors(), "Parse should succeed without errors")

        val objectNode = parseResult.ast!!.toKsonApi()
        assertTrue(objectNode is KsonObject, "Root node should be ObjectNodeApi")

        val properties = objectNode.propertyMap

        // Verify string value
        val stringProp = properties["stringVal"]!!
        assertTrue(stringProp is KsonString)
        assertEquals("hello world", stringProp.value)

        // Verify number value
        val numberProp = properties["numberVal"]!!
        assertTrue(numberProp is KsonNumber)
        assertEquals(42.5, (numberProp.value as ParsedNumber.Decimal).value)

        // Verify boolean value
        val booleanProp = properties["booleanVal"]!!
        assertTrue(booleanProp is KsonBoolean)
        assertEquals(true, booleanProp.value)

        // Verify null value
        val nullProp = properties["nullVal"]!!
        assertTrue(nullProp is KsonNull)

        // Verify embed block
        val embedProp = properties["embedBlock"]!!
        assertTrue(embedProp is EmbedBlock)
        assertEquals("", embedProp.embedTag)
        assertEquals(
            """
            This is an embed block
            with multiple lines
            
        """.trimIndent(), embedProp.embedContent
        )

        // Verify nested object
        val nestedObjectProp = properties["nestedObject"]!!
        assertTrue(nestedObjectProp is KsonObject)
        val nestedProps = nestedObjectProp.propertyMap

        assertEquals("value1", (nestedProps["key1"]!! as KsonString).value)

        val deepObject = nestedProps["key2"]!! as KsonObject
        val deepProps = deepObject.propertyMap
        assertEquals("deep value", (deepProps["deepKey"]!! as KsonString).value)

        // Verify nested list
        val nestedListProp = properties["nestedList"]!!
        assertTrue(nestedListProp is KsonList)
        val elements = nestedListProp.elements.map { it.ksonValue }

        assertEquals(6, elements.size)
        assertTrue(elements[0] is KsonString)
        assertEquals("string element", (elements[0] as KsonString).value)

        assertTrue(elements[1] is KsonNumber)
        assertEquals(42, ((elements[1] as KsonNumber).value as ParsedNumber.Integer).value)

        assertTrue(elements[2] is KsonBoolean)
        assertEquals(true, (elements[2] as KsonBoolean).value)

        assertTrue(elements[3] is KsonNull)

        assertTrue(elements[4] is KsonObject)
        val objectInList = elements[4] as KsonObject
        val objectInListProps = objectInList.propertyMap
        assertEquals("value", (objectInListProps["objectInList"] as KsonString).value)

        assertTrue(elements[5] is KsonList)
        val nestedListInList = elements[5] as KsonList
        val nestedListElements = nestedListInList.elements.map { it.ksonValue }
        assertEquals(3, nestedListElements.size)
        assertTrue(nestedListElements.all { it is KsonString })
        assertEquals(
            listOf("nested", "list", "elements"),
            nestedListElements.map { (it as KsonString).value }
        )
    }
} 

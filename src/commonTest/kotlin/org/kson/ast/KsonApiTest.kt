package org.kson.ast

import org.kson.Kson
import org.kson.parser.NumberParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KsonApiTest {
    @Test
    fun testAstApiComprehensive() {
        // Exercise all AstApi node types with some non-trivial nesting
        val source = """
            {
              stringVal: "hello world"
              numberVal: 42.5
              booleanVal: true
              nullVal: null
              embedBlock: %%
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

       val properties = objectNode.properties.associateBy { it.name.value }

        // Verify string value
        val stringProp = properties["stringVal"]!!
        assertTrue(stringProp.ksonValue is KsonString)
        assertEquals("hello world", (stringProp.ksonValue as KsonString).value)

        // Verify number value
        val numberProp = properties["numberVal"]!!
        assertTrue(numberProp.ksonValue is KsonNumber)
        assertEquals(42.5, ((numberProp.ksonValue as KsonNumber).value as NumberParser.ParsedNumber.Decimal).value)

        // Verify boolean value
        val booleanProp = properties["booleanVal"]!!
        assertTrue(booleanProp.ksonValue is KsonBoolean)
        assertEquals(true, (booleanProp.ksonValue as KsonBoolean).value)

        // Verify null value
        val nullProp = properties["nullVal"]!!
        assertTrue(nullProp.ksonValue is KsonNull)

        // Verify embed block
        val embedProp = properties["embedBlock"]!!
        assertTrue(embedProp.ksonValue is EmbedBlock)
        val embedBlock = embedProp.ksonValue as EmbedBlock
        assertEquals("", embedBlock.embedTag)
        assertEquals("""
            This is an embed block
            with multiple lines
            
        """.trimIndent(), embedBlock.embedContent)

        // Verify nested object
        val nestedObjectProp = properties["nestedObject"]!!
        assertTrue(nestedObjectProp.ksonValue is KsonObject)
        val nestedObject = nestedObjectProp.ksonValue as KsonObject
        val nestedProps = nestedObject.properties.associateBy { it.name.value }
        
        assertEquals("value1", (nestedProps["key1"]!!.ksonValue as KsonString).value)
        
        val deepObject = nestedProps["key2"]!!.ksonValue as KsonObject
        val deepProps = deepObject.properties.associateBy { it.name.value }
        assertEquals("deep value", (deepProps["deepKey"]!!.ksonValue as KsonString).value)

        // Verify nested list
        val nestedListProp = properties["nestedList"]!!
        assertTrue(nestedListProp.ksonValue is KsonList)
        val nestedList = nestedListProp.ksonValue as KsonList
        val elements = nestedList.elements.map { it.ksonValue }

        assertEquals(6, elements.size)
        assertTrue(elements[0] is KsonString)
        assertEquals("string element", (elements[0] as KsonString).value)
        
        assertTrue(elements[1] is KsonNumber)
        assertEquals(42, ((elements[1] as KsonNumber).value as NumberParser.ParsedNumber.Integer).value)
        
        assertTrue(elements[2] is KsonBoolean)
        assertEquals(true, (elements[2] as KsonBoolean).value)
        
        assertTrue(elements[3] is KsonNull)
        
        assertTrue(elements[4] is KsonObject)
        val objectInList = elements[4] as KsonObject
        val objectInListProps = objectInList.properties.associateBy { it.name.value }
        assertEquals("value", (objectInListProps["objectInList"]!!.ksonValue as KsonString).value)
        
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

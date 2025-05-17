package org.kson.ast

import org.kson.Kson
import org.kson.parser.NumberParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AstApiTest {
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
        
        val astApi = parseResult.ast!!.toAstApi()
        assertTrue(astApi is KsonRootApi, "Root should be KsonRootApi")
        
        val objectNode = astApi.rootNode
        assertTrue(objectNode is ObjectNodeApi, "Root node should be ObjectNodeApi")

       val properties = objectNode.properties.associateBy { it.name.value }

        // Verify string value
        val stringProp = properties["stringVal"]!!
        assertTrue(stringProp.value is StringNodeApi)
        assertEquals("hello world", (stringProp.value as StringNodeApi).value)

        // Verify number value
        val numberProp = properties["numberVal"]!!
        assertTrue(numberProp.value is NumberNodeApi)
        assertEquals(42.5, ((numberProp.value as NumberNodeApi).value as NumberParser.ParsedNumber.Decimal).value)

        // Verify boolean value
        val booleanProp = properties["booleanVal"]!!
        assertTrue(booleanProp.value is BooleanNodeApi)
        assertEquals(true, (booleanProp.value as BooleanNodeApi).value)

        // Verify null value
        val nullProp = properties["nullVal"]!!
        assertTrue(nullProp.value is NullNodeApi)

        // Verify embed block
        val embedProp = properties["embedBlock"]!!
        assertTrue(embedProp.value is EmbedBlockNodeApi)
        val embedBlock = embedProp.value as EmbedBlockNodeApi
        assertEquals("", embedBlock.embedTag)
        assertEquals("""
            This is an embed block
            with multiple lines
            
        """.trimIndent(), embedBlock.embedContent)

        // Verify nested object
        val nestedObjectProp = properties["nestedObject"]!!
        assertTrue(nestedObjectProp.value is ObjectNodeApi)
        val nestedObject = nestedObjectProp.value as ObjectNodeApi
        val nestedProps = nestedObject.properties.associateBy { it.name.value }
        
        assertEquals("value1", (nestedProps["key1"]!!.value as StringNodeApi).value)
        
        val deepObject = nestedProps["key2"]!!.value as ObjectNodeApi
        val deepProps = deepObject.properties.associateBy { it.name.value }
        assertEquals("deep value", (deepProps["deepKey"]!!.value as StringNodeApi).value)

        // Verify nested list
        val nestedListProp = properties["nestedList"]!!
        assertTrue(nestedListProp.value is ListNodeApi)
        val nestedList = nestedListProp.value as ListNodeApi
        val elements = nestedList.elements.map { it.valueNodeApi }

        assertEquals(6, elements.size)
        assertTrue(elements[0] is StringNodeApi)
        assertEquals("string element", (elements[0] as StringNodeApi).value)
        
        assertTrue(elements[1] is NumberNodeApi)
        assertEquals(42, ((elements[1] as NumberNodeApi).value as NumberParser.ParsedNumber.Integer).value)
        
        assertTrue(elements[2] is BooleanNodeApi)
        assertEquals(true, (elements[2] as BooleanNodeApi).value)
        
        assertTrue(elements[3] is NullNodeApi)
        
        assertTrue(elements[4] is ObjectNodeApi)
        val objectInList = elements[4] as ObjectNodeApi
        val objectInListProps = objectInList.properties.associateBy { it.name.value }
        assertEquals("value", (objectInListProps["objectInList"]!!.value as StringNodeApi).value)
        
        assertTrue(elements[5] is ListNodeApi)
        val nestedListInList = elements[5] as ListNodeApi
        val nestedListElements = nestedListInList.elements.map { it.valueNodeApi }
        assertEquals(3, nestedListElements.size)
        assertTrue(nestedListElements.all { it is StringNodeApi })
        assertEquals(
            listOf("nested", "list", "elements"),
            nestedListElements.map { (it as StringNodeApi).value }
        )
    }
} 

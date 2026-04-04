package org.kson

import kotlin.test.*

class DocumentSymbolTest {
    @Test
    fun testSimpleObject() {
        val doc =
            KsonTooling.parse(
                """
                {
                    "name": "John",
                    "age": 30,
                    "active": true
                }
                """.trimIndent(),
            )
        val symbols = KsonTooling.getDocumentSymbols(doc)

        assertEquals(1, symbols.size)
        val root = symbols[0]
        assertEquals("root", root.name)
        assertEquals(DocumentSymbolKind.OBJECT, root.kind)
        assertEquals(3, root.children.size)

        val name = root.children[0]
        assertEquals("name", name.name)
        assertEquals(DocumentSymbolKind.KEY, name.kind)
        assertEquals("key", name.detail)
        assertEquals(1, name.children.size)
        assertEquals(DocumentSymbolKind.STRING, name.children[0].kind)
        assertEquals("John", name.children[0].detail)

        val age = root.children[1]
        assertEquals("age", age.name)
        assertEquals(DocumentSymbolKind.KEY, age.kind)
        assertEquals(1, age.children.size)
        assertEquals(DocumentSymbolKind.NUMBER, age.children[0].kind)
        assertEquals("30", age.children[0].detail)

        val active = root.children[2]
        assertEquals("active", active.name)
        assertEquals(DocumentSymbolKind.KEY, active.kind)
        assertEquals(1, active.children.size)
        assertEquals(DocumentSymbolKind.BOOLEAN, active.children[0].kind)
        assertEquals("true", active.children[0].detail)
    }

    @Test
    fun testNestedObjects() {
        val doc =
            KsonTooling.parse(
                """
                {
                    "user": {
                        "name": "Jane",
                        "settings": {
                            "theme": "dark"
                        }
                    }
                }
                """.trimIndent(),
            )
        val symbols = KsonTooling.getDocumentSymbols(doc)

        assertEquals(1, symbols.size)
        val root = symbols[0]
        assertEquals(1, root.children.size)

        val userKey = root.children[0]
        assertEquals("user", userKey.name)
        assertEquals(DocumentSymbolKind.KEY, userKey.kind)
        assertEquals(1, userKey.children.size)

        val userObj = userKey.children[0]
        assertEquals(DocumentSymbolKind.OBJECT, userObj.kind)
        assertEquals(2, userObj.children.size)

        val settingsKey = userObj.children[1]
        assertEquals("settings", settingsKey.name)
        assertEquals(1, settingsKey.children.size)

        val settingsObj = settingsKey.children[0]
        assertEquals(DocumentSymbolKind.OBJECT, settingsObj.kind)
        assertEquals(1, settingsObj.children.size)
        assertEquals("theme", settingsObj.children[0].name)
    }

    @Test
    fun testArrays() {
        val doc =
            KsonTooling.parse(
                """
                {
                    "items": [
                        "apple",
                        42,
                        null,
                        { "nested": true }
                    ]
                }
                """.trimIndent(),
            )
        val symbols = KsonTooling.getDocumentSymbols(doc)

        val root = symbols[0]
        val itemsKey = root.children[0]
        assertEquals("items", itemsKey.name)
        assertEquals(DocumentSymbolKind.KEY, itemsKey.kind)
        assertEquals(1, itemsKey.children.size)

        val itemsArray = itemsKey.children[0]
        assertEquals(DocumentSymbolKind.ARRAY, itemsArray.kind)
        assertEquals("[4 items]", itemsArray.detail)
        assertEquals(4, itemsArray.children.size)

        assertEquals("[0]", itemsArray.children[0].name)
        assertEquals(DocumentSymbolKind.STRING, itemsArray.children[0].kind)

        assertEquals("[1]", itemsArray.children[1].name)
        assertEquals(DocumentSymbolKind.NUMBER, itemsArray.children[1].kind)

        assertEquals("[2]", itemsArray.children[2].name)
        assertEquals(DocumentSymbolKind.NULL, itemsArray.children[2].kind)

        assertEquals("[3]", itemsArray.children[3].name)
        assertEquals(DocumentSymbolKind.OBJECT, itemsArray.children[3].kind)
    }

    @Test
    fun testEmptyObjectsAndArrays() {
        val doc =
            KsonTooling.parse(
                """
                {
                    "emptyObject": {},
                    "emptyArray": []
                }
                """.trimIndent(),
            )
        val symbols = KsonTooling.getDocumentSymbols(doc)

        val root = symbols[0]
        assertEquals(2, root.children.size)

        val emptyObjKey = root.children[0]
        assertEquals("emptyObject", emptyObjKey.name)
        val emptyObj = emptyObjKey.children[0]
        assertEquals(DocumentSymbolKind.OBJECT, emptyObj.kind)
        assertEquals("{0 properties}", emptyObj.detail)
        assertEquals(0, emptyObj.children.size)

        val emptyArrKey = root.children[1]
        assertEquals("emptyArray", emptyArrKey.name)
        val emptyArr = emptyArrKey.children[0]
        assertEquals(DocumentSymbolKind.ARRAY, emptyArr.kind)
        assertEquals("[0 items]", emptyArr.detail)
        assertEquals(0, emptyArr.children.size)
    }

    @Test
    fun testRootArray() {
        val doc = KsonTooling.parse("""["item1", "item2", "item3"]""")
        val symbols = KsonTooling.getDocumentSymbols(doc)

        assertEquals(1, symbols.size)
        val root = symbols[0]
        assertEquals("root", root.name)
        assertEquals(DocumentSymbolKind.ARRAY, root.kind)
        assertEquals(3, root.children.size)
    }

    @Test
    fun testPrimitiveRoots() {
        val stringSymbols = KsonTooling.getDocumentSymbols(KsonTooling.parse("\"hello\""))
        assertEquals(1, stringSymbols.size)
        assertEquals(DocumentSymbolKind.STRING, stringSymbols[0].kind)
        assertEquals("hello", stringSymbols[0].detail)

        val numberSymbols = KsonTooling.getDocumentSymbols(KsonTooling.parse("42"))
        assertEquals(1, numberSymbols.size)
        assertEquals(DocumentSymbolKind.NUMBER, numberSymbols[0].kind)
        assertEquals("42", numberSymbols[0].detail)

        val boolSymbols = KsonTooling.getDocumentSymbols(KsonTooling.parse("false"))
        assertEquals(1, boolSymbols.size)
        assertEquals(DocumentSymbolKind.BOOLEAN, boolSymbols[0].kind)
        assertEquals("false", boolSymbols[0].detail)

        val nullSymbols = KsonTooling.getDocumentSymbols(KsonTooling.parse("null"))
        assertEquals(1, nullSymbols.size)
        assertEquals(DocumentSymbolKind.NULL, nullSymbols[0].kind)
        assertEquals("null", nullSymbols[0].detail)
    }

    @Test
    fun testEmptyDocument() {
        val symbols = KsonTooling.getDocumentSymbols(KsonTooling.parse(""))
        assertEquals(0, symbols.size)
    }

    @Test
    fun testInvalidDocument() {
        val symbols = KsonTooling.getDocumentSymbols(KsonTooling.parse("{ invalid json }"))
        assertEquals(0, symbols.size)
    }

    @Test
    fun testEmbedBlock() {
        val doc =
            KsonTooling.parse(
                """
                {
                    "code": ${"$"}bash
                        echo hello
                    ${"$$"}
                }
                """.trimIndent(),
            )
        val symbols = KsonTooling.getDocumentSymbols(doc)

        val root = symbols[0]
        val codeKey = root.children[0]
        assertEquals("code", codeKey.name)
        assertEquals(1, codeKey.children.size)

        val embed = codeKey.children[0]
        assertEquals(DocumentSymbolKind.EMBED, embed.kind)
        assertEquals("bash", embed.detail)
    }

    @Test
    fun testUnquotedKeys() {
        val symbols = KsonTooling.getDocumentSymbols(KsonTooling.parse("name: John"))

        assertEquals(1, symbols.size)
        val root = symbols[0]
        assertEquals(DocumentSymbolKind.OBJECT, root.kind)
        assertEquals(1, root.children.size)
        assertEquals("name", root.children[0].name)
        assertEquals(DocumentSymbolKind.KEY, root.children[0].kind)
    }

    @Test
    fun testRangesArePopulated() {
        val symbols = KsonTooling.getDocumentSymbols(KsonTooling.parse("{\"key\": \"value\"}"))

        val root = symbols[0]
        assertEquals(0, root.range.startLine)
        assertEquals(0, root.range.startColumn)
        assertEquals(0, root.range.endLine)
        assertEquals(16, root.range.endColumn)

        val keySymbol = root.children[0]
        assertEquals(0, keySymbol.range.startLine)
        assertEquals(2, keySymbol.range.startColumn)
    }
}

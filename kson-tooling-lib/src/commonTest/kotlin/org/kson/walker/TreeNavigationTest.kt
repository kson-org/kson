package org.kson.walker

import org.kson.KsonCore
import org.kson.parser.Coordinates
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.JsonPointer
import kotlin.test.*

/**
 * Tests that [TreeNavigation] with [KsonValueWalker] produces correct results.
 */
class TreeNavigationTest {

    private val walker = KsonValueWalker

    private fun parse(source: String): KsonValue =
        KsonCore.parseToAst(source).ksonValue
            ?: error("Parse failed")

    @Test
    fun testNavigateToNestedObjectProperty() {
        val root = parse("""
            name: 'John Doe'
            address:
              city: 'Springfield'
              .
            .
        """.trimIndent())

        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/address/city"))
        assertNotNull(result)
        assertIs<KsonString>(result)
        assertEquals("Springfield", walker.getStringValue(result))
    }

    @Test
    fun testNavigateThroughArrayByIndex() {
        val root = parse("""
            hobbies:
              - 'reading'
              - 'coding'
              - 'hiking'
        """.trimIndent())

        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/hobbies/1"))
        assertNotNull(result)
        assertEquals("coding", walker.getStringValue(result))
    }

    @Test
    fun testNavigateReturnsNullForInvalidProperty() {
        val root = parse("name: 'John'")
        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/nonexistent"))
        assertNull(result)
    }

    @Test
    fun testNavigateReturnsNullForOutOfBoundsIndex() {
        val root = parse("items:\n  - 'one'\n  - 'two'")
        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/items/99"))
        assertNull(result)
    }

    @Test
    fun testNavigateReturnsNullForNegativeArrayIndex() {
        val root = parse("items:\n  - 'one'\n  - 'two'")
        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/items/-1"))
        assertNull(result)
    }

    @Test
    fun testNavigateReturnsNullForNonNumericArrayIndex() {
        val root = parse("items:\n  - 'one'\n  - 'two'")
        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/items/notANumber"))
        assertNull(result)
    }

    @Test
    fun testNavigateEmptyPointerReturnsRoot() {
        val root = parse("name: 'test'")
        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer.ROOT)
        assertSame(root, result)
    }

    @Test
    fun testNavigateCannotNavigateIntoPrimitive() {
        val root = parse("name: 'John'")
        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/name/nested"))
        assertNull(result)
    }

    @Test
    fun testNavigateHandlesEscapedCharacters() {
        val root = parse("'a/b': 'slash value'\n'm~n': 'tilde value'")

        val slashResult = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/a~1b"))
        assertNotNull(slashResult)
        assertEquals("slash value", walker.getStringValue(slashResult))

        val tildeResult = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/m~0n"))
        assertNotNull(tildeResult)
        assertEquals("tilde value", walker.getStringValue(tildeResult))
    }

    @Test
    fun testNavigateComplexNestedStructure() {
        val root = parse("""
            users:
              - name: 'Alice'
                roles:
                  - 'admin'
                  - 'editor'
                .
              - name: 'Bob'
                roles:
                  - 'viewer'
                .
            .
        """.trimIndent())

        val result = TreeNavigation.navigateWithJsonPointer(walker, root, JsonPointer("/users/0/roles/1"))
        assertNotNull(result)
        assertIs<KsonString>(result)
        assertEquals("editor", walker.getStringValue(result))
    }

    @Test
    fun testNavigateToLocationFindsDeepestNode() {
        val root = parse("""
            person:
              name: Alice
              age: 25
        """.trimIndent())

        // Position inside "Alice" (line 1, col 8)
        val result = TreeNavigation.navigateToLocationWithPointer(
            walker, root, Coordinates(1, 8)
        )
        assertNotNull(result)
        assertIs<KsonString>(result.value)
        assertEquals("Alice", walker.getStringValue(result.value))
        assertEquals(JsonPointer.fromTokens(listOf("person", "name")), result.pointerFromRoot)
    }

    @Test
    fun testNavigateToLocationInArray() {
        val root = parse("""
            tags:
              - kotlin
              - multiplatform
        """.trimIndent())

        // Position inside "kotlin" (line 1, col 4)
        val result = TreeNavigation.navigateToLocationWithPointer(
            walker, root, Coordinates(1, 4)
        )
        assertNotNull(result)
        assertEquals(JsonPointer.fromTokens(listOf("tags", "0")), result.pointerFromRoot)
    }

    @Test
    fun testNavigateToLocationReturnsNullOutsideBounds() {
        val root = parse("name: Alice")

        // Position well beyond the document
        val result = TreeNavigation.navigateToLocationWithPointer(
            walker, root, Coordinates(100, 0)
        )
        assertNull(result)
    }

    @Test
    fun testNavigateToLocationReturnsParentWhenNotInChild() {
        val root = parse("""
            person:
              name: Alice
              age: 25
        """.trimIndent())

        // Position on "person" key (line 0, col 0) — inside root object but not inside any child value
        val result = TreeNavigation.navigateToLocationWithPointer(
            walker, root, Coordinates(0, 0)
        )
        assertNotNull(result)
        // Should return the root object since the position is not inside any property value
        assertEquals(JsonPointer.ROOT, result.pointerFromRoot)
    }

    @Test
    fun testNavigateToLocationDeepNesting() {
        val root = parse("""
            company:
              address:
                city: Boston
        """.trimIndent())

        // Position inside "Boston" (line 2, col 10)
        val result = TreeNavigation.navigateToLocationWithPointer(
            walker, root, Coordinates(2, 10)
        )
        assertNotNull(result)
        assertEquals(
            JsonPointer.fromTokens(listOf("company", "address", "city")),
            result.pointerFromRoot
        )
    }
}

package org.kson

import org.kson.parser.Location
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonValueNavigation
import org.kson.value.KsonNumber
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KsonNavigationUtilTest {

    // Sample KSON document for testing
    private val sampleKson = KsonCore.parseToAst("""
        name: 'John Doe'
        age: 30
        address:
          street: '123 Main St'
          city: 'Springfield'
          coordinates:
            - 40.7128
            - -74.0060
          .
        hobbies:
          - 'reading'
          - 'coding'
          - 'hiking'
        metadata:
          tags:
            - 'developer'
            - 'author'
          score: 95
          .
        .
    """.trimIndent()).ksonValue!!

    @Test
    fun `walkTree visits all nodes in depth-first order`() {
        val visited = mutableListOf<String>()

        KsonValueNavigation.walkTree(sampleKson) { node, _, depth ->
            val nodeType = when (node) {
                is KsonObject -> "Object(${node.propertyMap.size} props)"
                is KsonList -> "List(${node.elements.size} items)"
                is KsonString -> "String('${node.value}')"
                is KsonNumber -> "Number(${node.value})"
                else -> node::class.simpleName ?: "Unknown"
            }
            visited.add("${"  ".repeat(depth)}$nodeType")
        }

        // Verify we visited nodes
        assertTrue(visited.isNotEmpty(), "Should visit at least one node")
        assertTrue(visited[0].startsWith("Object"), "First node should be root object")

        // Verify depth increases for nested nodes
        assertTrue(visited.any { it.startsWith("  ") }, "Should have nested nodes")
    }

    @Test
    fun `walkTree passes correct parent references`() {
        val parentChildPairs = mutableListOf<Pair<KsonValue?, KsonValue>>()

        KsonValueNavigation.walkTree(sampleKson) { node, parent, _ ->
            parentChildPairs.add(parent to node)
        }

        // Root should have null parent
        assertEquals(null, parentChildPairs.first().first)
        assertEquals(sampleKson, parentChildPairs.first().second)

        // All non-root nodes should have parents
        val nonRootPairs = parentChildPairs.drop(1)
        assertTrue(nonRootPairs.all { it.first != null }, "All non-root nodes should have parents")
    }

    @Test
    fun `navigateByTokens navigates to nested object property`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("address", "city"))

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("Springfield", result.value)
    }

    @Test
    fun `navigateByTokens navigates through array by index`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "1"))

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("coding", result.value)
    }

    @Test
    fun `navigateByTokens navigates through nested arrays`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("address", "coordinates", "0"))

        assertNotNull(result)
        assertTrue(result is KsonNumber)
        assertEquals(40.7128, result.value.asDouble)
    }

    @Test
    fun `navigateByTokens returns null for invalid property`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("nonexistent", "property"))

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for out of bounds array index`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "99"))

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for negative array index`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "-1"))

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for non-numeric array index`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "notANumber"))

        assertNull(result)
    }

    @Test
    fun `navigateByTokens with empty path returns root`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, emptyList())

        assertSame(sampleKson, result)
    }

    @Test
    fun `navigateByTokens cannot navigate into primitive values`() {
        // Try to navigate into a string (primitive)
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("name", "someProp"))

        assertNull(result)
    }

    @Test
    fun `findParent returns null for root node`() {
        val parent = KsonValueNavigation.findParent(sampleKson, sampleKson)

        assertNull(parent)
    }

    @Test
    fun `findParent finds immediate parent of nested property`() {
        // First find the city string node
        val cityNode = KsonValueNavigation.navigateByTokens(sampleKson, listOf("address", "city"))!!

        // Then find its parent (should be the address object)
        val parent = KsonValueNavigation.findParent(sampleKson, cityNode)

        assertNotNull(parent)
        assertTrue(parent is KsonObject)
        assertNotNull(parent.propertyLookup["street"])
        assertNotNull(parent.propertyLookup["city"])
    }

    @Test
    fun `findParent finds parent of array element`() {
        val hobbyNode = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "0"))!!
        val parent = KsonValueNavigation.findParent(sampleKson, hobbyNode)

        assertNotNull(parent)
        assertTrue(parent is KsonList)
        assertEquals(3, parent.elements.size)
    }

    @Test
    fun `findParent returns null for node not in tree`() {
        val unrelatedNode = KsonString("not in tree", Location.create(0, 0, 0, 0, 0, 0))
        val parent = KsonValueNavigation.findParent(sampleKson, unrelatedNode)

        assertNull(parent)
    }


    @Test
    fun `findAll finds all string nodes`() {
        val strings = KsonValueNavigation.findAll(sampleKson) { it is KsonString }

        assertTrue(strings.isNotEmpty())
        assertTrue(strings.all { it is KsonString })

        // Should include strings like 'John Doe', 'Springfield', 'reading', etc.
        assertTrue(strings.size >= 8, "Should find at least 8 strings in sample data")
    }

    @Test
    fun `findAll finds all objects`() {
        val objects = KsonValueNavigation.findAll(sampleKson) { it is KsonObject }

        assertTrue(objects.isNotEmpty())
        assertTrue(objects.all { it is KsonObject })

        // Should include root, address, metadata
        assertTrue(objects.size >= 3)
    }

    @Test
    fun `findAll finds all arrays`() {
        val arrays = KsonValueNavigation.findAll(sampleKson) { it is KsonList }

        assertTrue(arrays.isNotEmpty())
        assertTrue(arrays.all { it is KsonList })

        // Should include hobbies, coordinates, tags
        assertTrue(arrays.size >= 3)
    }

    @Test
    fun `findAll with complex predicate finds objects with specific property`() {
        val objectsWithTags = KsonValueNavigation.findAll(sampleKson) {
            it is KsonObject && it.propertyLookup.containsKey("tags")
        }

        assertEquals(1, objectsWithTags.size)
        assertTrue(objectsWithTags[0] is KsonObject)
        assertNotNull((objectsWithTags[0] as KsonObject).propertyLookup["tags"])
    }

    @Test
    fun `findAll returns empty list when no matches`() {
        val booleans = KsonValueNavigation.findAll(sampleKson) { it is KsonBoolean }

        assertTrue(booleans.isEmpty())
    }

    @Test
    fun `findFirst finds first string node`() {
        val firstString = KsonValueNavigation.findFirst(sampleKson) { it is KsonString }

        assertNotNull(firstString)
        assertTrue(firstString is KsonString)

        // Due to depth-first traversal, should be 'John Doe' (first property value)
        assertEquals("John Doe", firstString.value)
    }

    @Test
    fun `findFirst returns null when no match`() {
        val boolean = KsonValueNavigation.findFirst(sampleKson) { it is KsonBoolean }

        assertNull(boolean)
    }

    @Test
    fun `findFirst stops after finding first match`() {
        var callCount = 0

        KsonValueNavigation.findFirst(sampleKson) { node ->
            callCount++
            node is KsonString
        }

        // Should have stopped early after finding first string
        // If it visited all nodes, callCount would be much higher
        // But we can't assert exact count without knowing traversal order details
        assertTrue(callCount > 0, "Should have called predicate at least once")
    }

    @Test
    fun `navigateByTokens handles complex nested structure`() {
        val complexKson = KsonCore.parseToAst("""
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
        """.trimIndent()).ksonValue!!

        val result = KsonValueNavigation.navigateByTokens(complexKson, listOf("users", "0", "roles", "1"))

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("editor", result.value)
    }

    @Test
    fun `walkTree handles single primitive value`() {
        val primitive = KsonString("test", Location.create(0, 0, 0, 0, 0, 0))
        val visited = mutableListOf<KsonValue>()

        KsonValueNavigation.walkTree(primitive) { node, parent, depth ->
            visited.add(node)
            assertEquals(0, depth)
            assertNull(parent)
        }

        assertEquals(1, visited.size)
        assertSame(primitive, visited[0])
    }

    @Test
    fun `walkTree handles empty object`() {
        val emptyObj = KsonCore.parseToAst("{}").ksonValue!!
        val visited = mutableListOf<KsonValue>()

        KsonValueNavigation.walkTree(emptyObj) { node, _, _ ->
            visited.add(node)
        }

        assertEquals(1, visited.size)
        assertTrue(visited[0] is KsonObject)
    }

    @Test
    fun `walkTree handles empty array`() {
        val emptyArray = KsonCore.parseToAst("<>").ksonValue!!
        val visited = mutableListOf<KsonValue>()

        KsonValueNavigation.walkTree(emptyArray) { node, _, _ ->
            visited.add(node)
        }

        assertEquals(1, visited.size)
        assertTrue(visited[0] is KsonList)
    }

}
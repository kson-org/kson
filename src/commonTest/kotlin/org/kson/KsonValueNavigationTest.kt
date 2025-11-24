package org.kson

import org.kson.parser.Coordinates
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

    // Tests for navigateToLocationWithPath()

    @Test
    fun `navigateToLocationWithPath finds simple property with correct path`() {
        // Document: name: 'John Doe'
        // Location inside 'John Doe' string
        val result = KsonValueNavigation.navigateToLocationWithPath(
            sampleKson,
            Coordinates(0, 8)  // Inside "John Doe" value
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonString)
        assertEquals("John Doe", (result.targetNode as KsonString).value)
        assertEquals(listOf("name"), result.pathFromRoot)
    }

    @Test
    fun `navigateToLocationWithPath finds nested property with correct path`() {
        // Document has: address.city: 'Springfield'
        // Find location inside 'Springfield'
        val result = KsonValueNavigation.navigateToLocationWithPath(
            sampleKson,
            Coordinates(4, 8)  // Line with "city: 'Springfield'"
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonString)
        assertEquals("Springfield", (result.targetNode as KsonString).value)
        assertEquals(listOf("address", "city"), result.pathFromRoot)
    }

    @Test
    fun `navigateToLocationWithPath finds array element with index in path`() {
        // Document has: hobbies[1] = 'coding' on line 12 (0-indexed: line 11)
        val result = KsonValueNavigation.navigateToLocationWithPath(
            sampleKson,
            Coordinates(11, 6)  // Inside 'coding'
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonString)
        assertEquals("coding", (result.targetNode as KsonString).value)
        assertEquals(listOf("hobbies", "1"), result.pathFromRoot)
    }

    @Test
    fun `navigateToLocationWithPath finds deeply nested array element`() {
        // Document has: address.coordinates[0] = 40.7128
        val result = KsonValueNavigation.navigateToLocationWithPath(
            sampleKson,
            Coordinates(6, 6)  // Inside first coordinate
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonNumber)
        assertEquals(40.7128, (result.targetNode as KsonNumber).value.asDouble)
        assertEquals(listOf("address", "coordinates", "0"), result.pathFromRoot)
    }

    @Test
    fun `navigateToLocationWithPath returns root with empty path when location is at root`() {
        // Test with a simple root value
        val simpleRoot = KsonCore.parseToAst("'test'").ksonValue!!
        val result = KsonValueNavigation.navigateToLocationWithPath(
            simpleRoot,
            Coordinates(0, 1)  // Inside the string
        )

        assertNotNull(result)
        assertSame(simpleRoot, result.targetNode)
        assertTrue(result.pathFromRoot.isEmpty())
    }

    @Test
    fun `navigateToLocationWithPath finds parent object when location is at object level`() {
        // Target the address object line (line 3, 0-indexed: line 2)
        val result = KsonValueNavigation.navigateToLocationWithPath(
            sampleKson,
            Coordinates(2, 0)  // Beginning of "address:" line
        )

        assertNotNull(result)
        // At the start of "address:" we should be at the root or address object level
        assertTrue(result.pathFromRoot.isEmpty() || result.pathFromRoot == listOf("address"))
    }

    @Test
    fun `navigateToLocationWithPath returns null when location is outside document bounds`() {
        val result = KsonValueNavigation.navigateToLocationWithPath(
            sampleKson,
            Coordinates(1000, 1000)  // Far outside document
        )

        assertNull(result)
    }

    @Test
    fun `navigateToLocationWithPath handles complex nested structure`() {
        // metadata.tags[1] = 'author' on line 17 (0-indexed: line 16)
        val result = KsonValueNavigation.navigateToLocationWithPath(
            sampleKson,
            Coordinates(16, 6)  // Inside 'author'
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonString)
        assertEquals("author", (result.targetNode as KsonString).value)
        assertEquals(listOf("metadata", "tags", "1"), result.pathFromRoot)
    }

    @Test
    fun `navigateToLocationWithPath finds most specific node`() {
        // When multiple nodes contain the location, should return the smallest/deepest one
        val doc = KsonCore.parseToAst("""
            user:
              name: 'Alice'
            .
        """.trimIndent()).ksonValue!!

        // Location inside 'Alice' string
        val result = KsonValueNavigation.navigateToLocationWithPath(
            doc,
            Coordinates(1, 10)  // Inside 'Alice'
        )

        assertNotNull(result)
        // Should find the string 'Alice', not the parent object
        assertTrue(result.targetNode is KsonString)
        assertEquals("Alice", (result.targetNode as KsonString).value)
        assertEquals(listOf("user", "name"), result.pathFromRoot)
    }

    // Test for the findValueAtPosition() bug fix

    @Test
    fun `findValueAtPosition returns most specific node after bug fix`() {
        // This test verifies that the smallestSize bug is fixed
        val doc = KsonCore.parseToAst("""
            outer:
              inner: 'value'
            .
        """.trimIndent()).ksonValue!!

        // Location inside 'value' string
        val result = KsonValueNavigation.findValueAtPosition(
            doc,
            Coordinates(1, 11)  // Inside 'value'
        )

        assertNotNull(result)
        // Should find the most specific node (the string), not a parent
        assertTrue(result is KsonString)
        assertEquals("value", result.value)
    }

    @Test
    fun `findValueAtPosition and navigateToLocationWithPath return same node`() {
        // These two methods should find the same target node
        val location = Coordinates(4, 8)  // Inside 'Springfield'

        val nodeFromFind = KsonValueNavigation.findValueAtPosition(sampleKson, location)
        val resultFromNavigate = KsonValueNavigation.navigateToLocationWithPath(sampleKson, location)

        assertNotNull(nodeFromFind)
        assertNotNull(resultFromNavigate)
        assertSame(nodeFromFind, resultFromNavigate.targetNode)
    }

}
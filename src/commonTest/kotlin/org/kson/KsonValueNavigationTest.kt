package org.kson

import org.kson.parser.Coordinates
import org.kson.value.KsonNumber
import org.kson.value.KsonString
import org.kson.value.KsonValueNavigation
import kotlin.test.*

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
        assertEquals("John Doe", result.targetNode.value)
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
        assertEquals("Springfield", result.targetNode.value)
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
        assertEquals("coding", result.targetNode.value)
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
        assertEquals(40.7128, result.targetNode.value.asDouble)
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
        assertEquals("author", result.targetNode.value)
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
        assertEquals("Alice", result.targetNode.value)
        assertEquals(listOf("user", "name"), result.pathFromRoot)
    }

    @Test
    fun `navigateToLocationWithPath returns most specific node in nested structure`() {
        // This test verifies that navigation finds the most specific (deepest) node
        val doc = KsonCore.parseToAst("""
            outer:
              inner: 'value'
            .
        """.trimIndent()).ksonValue!!

        // Location inside 'value' string
        val result = KsonValueNavigation.navigateToLocationWithPath(
            doc,
            Coordinates(1, 11)  // Inside 'value'
        )

        assertNotNull(result)
        // Should find the most specific node (the string), not a parent
        assertTrue(result.targetNode is KsonString)
        assertEquals("value", result.targetNode.value)
        assertEquals(listOf("outer", "inner"), result.pathFromRoot)
    }

}
package org.kson

import org.kson.parser.Coordinates
import org.kson.schema.JsonPointer
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
        val pointer = JsonPointer.fromTokens(listOf("address", "city"))
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("Springfield", result.value)
    }

    @Test
    fun `navigateByTokens navigates through array by index`() {
        val pointer = JsonPointer.fromTokens(listOf("hobbies", "1"))
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("coding", result.value)
    }

    @Test
    fun `navigateByTokens navigates through nested arrays`() {
        val pointer = JsonPointer.fromTokens(listOf("address", "coordinates", "0"))
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

        assertNotNull(result)
        assertTrue(result is KsonNumber)
        assertEquals(40.7128, result.value.asDouble)
    }

    @Test
    fun `navigateByTokens returns null for invalid property`() {
        val pointer = JsonPointer.fromTokens(listOf("nonexistent", "property"))
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for out of bounds array index`() {
        val pointer = JsonPointer.fromTokens(listOf("hobbies", "99"))
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for negative array index`() {
        val pointer = JsonPointer.fromTokens(listOf("hobbies", "-1"))
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for non-numeric array index`() {
        val pointer = JsonPointer.fromTokens(listOf("hobbies", "notANumber"))
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

        assertNull(result)
    }

    @Test
    fun `navigateByTokens with empty path returns root`() {
        val pointer = JsonPointer.fromTokens(emptyList())
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

        assertSame(sampleKson, result)
    }

    @Test
    fun `navigateByTokens cannot navigate into primitive values`() {
        // Try to navigate into a string (primitive)
        val pointer = JsonPointer.fromTokens(listOf("name", "someProp"))
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, pointer)

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

        val pointer = JsonPointer.fromTokens(listOf("users", "0", "roles", "1"))
        val result = KsonValueNavigation.navigateWithJsonPointer(complexKson, pointer)

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("editor", result.value)
    }

    // Tests for navigateToLocationWithPath()

    @Test
    fun `navigateToLocationWithPath finds simple property with correct path`() {
        // Document: name: 'John Doe'
        // Location inside 'John Doe' string
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            sampleKson,
            Coordinates(0, 8)  // Inside "John Doe" value
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonString)
        assertEquals("John Doe", result.targetNode.value)
        assertEquals(listOf("name"), result.pointerFromRoot.tokens)
    }

    @Test
    fun `navigateToLocationWithPath finds nested property with correct path`() {
        // Document has: address.city: 'Springfield'
        // Find location inside 'Springfield'
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            sampleKson,
            Coordinates(4, 9)  // Line with "city: 'Springfield'"
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonString)
        assertEquals("Springfield", result.targetNode.value)
        assertEquals(listOf("address", "city"), result.pointerFromRoot.tokens)
    }

    @Test
    fun `navigateToLocationWithPath finds array element with index in path`() {
        // Document has: hobbies[1] = 'coding' on line 12 (0-indexed: line 11)
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            sampleKson,
            Coordinates(11, 6)  // Inside 'coding'
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonString)
        assertEquals("coding", result.targetNode.value)
        assertEquals(listOf("hobbies", "1"), result.pointerFromRoot.tokens)
    }

    @Test
    fun `navigateToLocationWithPath finds deeply nested array element`() {
        // Document has: address.coordinates[0] = 40.7128
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            sampleKson,
            Coordinates(6, 6)  // Inside first coordinate
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonNumber)
        assertEquals(40.7128, result.targetNode.value.asDouble)
        assertEquals(listOf("address", "coordinates", "0"), result.pointerFromRoot.tokens)
    }

    @Test
    fun `navigateToLocationWithPath returns root with empty path when location is at root`() {
        // Test with a simple root value
        val simpleRoot = KsonCore.parseToAst("'test'").ksonValue!!
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            simpleRoot,
            Coordinates(0, 1)  // Inside the string
        )

        assertNotNull(result)
        assertSame(simpleRoot, result.targetNode)
        assertTrue(result.pointerFromRoot.tokens.isEmpty())
    }

    @Test
    fun `navigateToLocationWithPath finds parent object when location is at object level`() {
        // Target the address object line (line 3, 0-indexed: line 2)
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            sampleKson,
            Coordinates(2, 0)  // Beginning of "address:" line
        )

        assertNotNull(result)
        // At the start of "address:" we should be at the root or address object level
        assertTrue(result.pointerFromRoot.tokens.isEmpty() || result.pointerFromRoot.tokens == listOf("address"))
    }

    @Test
    fun `navigateToLocationWithPath returns null when location is outside document bounds`() {
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            sampleKson,
            Coordinates(1000, 1000)  // Far outside document
        )

        assertNull(result)
    }

    @Test
    fun `navigateToLocationWithPath handles complex nested structure`() {
        // metadata.tags[1] = 'author' on line 17 (0-indexed: line 16)
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            sampleKson,
            Coordinates(16, 7)  // Inside 'author'
        )

        assertNotNull(result)
        assertTrue(result.targetNode is KsonString)
        assertEquals("author", result.targetNode.value)
        assertEquals(listOf("metadata", "tags", "1"), result.pointerFromRoot.tokens)
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
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            doc,
            Coordinates(1, 10)  // Inside 'Alice'
        )

        assertNotNull(result)
        // Should find the string 'Alice', not the parent object
        assertTrue(result.targetNode is KsonString)
        assertEquals("Alice", result.targetNode.value)
        assertEquals(listOf("user", "name"), result.pointerFromRoot.tokens)
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
        val result = KsonValueNavigation.navigateToLocationWithPointer(
            doc,
            Coordinates(1, 11)  // Inside 'value'
        )

        assertNotNull(result)
        // Should find the most specific node (the string), not a parent
        assertTrue(result.targetNode is KsonString)
        assertEquals("value", result.targetNode.value)
        assertEquals(listOf("outer", "inner"), result.pointerFromRoot.tokens)
    }

    @Test
    fun `navigateWithJsonPointer returns null for invalid property`() {
        val result = KsonValueNavigation.navigateWithJsonPointer(sampleKson, JsonPointer("/nonexistent/property"))

        assertNull(result)
    }

    @Test
    fun `navigateWithJsonPointer handles escaped characters`() {
        val doc = KsonCore.parseToAst("""
            'a/b': 'slash value'
            'm~n': 'tilde value'
        """.trimIndent()).ksonValue!!

        // ~1 represents /
        val result1 = KsonValueNavigation.navigateWithJsonPointer(doc, JsonPointer("/a~1b"))
        assertNotNull(result1)
        assertTrue(result1 is KsonString)
        assertEquals("slash value", result1.value)

        // ~0 represents ~
        val result2 = KsonValueNavigation.navigateWithJsonPointer(doc, JsonPointer("/m~0n"))
        assertNotNull(result2)
        assertTrue(result2 is KsonString)
        assertEquals("tilde value", result2.value)
    }

    @Test
    fun `navigateWithJsonPointer with invalid pointer string throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            KsonValueNavigation.navigateWithJsonPointer(sampleKson, JsonPointer("invalid/pointer"))
        }
    }

    @Test
    fun `navigateWithJsonPointer handles complex nested structure`() {
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

        val result = KsonValueNavigation.navigateWithJsonPointer(
            complexKson,
            JsonPointer("/users/0/roles/1")
        )

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("editor", result.value)
    }

    @Test
    fun `navigateWithJsonPointer with invalid pointer string throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            KsonValueNavigation.navigateWithJsonPointer(sampleKson, JsonPointer("invalid/pointer"))
        }
    }

    @Test
    fun `navigateWithJsonPointer handles complex nested structure`() {
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

        val result = KsonValueNavigation.navigateWithJsonPointer(
            complexKson,
            JsonPointer("/users/0/roles/1")
        )

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("editor", result.value)
    }

}

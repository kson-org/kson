package org.kson.value.navigation

import org.kson.KsonCore
import org.kson.value.KsonString
import org.kson.value.navigation.jsonPointer.JsonPointer
import org.kson.value.navigation.jsonPointer.JsonPointerGlob
import org.kson.value.KsonValue
import kotlin.test.*

class KsonValueNavigationTest {

    // ========================================
    // Tests for navigateWithJsonPointer()
    // ========================================

    /**
     * Helper to test navigation with JsonPointer using <match></match> markers.
     * The documentWithMatch contains a single <match></match> pair indicating the expected matched value.
     */
    private fun assertJsonPointerNavigation(
        documentWithMatch: String,
        pointer: JsonPointer
    ) {
        val matchMarker = "<match>"
        val endMatchMarker = "</match>"

        // Remove markers to get the actual document
        val document = documentWithMatch.replace(matchMarker, "").replace(endMatchMarker, "")

        // Parse and navigate
        val ksonValue = KsonCore.parseToAst(document).ksonValue!!
        val result = KsonValueNavigation.navigateWithJsonPointer(ksonValue, pointer)

        // Build actual document with markers at the result's location
        val actualDocumentWithMarkers = if (result != null) {
            insertMatchMarkers(document, listOf(result))
        } else {
            document // No markers if null result
        }

        // Compare expected vs actual with markers
        assertEquals(
            documentWithMatch,
            actualDocumentWithMarkers,
            "Navigation result does not match expected. Expected vs Actual with <match> markers:"
        )
    }

    @Test
    fun `navigateWithJsonPointer navigates to nested object property`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                name: 'John Doe'
                age: 30
                address:
                  street: '123 Main St'
                  city: '<match>Springfield</match>'
                  coordinates:
                    - 40.7128
                    - -74.0060
                  .
                .
            """.trimIndent(),
            pointer = JsonPointer("/address/city")
        )
    }

    @Test
    fun `navigateWithJsonPointer navigates through array by index`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                hobbies:
                  - 'reading'
                  - '<match>coding</match>'
                  - 'hiking'
            """.trimIndent(),
            pointer = JsonPointer("/hobbies/1")
        )
    }

    @Test
    fun `navigateWithJsonPointer navigates through nested arrays`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                address:
                  coordinates:
                    - <match>40.7128</match>
                    - -74.0060
                  .
                .
            """.trimIndent(),
            pointer = JsonPointer("/address/coordinates/0")
        )
    }

    @Test
    fun `navigateWithJsonPointer returns null for invalid property`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                name: 'John Doe'
                age: 30
            """.trimIndent(),
            pointer = JsonPointer("/nonexistent/property")
        )
    }

    @Test
    fun `navigateWithJsonPointer returns null for out of bounds array index`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                hobbies:
                  - 'reading'
                  - 'coding'
                  - 'hiking'
            """.trimIndent(),
            pointer = JsonPointer("/hobbies/99")
        )
    }

    @Test
    fun `navigateWithJsonPointer returns null for negative array index`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                hobbies:
                  - 'reading'
                  - 'coding'
            """.trimIndent(),
            pointer = JsonPointer("/hobbies/-1")
        )
    }

    @Test
    fun `navigateWithJsonPointer returns null for non-numeric array index`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                hobbies:
                  - 'reading'
                  - 'coding'
            """.trimIndent(),
            pointer = JsonPointer("/hobbies/notANumber")
        )
    }

    @Test
    fun `navigateWithJsonPointer with empty path returns root`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                <match>name: 'John Doe'
                age: 30</match>
            """.trimIndent(),
            pointer = JsonPointer("")
        )
    }

    @Test
    fun `navigateWithJsonPointer cannot navigate into primitive values`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                name: 'John Doe'
                age: 30
            """.trimIndent(),
            pointer = JsonPointer("/name/someProp")
        )
    }

    @Test
    fun `navigateWithJsonPointer handles escaped characters`() {
        assertJsonPointerNavigation(
            documentWithMatch = """
                'a/b': '<match>slash value</match>'
                'm~n': 'tilde value'
            """.trimIndent(),
            pointer = JsonPointer("/a~1b")
        )

        assertJsonPointerNavigation(
            documentWithMatch = """
                'a/b': 'slash value'
                'm~n': '<match>tilde value</match>'
            """.trimIndent(),
            pointer = JsonPointer("/m~0n")
        )
    }

    // ========================================
    // Tests for navigateWithJsonPointerGlob() - Wildcard and Pattern Matching
    // ========================================

    /**
     * Helper to test navigation with JsonPointerGlob using multiple <match></match> markers.
     * Each matched value should be wrapped in <match></match> tags.
     */
    private fun assertJsonPointerGlobNavigation(
        documentWithMatches: String,
        pointer: JsonPointerGlob
    ) {
        // Remove markers to get the actual document
        val matchMarker = "<match>"
        val endMatchMarker = "</match>"
        val document = documentWithMatches.replace(matchMarker, "").replace(endMatchMarker, "")

        // Parse and navigate
        val ksonValue = KsonCore.parseToAst(document).ksonValue!!
        val results = KsonValueNavigation.navigateWithJsonPointerGlob(ksonValue, pointer)

        // Build actual document with markers at the results' locations
        val actualDocumentWithMarkers = insertMatchMarkers(document, results)

        // Compare expected vs actual with markers
        assertEquals(
            documentWithMatches,
            actualDocumentWithMarkers,
            "Navigation results do not match expected. Expected vs Actual with <match> markers:"
        )
    }

    /**
     * Helper function to insert <match></match> markers into the document at the locations of the results.
     * This allows for easy visual comparison of expected vs actual locations.
     */
    private fun insertMatchMarkers(document: String, results: List<KsonValue>): String {
        val matchMarker = "<match>"
        val endMatchMarker = "</match>"

        // Sort results by position (descending) so we can insert from end to start without affecting indices
        val sortedResults = results.sortedWith(
            compareByDescending<KsonValue> { it.location.start.line }
                .thenByDescending { it.location.start.column }
        )

        val lines = document.lines().toMutableList()

        // Insert markers from end to start to preserve indices
        for (result in sortedResults) {
            val startLine = result.location.start.line
            val startColumn = result.location.start.column
            val endLine = result.location.end.line
            val endColumn = result.location.end.column

            // Insert end marker
            val endLineContent = lines[endLine]
            lines[endLine] = endLineContent.take(endColumn) + endMatchMarker + endLineContent.substring(endColumn)

            // Insert start marker
            val startLineContent = lines[startLine]
            lines[startLine] = startLineContent.take(startColumn) + matchMarker + startLineContent.substring(startColumn)
        }

        return lines.joinToString("\n")
    }

    @Test
    fun `navigateWithJsonPointerGlob matches all object properties with wildcard`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                users:
                  alice:
                    email: '<match>alice@example.com</match>'
                    .
                  bob:
                    email: '<match>bob@example.com</match>'
                    .
                  charlie:
                    email: '<match>charlie@example.com</match>'
                .
            """.trimIndent(),
            pointer = JsonPointerGlob("/users/*/email")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob matches all array elements with wildcard`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                hobbies:
                  - '<match>reading</match>'
                  - '<match>coding</match>'
                  - '<match>hiking</match>'
            """.trimIndent(),
            pointer = JsonPointerGlob("/hobbies/*")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob matches pattern in object keys`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                users:
                  admin1:
                    role: '<match>superadmin</match>'
                    .
                  user1:
                    role: 'viewer'
                    .
                  admin2:
                    role: '<match>admin</match>'
                    .
                  guest1:
                    role: 'guest'
                .
            """.trimIndent(),
            pointer = JsonPointerGlob("/users/*admin*/role")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob matches pattern with question mark wildcard`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                items:
                  item1: '<match>first</match>'
                  item2: '<match>second</match>'
                  item3: '<match>third</match>'
                  item10: 'tenth'
                .
            """.trimIndent(),
            pointer = JsonPointerGlob("/items/item?")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob returns empty list for no matches`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                users:
                  alice: 'Alice'
                  bob: 'Bob'
                .
            """.trimIndent(),
            pointer = JsonPointerGlob("/users/*admin*")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob handles nested wildcards`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                departments:
                  engineering:
                    - name: '<match>Alice</match>'
                      skills: ['kotlin', 'java']
                      .
                    - name: '<match>Bob</match>'
                      skills: ['python', 'go']
                      .
                  sales:
                    - name: '<match>Charlie</match>'
                      skills: ['negotiation']
                .
            """.trimIndent(),
            pointer = JsonPointerGlob("/departments/*/*/name")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob handles combination of literal and wildcard`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                users:
                  - name: 'Alice'
                    email: '<match>alice@example.com</match>'
                  - name: 'Bob'
                    email: '<match>bob@example.com</match>'
                .
            """.trimIndent(),
            pointer = JsonPointerGlob("/users/*/email")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob returns root for empty pointer`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                <match>name: 'test'</match>
            """.trimIndent(),
            pointer = JsonPointerGlob("")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob handles exact wildcard token`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                data:
                  a: '<match>first</match>'
                  b: '<match>second</match>'
                  c: '<match>third</match>'
                .
            """.trimIndent(),
            pointer = JsonPointerGlob("/data/*")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob wildcard on primitive returns empty`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                value: 'test'
            """.trimIndent(),
            pointer = JsonPointerGlob("/value/*")
        )
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
    fun `navigateWithJsonPointerGlob pattern on array indices`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                items:
                  - '<match>item0</match>'
                  - '<match>item1</match>'
                  - '<match>item2</match>'
                  - '<match>item3</match>'
                  - '<match>item4</match>'
                  - '<match>item5</match>'
                  - '<match>item6</match>'
                  - '<match>item7</match>'
                  - '<match>item8</match>'
                  - '<match>item9</match>'
                  - 'item10'
            """.trimIndent(),
            pointer = JsonPointerGlob("/items/?")
        )
    }

    @Test
    fun `navigateWithJsonPointerGlob handles complex real-world scenario`() {
        assertJsonPointerGlobNavigation(
            documentWithMatches = """
                api:
                  v1:
                    users:
                      admin_panel:
                        endpoint: '<match>/api/v1/admin/users</match>'
                        .
                      user_list:
                        endpoint: '/api/v1/users'
                        .
                      .  
                    products:
                      admin_panel:
                        endpoint: '<match>/api/v1/admin/products</match>'
                .
            """.trimIndent(),
            pointer = JsonPointerGlob("/api/v1/*/admin_panel/endpoint")
        )
    }

}

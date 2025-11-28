package org.kson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchemaCompletionLocationTest {

    /**
     * Helper to get completions at the <caret> position in the document
     */
    private fun getCompletionsAtCaret(schema: String, documentWithCaret: String): List<CompletionItem>? {
        val caretMarker = "<caret>"
        val caretIndex = documentWithCaret.indexOf(caretMarker)
        require(caretIndex >= 0) { "Document must contain $caretMarker marker" }

        // Calculate line and column
        val beforeCaret = documentWithCaret.substring(0, caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)

        // Remove caret marker from document
        val document = documentWithCaret.replace(caretMarker, "")

        return KsonTooling.getCompletionsAtLocation(document, schema, line, column)
    }

    @Test
    fun testEnumValueCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    status: {
                        type: string
                        description: "Current status"
                        enum: ["active", "inactive", "pending"]
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                status: "<caret>active"
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for enum value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that all enum values are present
        val labels = completions.map { it.label }
        assertTrue("active" in labels, "Should include 'active'")
        assertTrue("inactive" in labels, "Should include 'inactive'")
        assertTrue("pending" in labels, "Should include 'pending'")

        // All should be VALUE kind
        assertTrue(completions.all { it.kind == CompletionKind.VALUE }, "All should be VALUE completions")
    }

    @Test
    fun testBooleanValueCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    enabled: {
                        type: boolean
                        description: "Feature toggle"
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                enabled: <caret>true
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for boolean value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that both boolean values are present
        val labels = completions.map { it.label }
        assertTrue("true" in labels, "Should include 'true'")
        assertTrue("false" in labels, "Should include 'false'")

        // Should have exactly 2 items
        assertEquals(2, completions.size, "Should have exactly 2 boolean values")
    }

    @Test
    fun testNullValueCompletion() {
        val schema = """
            {
                type: object
                properties: {
                    optional: {
                        type: "null"
                        description: "Optional field"
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                optional: <caret>null
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for null value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that null is present
        val labels = completions.map { it.label }
        assertTrue("null" in labels, "Should include 'null'")
    }

    @Test
    fun testUnionTypeBooleanAndNullCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    setting: {
                        type: ["boolean", "null"]
                        description: "Optional boolean setting"
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                setting: <caret>true
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for union type")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that boolean and null values are present
        val labels = completions.map { it.label }
        assertTrue("true" in labels, "Should include 'true'")
        assertTrue("false" in labels, "Should include 'false'")
        assertTrue("null" in labels, "Should include 'null'")

        // Should have 3 items total
        assertEquals(3, completions.size, "Should have 3 values (true, false, null)")
    }

    @Test
    fun testEnumWithDocumentation() {
        val schema = """
            {
                type: object
                properties: {
                    level: {
                        type: string
                        title: "Log Level"
                        description: "The logging verbosity level"
                        enum: ["debug", "info", "warn", "error"]
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                level: "<caret>info"
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that completions have documentation
        val withDocs = completions.filter { it.documentation != null }
        assertTrue(withDocs.isNotEmpty(), "At least one completion should have documentation")

        // The documentation should include the schema info
        val firstWithDoc = withDocs.first()
        assertTrue(
            firstWithDoc.documentation!!.contains("Log Level") ||
                    firstWithDoc.documentation.contains("logging verbosity"),
            "Documentation should include schema info"
        )
    }

    @Test
    fun testCompletionsWithDetail() {
        // language="kson"
        val schema = """
            {
                type: object
                properties: {
                    priority: {
                        type: string
                        enum: ["low", "medium", "high"]
                    }
                }
            }
        """.trimIndent()

        val completions = getCompletionsAtCaret(schema, """
            {
                priority: "<caret>low"
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // All completions should have a detail field
        assertTrue(
            completions.all { it.detail != null },
            "All completions should have detail text"
        )
    }

    @Test
    fun testNoCompletionsForStringWithoutEnum() {
        val schema = """
            {
                type: object
                properties: {
                    name: {
                        type: string
                        description: "User name"
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                name: "<caret>John"
            }
        """.trimIndent())

        // Should return empty list or null (no specific completions for plain strings)
        assertTrue(
            completions == null || completions.isEmpty(),
            "Should not have completions for plain string type"
        )
    }

    @Test
    fun testNestedPropertyEnumCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    user: {
                        type: object
                        properties: {
                            role: {
                                type: string
                                enum: ["admin", "user", "guest"]
                            }
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                user: {
                    role: "<caret>admin"
                }
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for nested enum")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("admin" in labels, "Should include 'admin'")
        assertTrue("user" in labels, "Should include 'user'")
        assertTrue("guest" in labels, "Should include 'guest'")
    }

    @Test
    fun testArrayItemEnumCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    tags: {
                        type: array
                        items: {
                            type: string
                            enum: ["red", "green", "blue"]
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                tags: ["red", "<caret>blue"]
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for array item enum")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("red" in labels, "Should include 'red'")
        assertTrue("green" in labels, "Should include 'green'")
        assertTrue("blue" in labels, "Should include 'blue'")
    }

    @Test
    fun testEnumCompletionsForPropertyWithinArrayItems() {
        // Create a schema with an array of objects containing enum properties
        // Similar to the todos array in the hardcoded schema
        val schema = """
            {
                type: object
                properties: {
                    todos: {
                        type: array
                        items: {
                            type: object
                            properties: {
                                status: {
                                    type: string
                                    description: "Current status of the task"
                                    enum: ["todo", "in_progress", "blocked", "done", "cancelled"]
                                }
                            }
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            todos:
              - status: <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions for enum property in array item")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that all enum values are present
        val labels = completions.map { it.label }
        assertTrue("todo" in labels, "Should include 'todo'")
        assertTrue("in_progress" in labels, "Should include 'in_progress'")
        assertTrue("blocked" in labels, "Should include 'blocked'")
        assertTrue("done" in labels, "Should include 'done'")
        assertTrue("cancelled" in labels, "Should include 'cancelled'")

        // All should be VALUE kind
        assertTrue(completions.all { it.kind == CompletionKind.VALUE }, "All should be VALUE completions")
    }

    @Test
    fun testEnumCompletionsWithRefDefinitions() {
        // Create a schema using $ref to reference a definition, like the hardcoded schema
        val schema = """
            {
                type: object
                properties: {
                    todos: {
                        type: array
                        items: {
                            '${'$'}ref': "#/${'$'}defs/Todo"
                        }
                    }
                }
                '${'$'}defs': {
                    Todo: {
                        type: object
                        properties: {
                            status: {
                                type: string
                                description: "Current status of the task"
                                enum: ["todo", "in_progress", "blocked", "done", "cancelled"]
                            }
                            priority: {
                                type: string
                                description: "Task priority level"
                                enum: ["low", "medium", "high", "urgent"]
                            }
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            todos:
              - status: <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions for enum property in ${'$'}ref definition")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that status enum values are present
        val labels = completions.map { it.label }
        assertTrue("todo" in labels, "Should include 'todo'")
        assertTrue("in_progress" in labels, "Should include 'in_progress'")
        assertTrue("blocked" in labels, "Should include 'blocked'")
        assertTrue("done" in labels, "Should include 'done'")
        assertTrue("cancelled" in labels, "Should include 'cancelled'")

        // All should be VALUE kind
        assertTrue(completions.all { it.kind == CompletionKind.VALUE }, "All should be VALUE completions")
    }

    @Test
    fun testNumericEnumCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    version: {
                        type: number
                        enum: [1, 2, 3]
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                version: <caret>1
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for numeric enum")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("1" in labels, "Should include '1'")
        assertTrue("2" in labels, "Should include '2'")
        assertTrue("3" in labels, "Should include '3'")
    }

    @Test
    fun testMixedTypeEnumCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    value: {
                        enum: ["auto", 100, true, null]
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                value: "<caret>auto"
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for mixed-type enum")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("auto" in labels, "Should include 'auto'")
        assertTrue("100" in labels, "Should include '100'")
        assertTrue("true" in labels, "Should include 'true'")
        assertTrue("null" in labels, "Should include 'null'")

        // Should have 4 items
        assertEquals(4, completions.size, "Should have all 4 enum values")
    }

    @Test
    fun testNoSchemaReturnsNull() {
        // Try to get completions with empty schema
        val completions = getCompletionsAtCaret("", """
            {
                name: "test<caret>"
            }
        """.trimIndent())

        // Should return null when schema is invalid
        assertEquals(completions, null, "Should return null when schema is empty/invalid")
    }

    @Test
    fun testLocationOnEmptyValue(){
       val schema = """
            {
                type: object
                properties: {
                    key: {
                      type: string
                      enum: ["active", "inactive", "pending"]
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            key: <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions for enum value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that all enum values are present
        val labels = completions.map { it.label }
        assertTrue("active" in labels, "Should include 'active'")
        assertTrue("inactive" in labels, "Should include 'inactive'")
        assertTrue("pending" in labels, "Should include 'pending'")
    }

    @Test
    fun testObjectValueProvidesPropertyCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    user: {
                        type: object
                        properties: {
                            name: { type: string }
                            age: { type: number }
                            email: { type: string }
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                user: <caret>{
                    name: "John"
                }
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for object value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Should suggest properties from the object schema
        val labels = completions.map { it.label }
        assertTrue("name" in labels, "Should include 'name' property")
        assertTrue("age" in labels, "Should include 'age' property")
        assertTrue("email" in labels, "Should include 'email' property")

        // All should be PROPERTY kind
        assertTrue(completions.all { it.kind == CompletionKind.PROPERTY }, "All should be PROPERTY completions")
    }

    @Test
    fun testObjectTypeWithoutExplicitType() {
        val schema = """
            {
                type: object
                properties: {
                    config: {
                        properties: {
                            debug: { type: boolean }
                            verbose: { type: boolean }
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                config: <caret>{
                    debug: true
                }
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions even without explicit type")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Should infer object type from presence of properties
        val labels = completions.map { it.label }
        assertTrue("debug" in labels, "Should include 'debug' property")
        assertTrue("verbose" in labels, "Should include 'verbose' property")
    }

    @Test
    fun testUnionTypeWithObject() {
        val schema = """
            {
                type: object
                properties: {
                    value: {
                        type: ["object", "null"]
                        properties: {
                            x: { type: number }
                            y: { type: number }
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                value: <caret>{
                    x: 10
                }
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for union with object")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // When object is in union type, should provide property completions
        val labels = completions.map { it.label }
        assertTrue("x" in labels, "Should include 'x' property")
        assertTrue("y" in labels, "Should include 'y' property")
    }

    @Test
    fun testNestedObjectValueCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    app: {
                        type: object
                        properties: {
                            settings: {
                                type: object
                                properties: {
                                    theme: {
                                        type: string
                                        enum: ["light", "dark"]
                                    }
                                    fontSize: { type: number }
                                }
                            }
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                app: {
                    settings: <caret>{

                    }
                }
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for nested object")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("theme" in labels, "Should include 'theme' property")
        assertTrue("fontSize" in labels, "Should include 'fontSize' property")
    }

    @Test
    fun testCompletionsAfterColonForObjectValue() {
        // Test case: typing "depends_on:" should suggest properties of the depends_on object
        val schema = """
            {
                type: object
                properties: {
                    name: { type: string }
                    depends_on: {
                        type: object
                        properties: {
                            service: { type: string }
                            timeout: { type: number }
                        }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            name: "test"
            depends_on: <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions after colon for object property")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Should get properties of depends_on object, not sibling properties
        val labels = completions.map { it.label }
        assertTrue("service" in labels, "Should include 'service' from depends_on schema")
        assertTrue("timeout" in labels, "Should include 'timeout' from depends_on schema")
        assertTrue("name" !in labels, "Should NOT include sibling property 'name'")

        // All should be PROPERTY kind
        assertTrue(completions.all { it.kind == CompletionKind.PROPERTY }, "All should be PROPERTY completions")
    }

    @Test
    fun testCompletionsAfterNewlineInObject() {
        // Test case: pressing enter in an object should suggest available properties
        val schema = """
            {
                type: object
                properties: {
                    name: { type: string }
                    age: { type: number }
                    email: { type: string }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            name: "John"
<caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions on newline in object")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Should get all root object properties
        val labels = completions.map { it.label }
        assertTrue("name" in labels, "Should include 'name' property")
        assertTrue("age" in labels, "Should include 'age' property")
        assertTrue("email" in labels, "Should include 'email' property")

        // All should be PROPERTY kind
        assertTrue(completions.all { it.kind == CompletionKind.PROPERTY }, "All should be PROPERTY completions")
    }

    @Test
    fun testCompletionsAfterColonForEnumValue() {
        // Test case: typing "status:" should suggest enum values, not properties
        val schema = """
            {
                type: object
                properties: {
                    status: {
                        type: string
                        enum: ["active", "inactive", "pending"]
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            status:<caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions after colon for enum property")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Should get enum values
        val labels = completions.map { it.label }
        assertTrue("active" in labels, "Should include 'active' enum value")
        assertTrue("inactive" in labels, "Should include 'inactive' enum value")
        assertTrue("pending" in labels, "Should include 'pending' enum value")

        // All should be VALUE kind
        assertTrue(completions.all { it.kind == CompletionKind.VALUE }, "All should be VALUE completions")
    }

    @Test
    fun testCompletionsAfterColonForListProperties() {
        // Test completions for additional properties in an array item
        // language="kson"
        val schema = """
            type: object
            properties:
              items:
                type: array
                items:
                  '${'$'}ref': '#/${'$'}defs/Item'
                  .
                .
              .
            '${'$'}defs':
              Item:
                type: object
                properties:
                  name:
                    type: string
                    .
                  status:
                    enum: [active, inactive]
                    .
                  priority:
                    type: number
        """.trimIndent()

        val completions = getCompletionsAtCaret(schema, """
            items:
              - status: active
                <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions for additional properties")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Should suggest remaining properties from the Item schema
        val labels = completions.map { it.label }
        assertTrue("name" in labels, "Should include 'name' property")
        assertTrue("priority" in labels, "Should include 'priority' property")

        // All should be PROPERTY kind
        assertTrue(completions.all { it.kind == CompletionKind.PROPERTY }, "All should be PROPERTY completions")
    }
    @Test
    fun testCompletionsWithExtraWhitespace() {
        val schema = """
            {
                type: object
                properties: {
                    status: {
                        type: string
                        description: "The current status"
                        enum: ["active", "inactive", "pending"]
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            status:   <caret>
              value: key
            another_status: key
        """.trimIndent())

        assertNotNull(completions, "Should return completions for enum property")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that enum values are included
        val labels = completions.map { it.label }
        assertTrue("active" in labels, "Should include 'active' enum value")
        assertTrue("inactive" in labels, "Should include 'inactive' enum value")
        assertTrue("pending" in labels, "Should include 'pending' enum value")
    }
}
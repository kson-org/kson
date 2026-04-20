package org.kson

import org.kson.tooling.CompletionItem
import org.kson.tooling.CompletionKind
import org.kson.tooling.KsonTooling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemaCompletionLocationTest {

    /**
     * Helper to get completions at the <caret> position in the document
     */
    private fun getCompletionsAtCaret(schema: String, documentWithCaret: String): List<CompletionItem> {
        val caretMarker = "<caret>"
        val caretIndex = documentWithCaret.indexOf(caretMarker)
        require(caretIndex >= 0) { "Document must contain $caretMarker marker" }

        // Calculate line and column
        val beforeCaret = documentWithCaret.substring(0, caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)

        // Remove caret marker from document
        val document = documentWithCaret.replace(caretMarker, "")

        return KsonTooling.getCompletionsAtLocation(KsonTooling.parse(document), KsonTooling.parse(schema), line, column)
    }

    @Test
    fun testConstValueCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    status: {
                        const: "active"
                        description: "Always active"
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                status: "<caret>"
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions for const value")
        val labels = completions.map { it.label }
        assertEquals(listOf("active"), labels, "Should offer only the const value")
    }

    @Test
    fun testIfThenNarrowsConstValueForSiblingProperty() {
        // if/then narrows a property to a const based on a sibling value
        val schema = """
            {
                "type": "object",
                "properties": {
                    "kind": { "type": "string" },
                    "breed": { "type": "string" }
                },
                "allOf": [
                    {
                        "if": {
                            "properties": { "kind": { "const": "dog" } },
                            "required": ["kind"]
                        },
                        "then": {
                            "properties": {
                                "breed": { "const": "labrador" }
                            }
                        }
                    }
                ]
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                "kind": "dog",
                "breed": "<caret>"
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }
        assertEquals(listOf("labrador"), labels, "Should narrow to only the const value, got: $labels")
    }

    @Test
    fun testIfThenNarrowsEnumValueForSiblingProperty() {
        // if/then narrows a property's enum based on a sibling value.
        // The base property has all enum values; the matching if/then branch
        // constrains it to a subset via intersection semantics.
        val schema = """
            {
                "type": "object",
                "properties": {
                    "integration": { "type": "string" },
                    "job": {
                        "type": "string",
                        "enum": ["SNOW_QUERY", "SNOW_TEST", "DBT_RUN", "DBT_TEST"]
                    }
                },
                "allOf": [
                    {
                        "if": {
                            "properties": { "integration": { "const": "SNOWFLAKE" } },
                            "required": ["integration"]
                        },
                        "then": {
                            "properties": {
                                "job": { "enum": ["SNOW_QUERY", "SNOW_TEST"] }
                            }
                        }
                    },
                    {
                        "if": {
                            "properties": { "integration": { "const": "DBT" } },
                            "required": ["integration"]
                        },
                        "then": {
                            "properties": {
                                "job": { "enum": ["DBT_RUN", "DBT_TEST"] }
                            }
                        }
                    }
                ]
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                "integration": "SNOWFLAKE",
                "job": "<caret>"
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }
        assertEquals(listOf("SNOW_QUERY", "SNOW_TEST"), labels.sorted(),
            "Should narrow to SNOWFLAKE jobs only, got: $labels")
    }

    @Test
    fun testIfThenEnumNarrowingFallsBackWhenNoSiblingValue() {
        // When no sibling value is set, all enum values should be available
        val schema = """
            {
                "type": "object",
                "properties": {
                    "integration": { "type": "string" },
                    "job": {
                        "type": "string",
                        "enum": ["SNOW_QUERY", "DBT_RUN"]
                    }
                },
                "allOf": [
                    {
                        "if": {
                            "properties": { "integration": { "const": "SNOWFLAKE" } },
                            "required": ["integration"]
                        },
                        "then": {
                            "properties": {
                                "job": { "enum": ["SNOW_QUERY"] }
                            }
                        }
                    },
                    {
                        "if": {
                            "properties": { "integration": { "const": "DBT" } },
                            "required": ["integration"]
                        },
                        "then": {
                            "properties": {
                                "job": { "enum": ["DBT_RUN"] }
                            }
                        }
                    }
                ]
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                "job": "<caret>"
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }
        assertEquals(listOf("DBT_RUN", "SNOW_QUERY"), labels.sorted(),
            "Should include all enum values when integration is not set, got: $labels")
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
    fun testNoSchemaReturnsEmptyList() {
        // Try to get completions with empty schema
        val completions = getCompletionsAtCaret("", """
            {
                name: "test<caret>"
            }
        """.trimIndent())

        // Should return null when schema is invalid
        assertEquals(completions, emptyList(), "Should return empty list when schema is empty/invalid")
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

        // Should suggest properties from the object schema, excluding already-filled ones
        val labels = completions.map { it.label }
        assertTrue("name" !in labels, "Should NOT include 'name' property (already filled)")
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

        // Should infer object type from presence of properties, excluding already-filled ones
        val labels = completions.map { it.label }
        assertTrue("debug" !in labels, "Should NOT include 'debug' property (already filled)")
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

        // When object is in union type, should provide property completions, excluding already-filled ones
        val labels = completions.map { it.label }
        assertTrue("x" !in labels, "Should NOT include 'x' property (already filled)")
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

        // Should get unfilled root object properties (name is already filled)
        val labels = completions.map { it.label }
        assertTrue("name" !in labels, "Should NOT include 'name' property (already filled)")
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

    @Test
    fun testOneOfCompletionsFilteredByExistingProperties() {
        // Test that only valid oneOf branches provide completions based on discriminator property
        val schema = """
            type: object
            properties:
              notification:
                oneOf:
                  - type: object
                    properties:
                      type:
                        const: email
                        .
                      recipient:
                        type: string
                        .
                      subject:
                        type: string
                        .
                      .
                    required: [type, recipient, subject]
                    .
                  - type: object
                    properties:
                      type:
                        const: sms
                        .
                      phoneNumber:
                        type: string
                        .
                      message:
                        type: string
                        .
                      .
                    required: [type, phoneNumber, message]
                    .
                  - type: object
                    properties:
                      type:
                        const: push
                        .
                      deviceId:
                        type: string
                        .
                      title:
                        type: string
                        .
                      body:
                        type: string
                        .
                      .
                    required: [type, deviceId, title, body]
                    .
                .
              .
        """

        // When type is "email", should only get email-specific properties
        val emailCompletions = getCompletionsAtCaret(schema, """
            notification:
              type: email
              <caret>
        """.trimIndent())

        assertNotNull(emailCompletions, "Should return completions for email notification")
        val emailLabels = emailCompletions.map { it.label }
        assertTrue("recipient" in emailLabels, "Should include 'recipient' for email type")
        assertTrue("subject" in emailLabels, "Should include 'subject' for email type")
        assertTrue("phoneNumber" !in emailLabels, "Should NOT include 'phoneNumber' (SMS-specific)")
        assertTrue("deviceId" !in emailLabels, "Should NOT include 'deviceId' (push-specific)")

        // When type is "sms", should only get SMS-specific properties
        val smsCompletions = getCompletionsAtCaret(schema, """
            notification:
              type: sms
              <caret>
        """.trimIndent())

        assertNotNull(smsCompletions, "Should return completions for SMS notification")
        val smsLabels = smsCompletions.map { it.label }
        assertTrue("phoneNumber" in smsLabels, "Should include 'phoneNumber' for SMS type")
        assertTrue("message" in smsLabels, "Should include 'message' for SMS type")
        assertTrue("recipient" !in smsLabels, "Should NOT include 'recipient' (email-specific)")
        assertTrue("deviceId" !in smsLabels, "Should NOT include 'deviceId' (push-specific)")

        // When type is "push", should only get push-specific properties
        val pushCompletions = getCompletionsAtCaret(schema, """
            notification:
              type: push
              <caret>
        """.trimIndent())

        assertNotNull(pushCompletions, "Should return completions for push notification")
        val pushLabels = pushCompletions.map { it.label }
        assertTrue("deviceId" in pushLabels, "Should include 'deviceId' for push type")
        assertTrue("title" in pushLabels, "Should include 'title' for push type")
        assertTrue("body" in pushLabels, "Should include 'body' for push type")
        assertTrue("recipient" !in pushLabels, "Should NOT include 'recipient' (email-specific)")
        assertTrue("phoneNumber" !in pushLabels, "Should NOT include 'phoneNumber' (SMS-specific)")
    }

    @Test
    fun testAnyOfCompletionsCombineValidBranches() {
        // Test that all valid anyOf branches provide completions
        val schema = """
            type: object
            properties:
              config:
                anyOf:
                  - type: object
                    properties:
                      debugMode:
                        type: boolean
                        .
                      logLevel:
                        enum: [debug, info, warn, error]
                        .
                      .
                    .
                  - type: object
                    properties:
                      production:
                        type: boolean
                        .
                      apiKey:
                        type: string
                        .
                      .
                    .
                .
              .
        """

        // Both branches are valid initially (anyOf means at least one must match)
        val completions = getCompletionsAtCaret(schema, """
            config:
              <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }

        // Should include properties from both branches since both are valid
        assertTrue("debugMode" in labels, "Should include 'debugMode' from first branch")
        assertTrue("logLevel" in labels, "Should include 'logLevel' from first branch")
        assertTrue("production" in labels, "Should include 'production' from second branch")
        assertTrue("apiKey" in labels, "Should include 'apiKey' from second branch")
    }

    @Test
    fun testAnyOfWithRefsFiltersBasedOnExistingProperties() {
        val schema = $$"""
            anyOf:
              - '$ref': '#/$defs/Development'
              - '$ref': '#/$defs/Production'
              =
            '$defs':
              Development:
                type: object
                additionalProperties: false
                properties:
                  debugMode:
                    type: boolean
                    .
                  logLevel:
                    enum: [debug, info, warn]
                    .
                  localDb:
                    type: string
                    .
                  .
                .
              Production:
                type: object
                additionalProperties: false
                properties:
                  apiKey:
                    type: string
                    .
                  replicas:
                    type: number
                    .
                  cluster:
                    type: string
                    .
                  .
                .
        """

        // When no properties are filled, should include properties from both branches
        val emptyCompletions = getCompletionsAtCaret(schema, """
            <caret>
        """.trimIndent())

        assertNotNull(emptyCompletions, "Should return completions for empty document")
        val emptyLabels = emptyCompletions.map { it.label }
        assertTrue("debugMode" in emptyLabels, "Should include 'debugMode' from Development")
        assertTrue("logLevel" in emptyLabels, "Should include 'logLevel' from Development")
        assertTrue("localDb" in emptyLabels, "Should include 'localDb' from Development")
        assertTrue("apiKey" in emptyLabels, "Should include 'apiKey' from Production")
        assertTrue("replicas" in emptyLabels, "Should include 'replicas' from Production")
        assertTrue("cluster" in emptyLabels, "Should include 'cluster' from Production")

        // When debugMode is used (Development property), should only get Development properties
        val devCompletions = getCompletionsAtCaret(schema, """
            debugMode: true
            <caret>
        """.trimIndent())

        assertNotNull(devCompletions, "Should return completions for Development branch")
        val devLabels = devCompletions.map { it.label }
        assertTrue("logLevel" in devLabels, "Should include 'logLevel' from Development")
        assertTrue("localDb" in devLabels, "Should include 'localDb' from Development")
        assertTrue("apiKey" !in devLabels, "Should NOT include 'apiKey' (from Production)")
        assertTrue("replicas" !in devLabels, "Should NOT include 'replicas' (from Production)")
        assertTrue("cluster" !in devLabels, "Should NOT include 'cluster' (from Production)")

        // When apiKey is used (Production property), should only get Production properties
        val prodCompletions = getCompletionsAtCaret(schema, """
            apiKey: "secret123"
            <caret>
        """.trimIndent())

        assertNotNull(prodCompletions, "Should return completions for Production branch")
        val prodLabels = prodCompletions.map { it.label }
        assertTrue("replicas" in prodLabels, "Should include 'replicas' from Production")
        assertTrue("cluster" in prodLabels, "Should include 'cluster' from Production")
        assertTrue("debugMode" !in prodLabels, "Should NOT include 'debugMode' (from Development)")
        assertTrue("logLevel" !in prodLabels, "Should NOT include 'logLevel' (from Development)")
        assertTrue("localDb" !in prodLabels, "Should NOT include 'localDb' (from Development)")
    }

    @Test
    fun testAllOfCompletionsCombineAllBranches() {
        // Test that all allOf branches always provide completions (no filtering)
        val schema = """
            type: object
            properties:
              entity:
                allOf:
                  - type: object
                    properties:
                      id:
                        type: string
                        .
                      createdAt:
                        type: string
                        .
                      .
                    .
                  - type: object
                    properties:
                      name:
                        type: string
                        .
                      description:
                        type: string
                        .
                      .
                    .
                .
              .
        """

        val completions = getCompletionsAtCaret(schema, """
            entity:
              <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }

        // Should include properties from all branches (allOf means all must match)
        assertTrue("id" in labels, "Should include 'id' from first branch")
        assertTrue("createdAt" in labels, "Should include 'createdAt' from first branch")
        assertTrue("name" in labels, "Should include 'name' from second branch")
        assertTrue("description" in labels, "Should include 'description' from second branch")
    }

    @Test
    fun testCompletionsExcludeAlreadyFilledProperties() {
        // Test that properties already filled in the object are excluded from completions
        val schema = """
            type: object
            properties:
              user:
                type: object
                properties:
                  name:
                    type: string
                    .
                  age:
                    type: number
                    .
                  email:
                    type: string
                    .
                  address:
                    type: string
                    .
                  .
                .
              .
        """

        // User object already has 'name' and 'email' filled in
        val completions = getCompletionsAtCaret(schema, """
            user:
              name: "John"
              email: "john@example.com"
              <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }

        // Should include only properties that are NOT already filled
        assertTrue("age" in labels, "Should include 'age' (not filled yet)")
        assertTrue("address" in labels, "Should include 'address' (not filled yet)")

        // Should NOT include properties that are already filled
        assertTrue("name" !in labels, "Should NOT include 'name' (already filled)")
        assertTrue("email" !in labels, "Should NOT include 'email' (already filled)")
    }

    @Test
    fun testCompletionsExcludeAlreadyFilledPropertiesInArrayItems() {
        // Test that properties already filled in array items are excluded from completions
        val schema = """
            type: object
            properties:
              todos:
                type: array
                items:
                  type: object
                  properties:
                    title:
                      type: string
                      .
                    status:
                      type: string
                      enum: [todo, done]
                      .
                    priority:
                      type: number
                      .
                    assignee:
                      type: string
                      .
                    .
                  .
                .
              .
        """

        // Array item already has 'title' and 'status' filled in
        val completions = getCompletionsAtCaret(schema, """
            todos:
              - title: "Fix bug"
                status: done
                <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }

        // Should include only properties that are NOT already filled
        assertTrue("priority" in labels, "Should include 'priority' (not filled yet)")
        assertTrue("assignee" in labels, "Should include 'assignee' (not filled yet)")

        // Should NOT include properties that are already filled
        assertTrue("title" !in labels, "Should NOT include 'title' (already filled)")
        assertTrue("status" !in labels, "Should NOT include 'status' (already filled)")
    }

    @Test
    fun testCompletionsExcludeAlreadyFilledPropertiesWithOneOf() {
        // Test that filtering works correctly with oneOf schemas
        val schema = """
            type: object
            properties:
              notification:
                oneOf:
                  - type: object
                    properties:
                      type:
                        const: email
                        .
                      recipient:
                        type: string
                        .
                      subject:
                        type: string
                        .
                      body:
                        type: string
                        .
                      .
                    .
                  - type: object
                    properties:
                      type:
                        const: sms
                        .
                      phoneNumber:
                        type: string
                        .
                      message:
                        type: string
                        .
                      .
                    .
                .
              .
        """

        // Email notification with 'type' and 'recipient' already filled
        val completions = getCompletionsAtCaret(schema, """
            notification:
              type: email
              recipient: "user@example.com"
              <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }

        // Should include only unfilled properties from the email branch
        assertTrue("subject" in labels, "Should include 'subject' (not filled yet)")
        assertTrue("body" in labels, "Should include 'body' (not filled yet)")

        // Should NOT include already filled properties
        assertTrue("type" !in labels, "Should NOT include 'type' (already filled)")
        assertTrue("recipient" !in labels, "Should NOT include 'recipient' (already filled)")

        // Should NOT include properties from other branches (SMS)
        assertTrue("phoneNumber" !in labels, "Should NOT include 'phoneNumber' (from SMS branch)")
        assertTrue("message" !in labels, "Should NOT include 'message' (from SMS branch)")
    }

    @Test
    fun testCompletionsAtValuePositionWithRefToAnyOf() {
        val schema = searchExpressionSchema()

        // Cursor directly at the value position for query (which $refs to SearchExpression anyOf)
        val completions = getCompletionsAtCaret(schema, $$"""
            '$schema': test
            query:
              <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions at value position with ref to anyOf")
        val labels = completions.map { it.label }
        assertTrue("field" in labels, "Should include 'field' from SearchTerm, got: $labels")
        assertTrue("term" in labels, "Should include 'term' from SearchTerm, got: $labels")
        assertTrue("and" in labels, "Should include 'and' from AndExpression, got: $labels")
        assertTrue("\$schema" !in labels, "Should NOT include root-level '\$schema'")
        assertTrue("query" !in labels, "Should NOT include root-level 'query'")
    }

    @Test
    fun testCompletionsAtValuePositionWithRefToAnyOfWithDescription() {
        // Schema where SearchExpression has description alongside anyOf (like PubMed schema)
        val schema = $$"""
            type: object
            additionalProperties: false
            properties:
              '$schema':
                type: string
                .
              query:
                '$ref': '#/$defs/SearchExpression'
                description: 'The root of the boolean search expression tree.'
                .
              .
            '$defs':
              SearchExpression:
                description: 'A node in the boolean expression tree.'
                anyOf:
                  - '$ref': '#/$defs/SearchTerm'
                  - '$ref': '#/$defs/AndExpression'
                  - '$ref': '#/$defs/OrExpression'
                  - '$ref': '#/$defs/NotExpression'
                    .
                .
              SearchTerm:
                type: object
                additionalProperties: false
                required:
                  - field
                  - term
                properties:
                  field:
                    type: string
                    .
                  term:
                    type: string
                    .
                  exactPhrase:
                    type: boolean
                    .
                  .
                .
              AndExpression:
                type: object
                additionalProperties: false
                properties:
                  and:
                    type: array
                    items:
                      '$ref': '#/$defs/SearchExpression'
                      .
                    .
                  .
                .
              OrExpression:
                type: object
                additionalProperties: false
                properties:
                  or:
                    type: array
                    items:
                      '$ref': '#/$defs/SearchExpression'
                      .
                    .
                  .
                .
              NotExpression:
                type: object
                additionalProperties: false
                properties:
                  not:
                    type: array
                    items:
                      '$ref': '#/$defs/SearchExpression'
                      .
                    .
                  .
                .
        """

        // Cursor directly at the value position for query
        val completions = getCompletionsAtCaret(schema, $$"""
            '$schema': test
            query:
              <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions at value position with ref to anyOf (with description)")
        val labels = completions.map { it.label }
        assertTrue("field" in labels, "Should include 'field' from SearchTerm, got: $labels")
        assertTrue("term" in labels, "Should include 'term' from SearchTerm, got: $labels")
        assertTrue("and" in labels, "Should include 'and' from AndExpression, got: $labels")
        assertTrue("or" in labels, "Should include 'or' from OrExpression, got: $labels")
        assertTrue("not" in labels, "Should include 'not' from NotExpression, got: $labels")
    }

    @Test
    fun testCompletionsAtValuePositionWithSchemaId() {
        // Schema with $id set — ensures $ref resolution works when the schema
        // has a non-URI $id like 'pubmed.schema.kson'
        val schema = $$"""
            '$schema': 'http://json-schema.org/draft-07/schema#'
            '$id': 'pubmed.schema.kson'
            type: object
            additionalProperties: false
            properties:
              '$schema':
                type: string
                .
              query:
                '$ref': '#/$defs/SearchExpression'
                description: 'The root of the boolean search expression tree.'
                .
              .
            '$defs':
              SearchExpression:
                description: 'A node in the boolean expression tree.'
                anyOf:
                  - '$ref': '#/$defs/SearchTerm'
                  - '$ref': '#/$defs/AndExpression'
                    .
                .
              SearchTerm:
                type: object
                additionalProperties: false
                properties:
                  field:
                    type: string
                    .
                  term:
                    type: string
                    .
                  .
                .
              AndExpression:
                type: object
                additionalProperties: false
                properties:
                  and:
                    type: array
                    items:
                      '$ref': '#/$defs/SearchExpression'
                      .
                    .
                  .
                .
        """

        val completions = getCompletionsAtCaret(schema, $$"""
            '$schema': 'pubmed.schema.kson'
            query:
              <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions with ${'$'}id set on schema")
        val labels = completions.map { it.label }
        assertTrue("field" in labels, "Should include 'field' from SearchTerm, got: $labels")
        assertTrue("term" in labels, "Should include 'term' from SearchTerm, got: $labels")
        assertTrue("and" in labels, "Should include 'and' from AndExpression, got: $labels")
    }

    @Test
    fun testCompletionsInsideDashListWithRefToAnyOf() {
        val schema = searchExpressionSchema()

        // Cursor inside an and: dash-list array nested in query
        val completions = getCompletionsAtCaret(schema, $$"""
            '$schema': test
            query:
              and:
                - <caret>
        """.trimIndent())

        assertNotNull(completions, "Should return completions inside dash-list array item")
        val labels = completions.map { it.label }
        assertTrue("field" in labels, "Should include 'field' from SearchTerm, got: $labels")
        assertTrue("term" in labels, "Should include 'term' from SearchTerm, got: $labels")
        assertTrue("and" in labels, "Should include 'and' from AndExpression, got: $labels")
        assertTrue("\$schema" !in labels, "Should NOT include root-level '\$schema'")
        assertTrue("query" !in labels, "Should NOT include root-level 'query'")
    }

    @Test
    fun testNoCompletionsInsideDelimitedListWhenSchemaExpectsObject() {
        val schema = searchExpressionSchema()

        // Cursor inside a [] delimited list, but schema expects objects (SearchTerm
        // or AndExpression). Like testNoCompletionsInsideEmptyDelimitedDashListWhenSchemaExpectsObject,
        // the structural mismatch (list where object expected) eliminates every anyOf
        // branch, so we should return no completions rather than leak object-property
        // suggestions into a list context. Also exercises the SQUARE_BRACKET_L guard in
        // the path builder: the path must target /query (not drop to root).
        val completions = getCompletionsAtCaret(schema, $$"""
            '$schema': test
            query:
              [
                <caret>
              ]
        """.trimIndent())

        assertNotNull(completions)
        assertTrue(
            completions.isEmpty(),
            "Should have no completions: the path must target /query, and list-at-object " +
                "filters out every anyOf branch. Got: ${completions.map { it.label }}"
        )
    }

    @Test
    fun testNoCompletionsInsideEmptyDelimitedDashListWhenSchemaExpectsObject() {
        val schema = searchExpressionSchema()

        // Cursor inside an empty delimited dash-list `<>`, but schema expects
        // objects (SearchTerm or AndExpression). Exercises the ANGLE_BRACKET_L
        // guard in the path builder: without it, the pointer would overshoot
        // to root and root-level properties would leak through as completions.
        val completions = getCompletionsAtCaret(schema, $$"""
            '$schema': test
            query: <<caret>>
        """.trimIndent())

        assertNotNull(completions)
        assertTrue(
            completions.isEmpty(),
            "Should have no completions: the path must target /query, and array-at-object " +
                "filters out both anyOf branches. Got: ${completions.map { it.label }}"
        )
    }

    @Test
    fun testCompletionsInsideEmptyDelimitedObject() {
        // Cursor inside an empty {} — the CURLY_BRACE_L guard should prevent
        // the path builder from dropping the last path element.
        val schema = """
            type: object
            properties:
              config:
                type: object
                properties:
                  name:
                    type: string
                    .
                  .
                .
              .
        """

        val completions = getCompletionsAtCaret(schema, """
            config: {<caret>}
        """.trimIndent())

        assertNotNull(completions, "Should return completions inside empty delimited object")
        val labels = completions.map { it.label }
        assertTrue("name" in labels, "Should include 'name' from config schema, got: $labels")
        assertTrue("config" !in labels, "Should NOT include 'config' (parent property)")
    }

    @Test
    fun testIfThenCompletionsIncludeConditionalProperties() {
        // Test that properties from if/then branches appear in completions
        val schema = """
            {
                "type": "object",
                "properties": {
                    "kind": { "type": "string" }
                },
                "if": {
                    "properties": {
                        "kind": { "const": "dog" }
                    }
                },
                "then": {
                    "properties": {
                        "bark": { "type": "boolean" }
                    }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            {
                "kind": "dog",
                <caret>
            }
        """.trimIndent())

        assertNotNull(completions, "Should return completions")
        val labels = completions.map { it.label }
        assertTrue("bark" in labels, "Should include 'bark' from then branch, got: $labels")
    }

    @Test
    fun testAllOfWithIfThenCompletionsIncludeConditionalProperties() {
        // allOf if/then should surface properties from matching branches
        val schema = """
            {
                "type": "object",
                "properties": { "kind": { "type": "string" } },
                "allOf": [
                    {
                        "if": { "properties": { "kind": { "const": "dog" } } },
                        "then": { "properties": { "bark": { "type": "boolean" } } }
                    }
                ]
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            { "kind": "dog", <caret> }
        """.trimIndent())

        assertNotNull(completions)
        assertTrue("bark" in completions.map { it.label }, "Should include 'bark' from then branch")
    }

    @Test
    fun testIfThenFiltersNestedPropertyCompletionsBySiblingValue() {
        // A nested property's completions should be narrowed by if/then evaluation
        // against a sibling at the parent level.  The base "config" allows any
        // properties; the if/then narrows to a specific $ref based on "kind".
        val schema = """
            {
                "${'$'}defs": {
                    "Item": {
                        "type": "object",
                        "properties": {
                            "kind": { "type": "string" },
                            "config": { "additionalProperties": true, "type": "object" }
                        },
                        "allOf": [
                            {
                                "if": { "properties": { "kind": { "const": "a" } }, "required": ["kind"] },
                                "then": { "properties": { "config": { "${'$'}ref": "#/${'$'}defs/ConfigA" } } }
                            },
                            {
                                "if": { "properties": { "kind": { "const": "b" } }, "required": ["kind"] },
                                "then": { "properties": { "config": { "${'$'}ref": "#/${'$'}defs/ConfigB" } } }
                            }
                        ]
                    },
                    "ConfigA": {
                        "type": "object", "additionalProperties": false,
                        "properties": { "alpha": { "type": "string" } }
                    },
                    "ConfigB": {
                        "type": "object", "additionalProperties": false,
                        "properties": { "beta": { "type": "string" } }
                    }
                },
                "type": "object",
                "properties": {
                    "items": { "type": "object", "additionalProperties": { "${'$'}ref": "#/${'$'}defs/Item" } }
                }
            }
        """

        val completions = getCompletionsAtCaret(schema, """
            { "items": { "x": { "kind": "a", "config": { <caret> } } } }
        """.trimIndent())

        assertNotNull(completions)
        val labels = completions.map { it.label }
        assertTrue("alpha" in labels, "Should include 'alpha' from ConfigA, got: $labels")
        assertFalse("beta" in labels, "Should NOT include 'beta' from ConfigB, got: $labels")
    }

    private fun searchExpressionSchema() = $$"""
        type: object
        additionalProperties: false
        properties:
          '$schema':
            type: string
            .
          query:
            '$ref': '#/$defs/SearchExpression'
            .
          .
        '$defs':
          SearchExpression:
            anyOf:
              - '$ref': '#/$defs/SearchTerm'
              - '$ref': '#/$defs/AndExpression'
              =
            .
          SearchTerm:
            type: object
            additionalProperties: false
            properties:
              field:
                type: string
                .
              term:
                type: string
                .
              .
            .
          AndExpression:
            type: object
            additionalProperties: false
            properties:
              and:
                type: array
                items:
                  '$ref': '#/$defs/SearchExpression'
                  .
                .
              .
            .
    """
}

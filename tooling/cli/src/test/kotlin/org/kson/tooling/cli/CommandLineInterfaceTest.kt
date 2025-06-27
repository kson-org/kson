package org.kson.tooling.cli

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class CommandLineInterfaceTest {
    
    private fun assertCommand(
       target: String,
        input: String,
        expectedOutput: String,
        vararg args: String
    ) {
        val inputFile = File.createTempFile("input", ".kson")
        inputFile.deleteOnExit()
        inputFile.writeText(input)
        
        val outputExtension = when (target) {
            "json" -> ".json"
            "yaml" -> ".yaml"
            else -> ".kson"
        }
        val outputFile = File.createTempFile("output", outputExtension)
        outputFile.deleteOnExit()
        
        val commandArgs = when (target) {
            "json", "yaml" -> {
                // For json and yaml, they are direct commands
                val list = mutableListOf(target)
                list.addAll(listOf("-i", inputFile.absolutePath, "-o", outputFile.absolutePath))
                list.addAll(args)
                list
            }
            else -> {
                // For kson formatting, use the format command
                val list = mutableListOf("format")
                list.addAll(args)
                list.addAll(listOf("-i", inputFile.absolutePath, "-o", outputFile.absolutePath))
                list
            }
        }
        
        main(commandArgs.toTypedArray())
        
        assertEquals(expectedOutput, outputFile.readText())
        
        inputFile.delete()
        outputFile.delete()
    }
    
    private fun assertValidateCommand(input: String, vararg args: String) {
        val inputFile = File.createTempFile("input", ".kson")
        inputFile.deleteOnExit()
        inputFile.writeText(input)
        
        val commandArgs = mutableListOf("validate")
        commandArgs.addAll(listOf("-i", inputFile.absolutePath))
        commandArgs.addAll(args)
        
        // For validate command, we just verify it doesn't throw
        main(commandArgs.toTypedArray())
        
        inputFile.delete()
    }
    

    @Test
    fun testTranspileToKsonWithDefaultOptions() {
        assertCommand(
            target = "kson",
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = """
                key: value
                nested:
                  inner: 123
            """.trimIndent()
        )
    }

    @Test
    fun testTranspileToKsonWithTabIndentation() {
        assertCommand(
            target = "kson",
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = """
                key: value
                nested:
                	inner: 123
            """.trimIndent(),
            "--indent-tabs"
        )
    }

    @Test
    fun testTranspileToKsonWithCustomIndentSpaces() {
        assertCommand(
            target = "kson",
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = """
                key: value
                nested:
                    inner: 123
            """.trimIndent(),
            "--indent-spaces", "4"
        )
    }

    @Test
    fun testTranspileToJsonWithSimpleObject() {
        assertCommand(
            target = "json",
            input = """
                key: "value"
                number: 42
            """.trimIndent(),
            expectedOutput = """
                {
                  "key": "value",
                  "number": 42
                }
            """.trimIndent()
        )
    }

    @Test
    fun testTranspileToJsonWithComplexTypes() {
        assertCommand(
            target = "json",
            input = """
                string: "value"
                number: 42
                boolean: true
                null_value: null
                array: [1, 2, 3]
                object: {
                  nested: "value"
                }
            """.trimIndent(),
            expectedOutput = """
                {
                  "string": "value",
                  "number": 42,
                  "boolean": true,
                  "null_value": null,
                  "array": [
                    1,
                    2,
                    3
                  ],
                  "object": {
                    "nested": "value"
                  }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testTranspileToYamlWithSimpleObject() {
        assertCommand(
            target = "yaml",
            input = """
                key: "value"
                number: 42
            """.trimIndent(),
            expectedOutput = """
                key: "value"
                number: 42
            """.trimIndent()
        )
    }

    @Test
    fun testTranspileToYamlWithComplexTypes() {
        assertCommand(
            target = "yaml",
            input = """
                string: "value"
                number: 42
                boolean: true
                null_value: null
                array: [1, 2, 3]
                object: {
                  nested: "value"
                }
            """.trimIndent(),
            expectedOutput = """
                string: "value"
                number: 42
                boolean: true
                null_value: null
                array:
                  - 1
                  - 2
                  - 3
                object:
                  nested: "value"
            """.trimIndent()
        )
    }

    @Test
    fun testTranspileToKsonWithCompactStyle() {
        assertCommand(
            target = "kson",
            input = """
                key: "value"
                nested: {
                  inner: 123
                  another: "test"
                }
            """.trimIndent(),
            expectedOutput = """key:value nested:inner:123 another:test""",
            "--style", "compact"
        )
    }

    @Test
    fun testTranspileToKsonWithDelimitedStyle() {
        assertCommand(
            target = "kson",
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = """
                {
                  key: value
                  nested: {
                    inner: 123
                  }
                }
            """.trimIndent(),
            "--style", "delimited"
        )
    }

    @Test
    fun testTranspileToJsonWithArray() {
        assertCommand(
            target = "json",
            input = """
                items: ["apple", "banana", "cherry"]
                count: 3
            """.trimIndent(),
            expectedOutput = """
                {
                  "items": [
                    "apple",
                    "banana",
                    "cherry"
                  ],
                  "count": 3
                }
            """.trimIndent()
        )
    }

    @Test
    fun testTranspileToYamlWithNestedStructure() {
        assertCommand(
            target = "yaml",
            input = """
                database: {
                  host: "localhost"
                  port: 5432
                  credentials: {
                    username: "admin"
                    password: "secret"
                  }
                }
            """.trimIndent(),
            expectedOutput = """
                database:
                  host: "localhost"
                  port: 5432
                  credentials:
                    username: "admin"
                    password: "secret"
            """.trimIndent()
        )
    }

    @Test
    fun testValidateCommandWithValidInput() {
        assertValidateCommand(
            input = """
                key: "value"
                number: 42
            """.trimIndent()
        )
    }

    @Test
    fun testValidateCommandWithShowTokens() {
        assertValidateCommand(
            input = """
                key: "value"
            """.trimIndent(),
            "--show-tokens"
        )
    }


    @Test
    fun testTranspileToJsonWithEmptyObject() {
        assertCommand(
            target = "json",
            input = "{}",
            expectedOutput = "{}"
        )
    }

    @Test
    fun testTranspileToYamlWithEmptyObject() {
        assertCommand(
            target = "yaml",
            input = "{}",
            expectedOutput = "{}"
        )
    }

    @Test
    fun testTranspileToKsonPreservesComments() {
        assertCommand(
            target = "kson",
            input = """
                # This is a comment
                key: "value"
                # Another comment
                number: 42
            """.trimIndent(),
            expectedOutput = """
                # This is a comment
                key: value
                # Another comment
                number: 42
            """.trimIndent()
        )
    }

    @Test
    fun testTranspileToJsonWithoutFormatOptions() {
        // With groups, format options are not available for JSON target
        assertCommand(
            target = "json",
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = """
                {
                  "key": "value",
                  "nested": {
                    "inner": 123
                  }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testTranspileToYamlWithoutFormatOptions() {
        // With groups, format options are not available for YAML target
        assertCommand(
            target = "yaml",
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = """
                key: "value"
                nested:
                  inner: 123
            """.trimIndent()
        )
    }
}
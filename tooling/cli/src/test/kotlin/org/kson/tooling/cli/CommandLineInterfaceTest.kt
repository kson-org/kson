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
                // For json and yaml, they are direct subcommands
                val list = mutableListOf(target)
                list.addAll(listOf("-i", inputFile.absolutePath, "-o", outputFile.absolutePath))
                list.addAll(args)
                list
            }
            else -> {
                // For kson formatting, use the format command
                val list = mutableListOf("format")
                list.addAll(listOf("-i", inputFile.absolutePath, "-o", outputFile.absolutePath))
                list.addAll(args)
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
    fun testTranspileToJsonWithRetainTags() {
        // Test the --retain-tags option for JSON transpilation
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
            """.trimIndent(),
            "--retain-tags"
        )
    }

    @Test
    fun testTranspileToYamlWithRetainTags() {
        // Test the --retain-tags option for YAML transpilation
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
            """.trimIndent(),
            "--retain-tags"
        )
    }

    @Test
    fun testTranspileToJsonFromStdin() {
        // Test reading from stdin (when no input file is provided)
        val outputFile = File.createTempFile("output", ".json")
        outputFile.deleteOnExit()
        
        val input = """
            key: "value"
            number: 42
        """.trimIndent()
        
        val inputStream = input.byteInputStream()
        val originalIn = System.`in`
        System.setIn(inputStream)
        
        try {
            main(arrayOf("json", "-o", outputFile.absolutePath))
            assertEquals(
                """
                {
                  "key": "value",
                  "number": 42
                }
                """.trimIndent(),
                outputFile.readText()
            )
        } finally {
            System.setIn(originalIn)
            outputFile.delete()
        }
    }

    @Test
    fun testTranspileToYamlFromStdin() {
        // Test reading from stdin for YAML conversion
        val outputFile = File.createTempFile("output", ".yaml")
        outputFile.deleteOnExit()
        
        val input = """
            key: "value"
            number: 42
        """.trimIndent()
        
        val inputStream = input.byteInputStream()
        val originalIn = System.`in`
        System.setIn(inputStream)
        
        try {
            main(arrayOf("yaml", "-o", outputFile.absolutePath))
            assertEquals(
                """
                key: "value"
                number: 42
                """.trimIndent(),
                outputFile.readText()
            )
        } finally {
            System.setIn(originalIn)
            outputFile.delete()
        }
    }

    @Test
    fun testKsonOutputFileIsOverwrittenNotAppended() {
        // Test that format command also overwrites instead of appending
        val inputFile = File.createTempFile("input", ".kson")
        inputFile.deleteOnExit()
        val outputFile = File.createTempFile("output", ".kson")
        outputFile.deleteOnExit()

        try {
            // First run
            inputFile.writeText("""key1: "value1"""")
            main(arrayOf("format", "-i", inputFile.absolutePath, "-o", outputFile.absolutePath))
            val firstOutput = outputFile.readText()
            assertEquals("""key1: value1""", firstOutput)

            // Second run with different content
            inputFile.writeText("""key2: "value2"""")
            main(arrayOf("format", "-i", inputFile.absolutePath, "-o", outputFile.absolutePath))
            val secondOutput = outputFile.readText()
            assertEquals("""key2: value2""", secondOutput)

            // Verify overwrite behavior
            assert(!secondOutput.contains("key1") && !secondOutput.contains("value1")) {
                "Format output file was appended to instead of overwritten"
            }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }
}
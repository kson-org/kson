package org.kson.tooling.cli

import com.github.ajalt.clikt.testing.test
import org.junit.Test
import org.kson.tooling.cli.commands.JsonCommand
import org.kson.tooling.cli.commands.KsonFormatCommand
import org.kson.tooling.cli.commands.ValidateCommand
import org.kson.tooling.cli.commands.YamlCommand
import java.io.File
import kotlin.test.assertEquals

enum class SubCommands{
    JSON,
    YAML,
    FORMAT,
    VALIDATE
}

sealed class OutputExpectation {
    data class Success(val message: String) : OutputExpectation()
    data class Failure(val message: String) : OutputExpectation()
}

class CommandLineInterfaceTest {
    
    private fun assertCommand(
        subCommand: SubCommands,
        input: String,
        expectedOutput: OutputExpectation,
        vararg args: String
    ) {
        val inputFile = File.createTempFile("input", ".kson")
        inputFile.deleteOnExit()
        inputFile.writeText(input)

        val outputFile = File.createTempFile("output", ".txt")
        outputFile.deleteOnExit()

        val commandArgs = listOf(
            "-i", inputFile.absolutePath,
            "-o", outputFile.absolutePath
        ) + args

        val mainCommand = when(subCommand) {
            SubCommands.JSON -> JsonCommand()
            SubCommands.YAML -> YamlCommand()
            SubCommands.FORMAT -> KsonFormatCommand()
            SubCommands.VALIDATE -> ValidateCommand()
        }
        val result = mainCommand.test(commandArgs)

        when(expectedOutput){
            is OutputExpectation.Failure -> {
                assertEquals(result.statusCode, 1)
                assertEquals(result.stderr, expectedOutput.message)
            }
            is OutputExpectation.Success -> {
                assertEquals(outputFile.readText(), expectedOutput.message)
            }
        }

        inputFile.delete()
        outputFile.delete()
    }

    @Test
    fun testTranspileToKsonWithDefaultOptions() {
        assertCommand(
            subCommand = SubCommands.FORMAT,
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                key: value
                nested:
                  inner: 123
            """.trimIndent())
        )
    }

    @Test
    fun testTranspileToKsonWithTabIndentation() {
        assertCommand(
            subCommand = SubCommands.FORMAT,
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                key: value
                nested:
                	inner: 123
            """.trimIndent()),
            "--indent-tabs"
        )
    }

    @Test
    fun testTranspileToKsonWithCustomIndentSpaces() {
        assertCommand(
            subCommand = SubCommands.FORMAT,
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                key: value
                nested:
                    inner: 123
            """.trimIndent()),
            "--indent-spaces", "4"
        )
    }

    @Test
    fun testTranspileToJsonWithSimpleObject() {
        assertCommand(
            subCommand = SubCommands.JSON,
            input = """
                key: "value"
                number: 42
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                {
                  "key": "value",
                  "number": 42
                }
            """.trimIndent())
        )
    }

    @Test
    fun testTranspileToJsonWithComplexTypes() {
        assertCommand(
            subCommand = SubCommands.JSON,
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
            expectedOutput = OutputExpectation.Success("""
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
            """.trimIndent())
        )
    }

    @Test
    fun testTranspileToYamlWithSimpleObject() {
        assertCommand(
            subCommand = SubCommands.YAML,
            input = """
                key: "value"
                number: 42
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                key: "value"
                number: 42
            """.trimIndent())
        )
    }

    @Test
    fun testTranspileToYamlWithComplexTypes() {
        assertCommand(
            subCommand = SubCommands.YAML,
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
            expectedOutput = OutputExpectation.Success("""
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
            """.trimIndent())
        )
    }

    @Test
    fun testTranspileToKsonWithCompactStyle() {
        assertCommand(
            subCommand = SubCommands.FORMAT,
            input = """
                key: "value"
                nested: {
                  inner: 123
                  another: "test"
                }
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""key:value nested:inner:123 another:test"""),
            "--style", "compact"
        )
    }

    @Test
    fun testTranspileToKsonWithClassicStyle() {
        assertCommand(
            subCommand = SubCommands.FORMAT,
            input = """
                key: value
                list: [1,2,3]
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                {
                  "key": "value",
                  "list": [
                    1,
                    2,
                    3
                  ]
                }
            """.trimIndent()),
            "--style", "classic"
        )
    }

    @Test
    fun testTranspileToKsonWithDelimitedStyle() {
        assertCommand(
            subCommand = SubCommands.FORMAT,
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                {
                  key: value
                  nested: {
                    inner: 123
                  }
                }
            """.trimIndent()),
            "--style", "delimited"
        )
    }

    @Test
    fun testTranspileToJsonWithArray() {
        assertCommand(
            subCommand = SubCommands.JSON,
            input = """
                items: ["apple", "banana", "cherry"]
                count: 3
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                {
                  "items": [
                    "apple",
                    "banana",
                    "cherry"
                  ],
                  "count": 3
                }
            """.trimIndent())
        )
    }

    @Test
    fun testTranspileToYamlWithNestedStructure() {
        assertCommand(
            subCommand = SubCommands.YAML,
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
            expectedOutput = OutputExpectation.Success("""
                database:
                  host: "localhost"
                  port: 5432
                  credentials:
                    username: "admin"
                    password: "secret"
            """.trimIndent())
        )
    }

    @Test
    fun testValidateCommandWithValidInput() {
        assertCommand(
            SubCommands.VALIDATE,
            input = """
                key: "value"
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                ✓ No errors or warnings found

                Tokens:
                  UNQUOTED_STRING: 'key' at 0:0-0:3
                  COLON: ':' at 0:3-0:4
                  STRING_OPEN_QUOTE: '"' at 0:5-0:6
                  STRING_CONTENT: 'value' at 0:6-0:11
                  STRING_CLOSE_QUOTE: '"' at 0:11-0:12
                  EOF: '' at 0:12-0:12
                
            """.trimIndent()),
            "--show-tokens"
        )
    }

    @Test
    fun testValidateCommandWithInvalidInput() {
        assertCommand(
            SubCommands.VALIDATE,
            input = """
                { {} 
            """.trimIndent(),
            expectedOutput = OutputExpectation.Failure("""
                [ERROR] Object properties must be `key: value` pairs at 0:2
                
                Tokens:
                  CURLY_BRACE_L: '{' at 0:0-0:1
                  CURLY_BRACE_L: '{' at 0:2-0:3
                  CURLY_BRACE_R: '}' at 0:3-0:4
                  EOF: '' at 0:5-0:5
                
            """.trimIndent()),
            "--show-tokens"
        )
    }


    @Test
    fun testTranspileToJsonWithEmptyObject() {
        assertCommand(
            subCommand = SubCommands.JSON,
            input = "{}",
            expectedOutput = OutputExpectation.Success("{}")
        )
    }

    @Test
    fun testTranspileToYamlWithEmptyObject() {
        assertCommand(
            subCommand = SubCommands.YAML,
            input = "{}",
            expectedOutput = OutputExpectation.Success("{}")
        )
    }

    @Test
    fun testTranspileToKsonPreservesComments() {
        assertCommand(
            subCommand = SubCommands.FORMAT,
            input = """
                # This is a comment
                key: "value"
                # Another comment
                number: 42
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                # This is a comment
                key: value
                # Another comment
                number: 42
            """.trimIndent())
        )
    }

    @Test
    fun testTranspileToJsonWithRetainTags() {
        // Test the --retain-tags option for JSON transpilation
        assertCommand(
            subCommand = SubCommands.JSON,
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                {
                  "key": "value",
                  "nested": {
                    "inner": 123
                  }
                }
            """.trimIndent()),
            "--retain-tags"
        )
    }

    @Test
    fun testTranspileToYamlWithRetainTags() {
        // Test the --retain-tags option for YAML transpilation
        assertCommand(
            subCommand = SubCommands.YAML,
            input = """
                key: "value"
                nested: {
                  inner: 123
                }
            """.trimIndent(),
            expectedOutput = OutputExpectation.Success("""
                key: "value"
                nested:
                  inner: 123
            """.trimIndent()),
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

    @Test
    fun testVersionFlag() {
        val flags = listOf("--version", "-V")

        for (flag in flags) {
            val result = KsonCli().test(argv = flag)

            assertEquals(0, result.statusCode)
            assert(result.output.contains("kson version")) {
                "Version output for '$flag' should contain 'kson version', but was: ${result.output}"
            }
            assert(result.output.contains("0.3.0-SNAPSHOT")) {
                "Version output for '$flag' should contain version number, but was: ${result.output}"
            }
        }
    }
}
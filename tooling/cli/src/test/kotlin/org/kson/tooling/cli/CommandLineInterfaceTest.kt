package org.kson.tooling.cli

import com.github.ajalt.clikt.testing.test
import org.junit.Test
import org.kson.tooling.cli.commands.JsonCommand
import org.kson.tooling.cli.commands.KsonFormatCommand
import org.kson.tooling.cli.commands.ValidateCommand
import org.kson.tooling.cli.commands.YamlCommand
import org.kson.tooling.cli.generated.CLI_NAME
import org.kson.tooling.cli.generated.KSON_VERSION
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

enum class SubCommands{
    JSON,
    YAML,
    FORMAT,
    VALIDATE
}

sealed class OutputExpectation {
    data class Success(val message: String) : OutputExpectation()
    data class Failure(val message: String) : OutputExpectation()

    /**
     * A failure identified by what its report mentions rather than by its full text, for reports whose
     * wording belongs to the messages being relayed rather than to this command line.
     */
    data class FailureMentioning(val fragments: List<String>) : OutputExpectation()
}

class CommandLineInterfaceTest {
    
    private fun assertCommand(
        subCommand: SubCommands,
        input: String,
        expectedOutput: OutputExpectation,
        vararg args: String,
        schema: String? = null
    ) {
        val inputFile = File.createTempFile("input", ".kson")
        inputFile.deleteOnExit()
        inputFile.writeText(input)

        val schemaArgs = schema?.let {
            val schemaFile = File.createTempFile("schema", ".kson")
            schemaFile.deleteOnExit()
            schemaFile.writeText(it)
            listOf("-s", schemaFile.absolutePath)
        } ?: emptyList()

        val outputFile = File.createTempFile("output", ".txt")
        outputFile.deleteOnExit()

        val commandArgs = listOf(
            "-i", inputFile.absolutePath,
            "-o", outputFile.absolutePath
        ) + schemaArgs + args

        val mainCommand = when(subCommand) {
            SubCommands.JSON -> JsonCommand()
            SubCommands.YAML -> YamlCommand()
            SubCommands.FORMAT -> KsonFormatCommand()
            SubCommands.VALIDATE -> ValidateCommand()
        }
        val result = mainCommand.test(commandArgs)

        when(expectedOutput){
            is OutputExpectation.Failure -> {
                assertEquals(1, result.statusCode)
                assertEquals(expectedOutput.message, result.stderr)
            }
            is OutputExpectation.Success -> {
                assertEquals(expectedOutput.message, outputFile.readText())
                /**
                 * On success, a command's output to `-o`/[outputFile] must be identical to what it would pipe to
                 * another command on stdout in the non-`-o` case. Which is to say: on success of a `-o` call,
                 * which all these tests use to put their output in [outputFile], we must have no extraneous output
                 * direct to stdout
                 */
                assertEquals("", result.stdout, "a successful command must not say anything extra on stdout")
            }
            is OutputExpectation.FailureMentioning -> {
                assertEquals(1, result.statusCode, "expected a failing command, stderr was: ${result.stderr}")
                expectedOutput.fragments.forEach {
                    assertTrue(
                        result.stderr.contains(it),
                        "expected the report to mention \"$it\", stderr was: ${result.stderr}"
                    )
                }
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
                key: value
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
                string: value
                number: 42
                boolean: true
                null_value: null
                array:
                  - 1
                  - 2
                  - 3
                object:
                  nested: value
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
                  host: localhost
                  port: 5432
                  credentials:
                    username: admin
                    password: secret
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
                error1: {
                    key
                }
                error2: 3.4.5 
            """.trimIndent(),
            expectedOutput = OutputExpectation.Failure("""
                [ERROR] Object properties must be `key: value` pairs at 1:4
                [ERROR] Invalid character `.` found in this number. If a string was intended, add quotes: unquoted strings must start with a letter or `_` at 3:8
                
                
                Tokens:
                  UNQUOTED_STRING: 'error1' at 0:0-0:6
                  COLON: ':' at 0:6-0:7
                  CURLY_BRACE_L: '{' at 0:8-0:9
                  UNQUOTED_STRING: 'key' at 1:4-1:7
                  CURLY_BRACE_R: '}' at 2:0-2:1
                  UNQUOTED_STRING: 'error2' at 3:0-3:6
                  COLON: ':' at 3:6-3:7
                  NUMBER: '3.4.5' at 3:8-3:13
                  EOF: '' at 3:14-3:14

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
                key: value
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
                key: value
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
    fun testValidateCommandPassesFilePath() {
        val inputFile = File.createTempFile("test-input", ".kson")
        inputFile.deleteOnExit()
        inputFile.writeText("""key: "value"""")
        val outputFile = File.createTempFile("output", ".txt")
        outputFile.deleteOnExit()

        try {
            val result = ValidateCommand().test(
                listOf("-i", inputFile.absolutePath, "-o", outputFile.absolutePath)
            )

            assertEquals(0, result.statusCode)
            assert(outputFile.readText().contains("No errors")) {
                "Validate with file input should succeed, but got: ${outputFile.readText()}"
            }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    @Test
    fun testValidateCommandWorksFromStdin() {
        val input = """key: "value"""".byteInputStream()
        val originalIn = System.`in`
        System.setIn(input)
        val outputFile = File.createTempFile("output", ".txt")
        outputFile.deleteOnExit()

        try {
            val result = ValidateCommand().test(listOf("-o", outputFile.absolutePath))

            assertEquals(0, result.statusCode)
            assert(outputFile.readText().contains("No errors")) {
                "Validate from stdin should succeed, but got: ${outputFile.readText()}"
            }
        } finally {
            System.setIn(originalIn)
            outputFile.delete()
        }
    }

    @Test
    fun testVersionFlag() {
        val flags = listOf("--version", "-V")

        for (flag in flags) {
            val result = KsonCli().test(argv = flag)

            assertEquals(0, result.statusCode)
            assert(result.output.contains("$CLI_NAME version")) {
                "Version output for '$flag' should contain '$CLI_NAME version', but was: ${result.output}"
            }
            assert(result.output.contains(KSON_VERSION)) {
                "Version output for '$flag' should contain version number, but was: ${result.output}"
            }
        }
    }

    private val requiresAgeSchema = """
        {
          type: object
          properties: { name: { type: string } }
          required: ["age"]
        }
    """.trimIndent()

    @Test
    fun testValidateWithSchemaAcceptsAConformingDocument() {
        assertCommand(
            subCommand = SubCommands.VALIDATE,
            input = """{ name: "Alice", age: 30 }""",
            expectedOutput = OutputExpectation.Success("\u2713 No errors or warnings found"),
            schema = requiresAgeSchema
        )
    }

    @Test
    fun testValidateWithSchemaFailsOnASchemaViolation() {
        assertCommand(
            subCommand = SubCommands.VALIDATE,
            input = """{ name: "Alice" }""",
            expectedOutput = OutputExpectation.FailureMentioning(listOf("age")),
            schema = requiresAgeSchema
        )
    }

    /**
     * Regression test for a bug where validate would swallow a document's warnings if it was also asked to validate
     * against a schema. Validate's whole job is to validate, so it should always report everything (including
     * warnings) that it finds.
     */
    @Test
    fun testValidateWithSchemaAlsoReportsTheDocumentsOwnWarnings() {
        assertCommand(
            subCommand = SubCommands.VALIDATE,
            input = """{ name: "Alice", name: "Bob" }""",
            expectedOutput = OutputExpectation.FailureMentioning(listOf("Duplicate key", "age")),
            schema = requiresAgeSchema
        )
    }

    @Test
    fun testJsonWithSchemaFailsOnASchemaViolation() {
        assertCommand(
            subCommand = SubCommands.JSON,
            input = """{ name: "Alice" }""",
            expectedOutput = OutputExpectation.FailureMentioning(listOf("age")),
            schema = requiresAgeSchema
        )
    }

    /**
     * Transpiling tolerates a document's own warnings, and asking to be held to a schema does not change
     * that: this document has a duplicate key but breaks no schema rule, so the transpilation succeeds.
     */
    @Test
    fun testJsonWithSchemaTranspilesADocumentCarryingItsOwnWarnings() {
        assertCommand(
            subCommand = SubCommands.JSON,
            input = """{ name: "Alice", name: "Bob" }""",
            expectedOutput = OutputExpectation.Success("""
                {
                  "name": "Alice",
                  "name": "Bob"
                }
            """.trimIndent()),
            schema = """
                {
                  type: object
                  properties: { name: { type: string } }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testSchemaThatCannotBeUsedStopsTheCommand() {
        assertCommand(
            subCommand = SubCommands.JSON,
            input = """{ name: "Alice" }""",
            expectedOutput = OutputExpectation.FailureMentioning(listOf("Failed to parse schema")),
            schema = "{ type: object"
        )
    }
}

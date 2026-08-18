package org.kson.tooling.cli

import com.github.ajalt.clikt.testing.test
import org.junit.Test
import org.kson.tooling.cli.commands.BaseKsonCommand
import org.kson.tooling.cli.commands.JsonCommand
import org.kson.tooling.cli.commands.KsonFormatCommand
import org.kson.tooling.cli.commands.ValidateCommand
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the checks the commands run over their input, and how `--schema` and `--strict` shape them.
 *
 * The check is only as wide as the caller asked for: `--schema` checks against that schema, `--strict`
 * checks where there would otherwise be no check, and with neither flag `format` and the transpile
 * commands handle a broken document themselves. `validate` always checks, and folds the schema's
 * findings into the single report it prints.
 */
class DocumentCheckTest {

    private val duplicateKeyDocument = """{ name: "Alice", name: "Bob" }"""
    private val unparseableDocument = """key: "value" extra"""
    private val cleanDocument = """{ name: "Alice" }"""
    private val permissiveSchema = """{"type": "object"}"""
    private val stringNameSchema = """{"type": "object", "properties": {"name": {"type": "string"}}}"""
    private val unparseableSchema = """{"type": ]}"""

    private val duplicateKeyWarning = """[WARNING] Duplicate key "name" in object at 0:17"""
    private val trailingContentError =
        "[ERROR] Unexpected trailing content. The previous content parsed as a complete Kson document. " +
                "at 0:13"

    /**
     * Runs [command] over [document], returning its exit code, both streams, and whatever it wrote to
     * `-o` (empty when it wrote nothing).
     */
    private fun runCommand(
        command: BaseKsonCommand,
        document: String,
        schema: String? = null,
        vararg extraArgs: String
    ): CommandRun {
        val inputFile = writeTempFile("input", ".kson", document)
        val schemaFile = schema?.let { writeTempFile("schema", ".kson", it) }
        val outputFile = File.createTempFile("output", ".kson")
        outputFile.deleteOnExit()

        try {
            val args = listOf("-i", inputFile.absolutePath, "-o", outputFile.absolutePath) +
                    (schemaFile?.let { listOf("-s", it.absolutePath) } ?: emptyList()) +
                    extraArgs

            val result = command.test(args)
            return CommandRun(result.statusCode, result.stdout, result.stderr, outputFile.readText())
        } finally {
            inputFile.delete()
            schemaFile?.delete()
            outputFile.delete()
        }
    }

    private data class CommandRun(
        val statusCode: Int,
        val stdout: String,
        val stderr: String,
        val output: String
    )

    private fun writeTempFile(prefix: String, suffix: String, content: String): File {
        val file = File.createTempFile(prefix, suffix)
        file.deleteOnExit()
        file.writeText(content)
        return file
    }

    /**
     * No check was asked for, so none runs: `format` formats the document and reports nothing.
     */
    @Test
    fun testFormatWithoutSchemaFormatsDespiteParseWarning() {
        val run = runCommand(KsonFormatCommand(), duplicateKeyDocument)

        assertEquals(0, run.statusCode, "expected exit 0, stderr: ${run.stderr}")
        assertEquals(
            """
            name: Alice
            name: Bob
            """.trimIndent(),
            run.output
        )
        assertEquals("", run.stderr, "stderr should be empty without a schema")
    }

    /**
     * The formatter is error tolerant by design, so an unrequested check must not reject input it cannot
     * parse. What it produces for broken input is covered by `FormatterTest`; here it only needs to
     * exit 0 and write output.
     */
    @Test
    fun testFormatWithoutSchemaFormatsUnparseableDocument() {
        val run = runCommand(KsonFormatCommand(), unparseableDocument)

        assertEquals(0, run.statusCode, "expected exit 0, stderr: ${run.stderr}")
        assertEquals("", run.stderr, "stderr should be empty without a schema")
        assertTrue(run.output.isNotEmpty(), "expected formatted output, got nothing")
    }

    /**
     * Converting needs a parseable document, and reports the failure itself rather than being
     * pre-empted by an unrequested check.
     */
    @Test
    fun testJsonWithoutSchemaReportsItsOwnConversionFailure() {
        val run = runCommand(JsonCommand(), unparseableDocument)

        assertEquals(1, run.statusCode, "expected exit 1")
        assertEquals("", run.output, "output should be empty when the conversion fails")
        assertEquals("Conversion failed with errors:\n  $trailingContentError\n\n", run.stderr)
    }

    @Test
    fun testFormatWithStrictFlagFailsOnParseWarning() {
        val run = runCommand(KsonFormatCommand(), duplicateKeyDocument, schema = null, "--strict")

        assertEquals(1, run.statusCode, "expected exit 1")
        assertEquals("", run.output, "output should be empty when the check fails")
        assertEquals("Validation errors:\n  $duplicateKeyWarning\n\n", run.stderr)
    }

    /**
     * Without a schema there is no check to report passing, so a clean document produces no stderr.
     */
    @Test
    fun testJsonWithStrictFlagSucceedsOnCleanDocument() {
        val run = runCommand(JsonCommand(), cleanDocument, schema = null, "--strict")

        assertEquals(0, run.statusCode, "expected exit 0, stderr: ${run.stderr}")
        assertEquals(
            """
            {
              "name": "Alice"
            }
            """.trimIndent(),
            run.output
        )
        assertEquals("", run.stderr, "stderr should be empty without a schema")
    }

    /**
     * `-s` reports every diagnostic it finds: a parse warning and a schema violation are both
     * [org.kson.MessageSeverity.WARNING], so the command cannot tell them apart. Neither stops it.
     */
    @Test
    fun testFormatWithSchemaReportsParseWarningAndContinues() {
        val run = runCommand(KsonFormatCommand(), duplicateKeyDocument, permissiveSchema)

        assertEquals(0, run.statusCode, "expected exit 0, stderr: ${run.stderr}")
        assertEquals(
            """
            name: Alice
            name: Bob
            """.trimIndent(),
            run.output
        )
        assertEquals("Validation warnings:\n  $duplicateKeyWarning\n\n", run.stderr)
    }

    @Test
    fun testFormatWithSchemaReportsViolationAndContinues() {
        val run = runCommand(KsonFormatCommand(), "{ name: 42 }", stringNameSchema)

        assertEquals(0, run.statusCode, "expected exit 0, stderr: ${run.stderr}")
        assertEquals("name: 42", run.output)
        assertEquals(
            "Validation warnings:\n" +
                    "  [WARNING] Property 'name': Expected one of: string, but got: integer at 0:8\n\n",
            run.stderr
        )
    }

    @Test
    fun testFormatWithSchemaAndStrictFlagFailsOnWarning() {
        val run = runCommand(KsonFormatCommand(), duplicateKeyDocument, permissiveSchema, "--strict")

        assertEquals(1, run.statusCode, "expected exit 1, stderr: ${run.stderr}")
        assertEquals("", run.output, "output should be empty when the check fails")
        assertEquals("Validation errors:\n  $duplicateKeyWarning\n\n", run.stderr)
    }

    /**
     * A document that does not parse cannot be checked against a schema, so the parse error stops the
     * command with or without `--strict`.
     */
    @Test
    fun testJsonWithSchemaFailsOnParseError() {
        val run = runCommand(JsonCommand(), unparseableDocument, permissiveSchema)

        assertEquals(1, run.statusCode, "expected exit 1, stderr: ${run.stderr}")
        assertEquals("", run.output, "output should be empty when the check fails")
        assertEquals("Validation errors:\n  $trailingContentError\n\n", run.stderr)
    }

    /**
     * When the document is clean, the check reports only that the schema is satisfied.
     */
    @Test
    fun testFormatWithSchemaReportsPassingIt() {
        val run = runCommand(KsonFormatCommand(), cleanDocument, stringNameSchema)

        assertEquals(0, run.statusCode, "expected exit 0, stderr: ${run.stderr}")
        assertEquals("name: Alice", run.output)
        assertEquals("✓ Document is valid according to the schema\n", run.stdout)
        assertEquals("", run.stderr)
    }

    /**
     * Checking is all `validate` does, so it is always strict: any diagnostic fails it. The schema's
     * findings already include the parse messages, so they are reported once, in `validate`'s format.
     */
    @Test
    fun testValidateWithSchemaFailsOnParseWarning() {
        val run = runCommand(ValidateCommand(), duplicateKeyDocument, permissiveSchema)

        assertEquals(1, run.statusCode, "expected exit 1, stderr: ${run.stderr}")
        assertEquals("$duplicateKeyWarning\n", run.stderr)
        assertEquals("", run.stdout, "stdout should be empty when a diagnostic was found")
    }

    @Test
    fun testValidateWithSchemaFailsOnSchemaViolation() {
        val run = runCommand(ValidateCommand(), "{ name: 42 }", stringNameSchema)

        assertEquals(1, run.statusCode, "expected exit 1, stderr: ${run.stderr}")
        assertEquals(
            "[WARNING] Property 'name': Expected one of: string, but got: integer at 0:8\n",
            run.stderr
        )
    }

    /**
     * `--show-tokens` still works with a schema: the report and the token dump both land in the single
     * report `validate` prints to stderr.
     */
    @Test
    fun testValidateWithSchemaShowsTokensBesideTheWarning() {
        val run = runCommand(ValidateCommand(), duplicateKeyDocument, permissiveSchema, "--show-tokens")

        assertEquals(1, run.statusCode, "expected exit 1, stderr: ${run.stderr}")
        val reportAndTokens = run.stderr.split("\n\n\nTokens:\n")
        assertEquals(2, reportAndTokens.size, "expected a report and a token dump, stderr: ${run.stderr}")
        assertEquals(duplicateKeyWarning, reportAndTokens[0], "the report should hold the warning exactly once")
        assertTrue(
            reportAndTokens[1].contains("UNQUOTED_STRING: 'name' at 0:17-0:21"),
            "expected the duplicate key's tokens, got: ${reportAndTokens[1]}"
        )
    }

    /**
     * A schema that does not itself parse stops the command rather than being skipped. The message text
     * is the schema parser's; the heading and formatting are the command's.
     */
    @Test
    fun testValidateWithUnparseableSchemaFails() {
        val run = runCommand(ValidateCommand(), cleanDocument, unparseableSchema)

        assertEquals(1, run.statusCode, "expected exit 1 for an unparseable schema")
        assertTrue(run.stderr.startsWith("Failed to parse schema:\n  [ERROR] "), run.stderr)
    }

    /**
     * Two verdicts from two places: the schema is satisfied (stdout, from the check) and the document
     * is clean (the output file, from `validate`).
     */
    @Test
    fun testValidateWithSchemaReportsPassingItAndTheDocument() {
        val run = runCommand(ValidateCommand(), cleanDocument, stringNameSchema)

        assertEquals(0, run.statusCode, "expected exit 0, stderr: ${run.stderr}")
        assertEquals("✓ Document is valid according to the schema\n", run.stdout)
        assertEquals("✓ No errors or warnings found", run.output)
        assertEquals("", run.stderr)
    }
}

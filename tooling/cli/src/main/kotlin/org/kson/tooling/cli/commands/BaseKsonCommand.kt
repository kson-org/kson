package org.kson.tooling.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.outputStream
import org.kson.Kson
import org.kson.Message
import org.kson.SchemaResult
import java.io.File

abstract class BaseKsonCommand(
    name: String? = null
) : CliktCommand(name = name) {
    private val inputFile by option("-i", "--input", help = "Input file (default: stdin)")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val output by option("-o", "--output", help = "Output file (default: stdout)")
        .outputStream(truncateExisting = true)
        .default(System.out)

    private val schema by option("-s", "--schema", help = "Path to KSON schema file for validation")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    protected val errorFormat = { error: Message ->
        "[${error.severity}] ${error.message} at ${error.start.line}:${error.start.column}\n"
    }

    protected fun getFilePath(): String? = inputFile?.path

    /**
     * Checks if we should show help instead of processing.
     * Shows help when:
     * - Input is stdin AND
     * - No data is immediately available AND
     * - Running in an interactive terminal (not piped)
     *
     * Subclasses should call super.run() at the beginning of their run() method.
     */
    override fun run() {
        if (inputFile == null && System.`in`.available() == 0 && System.console() != null) {
            echo(getFormattedHelp())
            throw ProgramResult(0)
        }
    }

    /**
     * Reads input from the configured source (file or stdin).
     * @return The content read from the input source
     */
    protected fun readInput(): String {
        val source = inputFile?.inputStream() ?: System.`in`
        return source.bufferedReader().use { it.readText() }
    }

    protected fun writeOutput(content: String) {
        output.bufferedWriter().use { it.write(content) }
    }

    protected fun validateWithSchema(ksonContent: String) {
        val schemaFile = schema ?: return

        val schemaContent = schemaFile.readText()

        when (val schemaResult = Kson.parseSchema(schemaContent)) {
            is SchemaResult.Success -> {
                val validationErrors = schemaResult.schemaValidator.validate(ksonContent, getFilePath())

                if (validationErrors.isEmpty()) {
                    echo("✓ Document is valid according to the schema")
                } else {
                    echo("Validation errors:", err = true)
                    validationErrors.forEach { error ->
                        echo("  ${errorFormat(error)}", err = true)
                    }
                    throw ProgramResult(1)
                }
            }

            is SchemaResult.Failure -> {
                echo("Failed to parse schema:", err = true)
                schemaResult.errors.forEach { error ->
                    echo("  ${errorFormat(error)}", err = true)
                }
                throw ProgramResult(1)
            }
        }
    }
}
package org.kson.tooling.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.inputStream
import com.github.ajalt.clikt.parameters.types.outputStream
import org.kson.Kson
import org.kson.Message
import org.kson.SchemaResult
import kotlin.system.exitProcess

abstract class BaseKsonCommand(
    name: String? = null
) : CliktCommand(name = name) {
    private val input by option("-i", "--input", help = "Input file (default: stdin)")
        .inputStream()
        .default(System.`in`)

    private val output by option("-o", "--output", help = "Output file (default: stdout)")
        .outputStream()
        .default(System.out)

    private val schema by option("-s", "--schema", help = "Path to KSON schema file for validation")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    protected val errorFormat = { error: Message ->
        "[${error.severity}] ${error.message} at ${error.start.line}:${error.start.column}"
    }

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
        if (input == System.`in` && System.`in`.available() == 0 && System.console() != null) {
            echo(getFormattedHelp())
            exitProcess(0)
        }
    }

    /**
     * Reads input from the configured source (file or stdin).
     * @return The content read from the input source
     */
    protected fun readInput(): String {
        return input.bufferedReader().use { it.readText() }
    }

    protected fun writeOutput(content: String) {
        output.bufferedWriter().use { it.write(content) }
    }

    protected fun validateWithSchema(ksonContent: String): Boolean {
        val schemaFile = schema ?: return false

        val schemaContent = schemaFile.readText()

        when (val schemaResult = Kson.parseSchema(schemaContent)) {
            is SchemaResult.Success -> {
                val validationErrors = schemaResult.schemaValidator.validate(ksonContent)

                if (validationErrors.isEmpty()) {
                    println("✓ Document is valid according to the schema")
                } else {
                    System.err.println("Validation errors:")
                    validationErrors.forEach { error ->
                        System.err.println("  ${errorFormat(error)}")
                    }
                    exitProcess(1)
                }
            }

            is SchemaResult.Failure -> {
                System.err.println("Failed to parse schema:")
                schemaResult.errors.forEach { error ->
                    System.err.println("  ${errorFormat(error)}")
                }
                exitProcess(1)
            }
        }
        return true
    }
}
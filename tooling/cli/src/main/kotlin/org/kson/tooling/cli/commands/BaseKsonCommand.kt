package org.kson.tooling.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.outputStream
import org.kson.Kson
import org.kson.Message
import org.kson.MessageSeverity
import org.kson.SchemaResult
import org.kson.SchemaValidator

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
     * The `--strict` flag, for commands that do more than check the document. [ValidateCommand] does
     * not offer it: checking is its only work, so it is always strict.
     */
    protected fun strictOption() = option(
        "--strict",
        help = "Fail (exit 1) on any diagnostic, including warnings, instead of reporting them and " +
                "continuing"
    ).flag()

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

    /**
     * Checks [ksonContent] before the command does its own work, reporting findings on stderr:
     *
     * - with `--schema`: reports the parse messages and schema violations, stopping the command on a
     *   [MessageSeverity.ERROR], or on any diagnostic when [strict]. Warnings alone do not stop it —
     *   schema violations are all [MessageSeverity.WARNING], so they cannot be told apart from parse
     *   warnings here.
     * - without `--schema`: checks nothing unless [strict], which stops the command on any parse
     *   diagnostic. Otherwise each command handles unparseable input itself: formatting is error
     *   tolerant by design, and transpiling reports the errors that stopped it.
     *
     * [ValidateCommand] instead takes the messages from [validateAgainstSchema] and prints them in the
     * single report it produces.
     */
    protected fun checkDocument(ksonContent: String, strict: Boolean) {
        val schemaValidator = parseSchemaOrExit()

        if (schemaValidator == null) {
            if (strict) {
                val parseMessages = Kson.analyze(ksonContent, getFilePath()).errors
                if (parseMessages.isNotEmpty()) {
                    reportMessages(parseMessages, fatal = true)
                }
            }
            return
        }

        val messages = schemaValidator.validate(ksonContent, getFilePath())
        if (messages.isEmpty()) {
            echo("✓ Document is valid according to the schema")
            return
        }

        reportMessages(messages, fatal = strict || messages.any { it.severity == MessageSeverity.ERROR })
    }

    /**
     * [ksonContent]'s parse messages followed by any `--schema` violations, or null when no schema was
     * given. Returns them rather than reporting them, for [ValidateCommand]'s single report; every
     * other command wants [checkDocument].
     */
    protected fun validateAgainstSchema(ksonContent: String): List<Message>? {
        val schemaValidator = parseSchemaOrExit() ?: return null

        return schemaValidator.validate(ksonContent, getFilePath())
    }

    /**
     * The `--schema` file parsed into a validator, or null when no schema was given. A schema that
     * does not itself parse stops the command.
     */
    private fun parseSchemaOrExit(): SchemaValidator? {
        val schemaContent = (schema ?: return null).readText()

        return when (val schemaResult = Kson.parseSchema(schemaContent)) {
            is SchemaResult.Success -> schemaResult.schemaValidator
            is SchemaResult.Failure -> {
                echo("Failed to parse schema:", err = true)
                schemaResult.errors.forEach { error ->
                    echo("  ${errorFormat(error)}", err = true)
                }
                throw ProgramResult(1)
            }
        }
    }

    /**
     * Prints [messages] on stderr under an "errors" or "warnings" heading, and stops the command when
     * [fatal].
     */
    private fun reportMessages(messages: List<Message>, fatal: Boolean) {
        echo(if (fatal) "Validation errors:" else "Validation warnings:", err = true)
        messages.forEach { message ->
            echo("  ${errorFormat(message)}", err = true)
        }

        if (fatal) {
            throw ProgramResult(1)
        }
    }
}
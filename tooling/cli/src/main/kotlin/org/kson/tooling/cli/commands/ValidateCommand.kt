package org.kson.tooling.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.kson.Kson
import org.kson.tooling.cli.generated.CLI_DISPLAY_NAME
import org.kson.tooling.cli.generated.FILE_EXTENSION
import org.kson.tooling.cli.generated.CLI_NAME

class ValidateCommand : BaseKsonCommand() {
    override fun help(context: Context) = """
        |Validate $CLI_DISPLAY_NAME documents for syntax errors, warnings and tokens.
        |
        |Performs static analysis to detect issues without executing or converting the document.
        |Reports errors and warnings with line and column information.
        |
        |Examples:
        |${"\u0085"}Validate a $CLI_DISPLAY_NAME file:
        |${"\u0085"}  $CLI_NAME validate -i document.$FILE_EXTENSION
        |${"\u0085"}
        |${"\u0085"}Validate from stdin:
        |${"\u0085"}  cat file.$FILE_EXTENSION | $CLI_NAME validate
        |${"\u0085"}
        |${"\u0085"}Show lexical tokens for debugging:
        |${"\u0085"}  $CLI_NAME validate -i file.$FILE_EXTENSION --show-tokens
        |${"\u0085"}
        |${"\u0085"}Validate against a schema:
        |${"\u0085"}  $CLI_NAME validate -i file.$FILE_EXTENSION -s schema.$FILE_EXTENSION
        |
        |Exit codes:
        |${"\u0085"}  0 - No errors found
        |${"\u0085"}  1 - Errors detected (warnings don't affect exit code)
    """.trimMargin()

    private val showTokens by option("--show-tokens", help = "Display lexical tokens (for debugging)")
        .flag()

    override fun run() {
        super.run()  // Check for help display

        val ksonContent = try {
            readInput()
        } catch (e: Exception) {
            echo("Error reading input: ${e.message}", err = true)
            throw ProgramResult(1)
        }

        if (ksonContent.isBlank()) {
            echo("Error: Input is empty. Provide a $CLI_DISPLAY_NAME document to validate.", err = true)
            echo("\nUse '$CLI_NAME validate --help' for usage information.", err = true)
            throw ProgramResult(1)
        }

        // Validate against schema if provided
        validateWithSchema(ksonContent)

        // Perform analysis
        val analysis = Kson.analyze(ksonContent, getFilePath())
        var outputString = ""
        if (analysis.errors.isEmpty()) {
            outputString += "✓ No errors or warnings found"
        } else {
            analysis.errors.forEach { outputString += errorFormat(it) }
        }

        if (showTokens) {
            outputString += "\n\nTokens:\n"
            analysis.tokens.forEach { token ->
                outputString += "  ${token.tokenType}: '${token.text}' at ${token.start.line}:${token.start.column}-${token.end.line}:${token.end.column}\n"
            }
        }

        if (analysis.errors.isEmpty()) {
            writeOutput(outputString)
        } else {
            echo(outputString.trimEnd(), err = true)
            throw ProgramResult(1)
        }
    }
}
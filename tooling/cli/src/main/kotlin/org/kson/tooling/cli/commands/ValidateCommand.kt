package org.kson.tooling.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.kson.Kson

class ValidateCommand : BaseKsonCommand() {
    override fun help(context: Context) = """
        |Validate KSON documents for syntax errors, warnings and tokens.
        |
        |Performs static analysis to detect issues without executing or converting the document.
        |Reports errors and warnings with line and column information.
        |
        |Examples:
        |${"\u0085"}Validate a KSON file:
        |${"\u0085"}  kson validate -i document.kson
        |${"\u0085"}
        |${"\u0085"}Analyze from stdin:
        |${"\u0085"}  cat file.kson | kson validate
        |${"\u0085"}
        |${"\u0085"}Show lexical tokens for debugging:
        |${"\u0085"}  kson validate -i file.kson --show-tokens
        |${"\u0085"}
        |${"\u0085"}Validate against schema before analyzing:
        |${"\u0085"}  kson validate -i file.kson -s schema.kson
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
            echo("Error: Input is empty. Provide a KSON document to validate.", err = true)
            echo("\nUse 'kson validate --help' for usage information.", err = true)
            throw ProgramResult(1)
        }

        // Validate against schema if provided
        validateWithSchema(ksonContent)

        // Perform analysis
        val analysis = Kson.analyze(ksonContent)
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
        }else{
            echo(outputString.trimEnd(), err = true)
            throw ProgramResult(1)
        }
    }
}
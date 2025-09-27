package org.kson.tooling.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.groups.*
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import org.kson.FormatOptions
import org.kson.FormattingStyle
import org.kson.IndentType
import org.kson.Kson

// Default command for formatting KSON
class KsonFormatCommand : BaseKsonCommand(name = "format") {
    override fun help(context: Context) = """
        |Format KSON documents with customizable indentation and style.
        |
        |Examples:
        |${"\u0085"}Format a KSON file with default settings:
        |${"\u0085"}  kson format -i input.kson -o formatted.kson
        |${"\u0085"}
        |${"\u0085"}Format with tabs instead of spaces:
        |${"\u0085"}  kson format -i input.kson --indent-tabs
        |${"\u0085"}
        |${"\u0085"}Use compact formatting style:
        |${"\u0085"}  kson format -i input.kson --style compact
        |${"\u0085"}
        |${"\u0085"}Validate against schema before formatting:
        |${"\u0085"}  kson format -i input.kson -s schema.kson -o output.kson
    """.trimMargin()

    private val indentType: IndentType by mutuallyExclusiveOptions(
        option("--indent-spaces", help = "Number of spaces for indentation")
            .int()
            .convert { IndentType.Spaces(it) },
        option("--indent-tabs", help = "Use tabs for indentation")
            .flag()
            .convert { if (it) IndentType.Tabs else null }
    ).single().default(IndentType.Spaces(2))

    private val style by option("--style", help = "Formatting style")
        .choice("plain", "delimited", "compact", "classic")
        .default("plain")

    override fun run() {
        super.run()  // Check for help display

        val ksonContent = try {
            readInput()
        } catch (e: Exception) {
            echo("Error reading input: ${e.message}", err = true)
            throw ProgramResult(1)
        }

        if (ksonContent.isBlank()) {
            echo("Error: Input is empty. Provide a KSON document to format.", err = true)
            throw ProgramResult(1)
        }

        // Validate against schema if provided
        validateWithSchema(ksonContent)

        val indentType = this.indentType

        val formattingStyle = when (style) {
            "plain" -> FormattingStyle.PLAIN
            "delimited" -> FormattingStyle.DELIMITED
            "compact" -> FormattingStyle.COMPACT
            "classic" -> FormattingStyle.CLASSIC
            else -> FormattingStyle.PLAIN
        }

        val formatOptions = FormatOptions(
            indentType = indentType,
            formattingStyle = formattingStyle
        )
        
        val formatted = Kson.format(ksonContent, formatOptions)

        writeOutput(formatted)
    }
}
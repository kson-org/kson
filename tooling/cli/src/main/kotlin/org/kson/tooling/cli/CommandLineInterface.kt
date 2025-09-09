package org.kson.tooling.cli

import com.github.ajalt.clikt.core.*
import org.kson.tooling.cli.commands.JsonCommand
import org.kson.tooling.cli.commands.YamlCommand
import org.kson.tooling.cli.commands.ValidateCommand
import org.kson.tooling.cli.commands.KsonFormatCommand


class KsonCli : CliktCommand(name = "kson") {
    override fun help(context: Context) = """
        |KSON CLI - A tool for working with KSON (KSON Structured Object Notation) files.
        |
        |KSON is a human-friendly data serialization format that supports JSON and YAML conversion.
        |Use the subcommands to transpile, analyze, or validate KSON documents.
        |
        |Examples:
        |${"\u0085"}Convert KSON to JSON:
        |${"\u0085"}  kson json -i input.kson -o output.json
        |${"\u0085"}
        |${"\u0085"}Convert KSON to YAML:
        |${"\u0085"}  kson yaml -i input.kson -o output.yaml
        |${"\u0085"}
        |${"\u0085"}Format KSON with custom formatting options:
        |${"\u0085"}  kson format -i input.kson --indent-spaces 4 -o formatted.kson
        |${"\u0085"}
        |${"\u0085"}Analyze KSON for errors:
        |${"\u0085"}  kson analyze -i file.kson
        |${"\u0085"}
        |${"\u0085"}Validate against a schema:
        |${"\u0085"}  kson json -i input.kson -s schema.kson -o output.json
        |${"\u0085"}
        |${"\u0085"}Read from stdin (use - or omit filename):
        |${"\u0085"}  cat data.kson | kson json
        |
        |For more help on a specific command, use: kson <command> --help
    """.trimMargin()

    init {
        context {
            allowInterspersedArgs = false
        }
    }

    override fun run() = Unit
}

fun main(args: Array<String>) {
    KsonCli()
        .subcommands(
            KsonFormatCommand(),
            ValidateCommand(),
            JsonCommand(),
            YamlCommand(),
        )
        .main(args)
}
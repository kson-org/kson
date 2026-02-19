package org.kson.tooling.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.versionOption
import org.kson.tooling.cli.commands.JsonCommand
import org.kson.tooling.cli.commands.YamlCommand
import org.kson.tooling.cli.commands.ValidateCommand
import org.kson.tooling.cli.commands.KsonFormatCommand
import org.kson.tooling.cli.generated.CLI_DISPLAY_NAME
import org.kson.tooling.cli.generated.FILE_EXTENSION
import org.kson.tooling.cli.generated.CLI_NAME
import org.kson.tooling.cli.generated.KSON_VERSION


class KsonCli : CliktCommand(name = CLI_NAME) {
    init {
        versionOption(KSON_VERSION, names = setOf("--version", "-V"))
    }
    override fun help(context: Context) = """
        |$CLI_DISPLAY_NAME CLI - A tool for working with $CLI_DISPLAY_NAME files.
        |
        |$CLI_DISPLAY_NAME is a human-friendly data serialization format that supports JSON and YAML conversion.
        |Use the subcommands to transpile, format, or validate $CLI_DISPLAY_NAME documents.
        |
        |Examples:
        |${"\u0085"}Convert $CLI_DISPLAY_NAME to JSON:
        |${"\u0085"}  $CLI_NAME json -i input.$FILE_EXTENSION -o output.json
        |${"\u0085"}
        |${"\u0085"}Convert $CLI_DISPLAY_NAME to YAML:
        |${"\u0085"}  $CLI_NAME yaml -i input.$FILE_EXTENSION -o output.yaml
        |${"\u0085"}
        |${"\u0085"}Format $CLI_DISPLAY_NAME with custom formatting options:
        |${"\u0085"}  $CLI_NAME format -i input.$FILE_EXTENSION --indent-spaces 4 -o formatted.$FILE_EXTENSION
        |${"\u0085"}
        |${"\u0085"}Validate $CLI_DISPLAY_NAME for errors:
        |${"\u0085"}  $CLI_NAME validate -i file.$FILE_EXTENSION
        |${"\u0085"}
        |${"\u0085"}Validate against a schema:
        |${"\u0085"}  $CLI_NAME json -i input.$FILE_EXTENSION -s schema.$FILE_EXTENSION -o output.json
        |${"\u0085"}
        |${"\u0085"}Read from stdin (omit -i flag):
        |${"\u0085"}  cat data.$FILE_EXTENSION | $CLI_NAME json
        |
        |For more help on a specific command, use: $CLI_NAME <command> --help
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
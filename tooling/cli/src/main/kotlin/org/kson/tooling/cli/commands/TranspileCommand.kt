package org.kson.tooling.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.kson.Kson
import org.kson.Result
import org.kson.TranspileOptions

/**
 * Base class for transpile commands (JSON, YAML, etc.)
 * Eliminates duplication between conversion commands.
 */
abstract class TranspileCommand(
    name: String,
    private val targetFormat: String,
    private val optionsFactory: (Boolean) -> TranspileOptions,
    private val converter: (String, TranspileOptions) -> Result
) : BaseKsonCommand(name = name) {

    private val cmdName = name

    override fun help(context: Context) = """
        |Convert KSON documents to $targetFormat format.
        |
        |Examples:
        |${"\u0085"}Convert KSON to $targetFormat:
        |${"\u0085"}  kson $cmdName -i input.kson -o output.${targetFormat.lowercase()}
        |${"\u0085"}
        |${"\u0085"}Read from stdin and output to stdout:
        |${"\u0085"}  cat data.kson | kson $cmdName
        |${"\u0085"}
        |${"\u0085"}Validate against schema before conversion:
        |${"\u0085"}  kson $cmdName -i input.kson -s schema.kson -o output.${targetFormat.lowercase()}
    """.trimMargin()

    val retainEmbedTags by option("--retain-tags", help = "Retain the embed tags of embed blocks")
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
            echo("Error: Input is empty. Provide a KSON document to convert.", err = true)
            throw ProgramResult(1)
        }

        // Validate against schema if provided
        validateWithSchema(ksonContent)

        val options = optionsFactory(retainEmbedTags)
        when (val result = converter(ksonContent, options)) {
            is Result.Success -> {
                writeOutput(result.output)
            }
            is Result.Failure -> {
                echo("Conversion failed with errors:", err = true)
                result.errors.forEach { error ->
                    echo("  ${errorFormat(error)}", err = true)
                }
                throw ProgramResult(0)
            }
        }
    }
}

class JsonCommand : TranspileCommand(
    name = "json",
    targetFormat = "JSON",
    optionsFactory = { retainEmbedTags -> TranspileOptions.Json(retainEmbedTags) },
    converter = { kson, options -> Kson.toJson(kson, options as TranspileOptions.Json) }
)

class YamlCommand : TranspileCommand(
    name = "yaml",
    targetFormat = "YAML",
    optionsFactory = { retainEmbedTags -> TranspileOptions.Yaml(retainEmbedTags) },
    converter = { kson, options -> Kson.toYaml(kson, options as TranspileOptions.Yaml) }
)
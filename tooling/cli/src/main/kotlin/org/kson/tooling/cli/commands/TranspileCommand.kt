package org.kson.tooling.cli.commands

import com.github.ajalt.clikt.core.Context
import org.kson.Kson
import org.kson.Result
import kotlin.system.exitProcess

/**
 * Base class for transpile commands (JSON, YAML, etc.)
 * Eliminates duplication between conversion commands.
 */
abstract class TranspileCommand(
    name: String,
    private val targetFormat: String,
    private val converter: (String) -> Result
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

    override fun run() {
        super.run()  // Check for help display

        val ksonContent = try {
            readInput()
        } catch (e: Exception) {
            System.err.println("Error reading input: ${e.message}")
            exitProcess(1)
        }

        if (ksonContent.isBlank()) {
            System.err.println("Error: Input is empty. Provide a KSON document to convert.")
            exitProcess(1)
        }

        // Validate against schema if provided
        validateWithSchema(ksonContent)

        when (val result = converter(ksonContent)) {
            is Result.Success -> {
                writeOutput(result.output)
            }
            is Result.Failure -> {
                System.err.println("Conversion failed with errors:")
                result.errors.forEach { error ->
                    System.err.println("  ${errorFormat(error)}")
                }
                exitProcess(1)
            }
        }
    }
}

class JsonCommand : TranspileCommand(
    name = "json",
    targetFormat = "JSON",
    converter = Kson::toJson
)

class YamlCommand : TranspileCommand(
    name = "yaml",
    targetFormat = "YAML",
    converter = Kson::toYaml
)
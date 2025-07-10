package org.kson.cli

import org.kson.CompileTarget
import org.kson.KsonCore
import org.kson.ParseResult
import org.kson.ast.AstNode
import org.kson.mpp.PlatformShim

/**
 * A [CommandLineInterface] for parsing [KsonCore].  Platform-specific configuration is injected in [platformShim],
 * and output is written to [out].  See [formatParseResult] for details on the output format.
 */
class CommandLineInterface(private val platformShim: PlatformShim, private val out: (String) -> Unit) {
    fun run(args: Array<String>) {
        val ksonSource = if (args.size > 1) {
            out("Usage: kson [file]")
            platformShim.exitFailure()
        } else if (args.size == 1) {
            val ksonFilePath = args[0]
            val ksonSourceFileContents = try {
                platformShim.readFile(ksonFilePath)
            } catch (e: Exception) {
                out("Failed to read file at path: $ksonFilePath. Error:")
                out(e.stackTraceToString())
                platformShim.exitFailure()
            }
            ksonSourceFileContents
        } else {
            out("--- Enter some kson source (Ctrl-D to finish): ---\n")
            val inputBuilder = StringBuilder()

            while (true) {
                val line = platformShim.readLine()
                if (line == null) {
                    // got EOF, we're done here
                    break
                } else {
                    inputBuilder.appendLine(line)
                }
            }

            inputBuilder.toString()
        }

        val parseResult = KsonCore.parseToAst(ksonSource)
        out(formatParseResult(parseResult))

        if (parseResult.hasErrors()) {
            platformShim.exitFailure()
        } else {
            platformShim.exitSuccess()
        }
    }
}

private fun formatParseResult(parseResult: ParseResult): String {
    // todo this is fine and useful output for now, but we'll likely want to
    //      make it pure kson or json so it's machine readable too
    return """
        |lexedTokens: [
        |"${parseResult.lexedTokens.joinToString(",\n  ", prefix = "  ")}"
        |]
        |
        |loggedMessages: [
        |${parseResult.messages.joinToString(",\n  ", prefix = "  ")}
        |]
        |
        |serializedAst: %
        |${parseResult.ast?.toSource(AstNode.Indent(), CompileTarget.Kson())}
        |%%
    """.trimMargin()
}

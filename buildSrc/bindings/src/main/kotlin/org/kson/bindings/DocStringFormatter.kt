package org.kson.bindings

/**
 * Responsible for formatting a KDoc docString into an idiomatic documentation format used by a
 * different programming language. With this we make sure the documentation of our bindings follows
 * existing conventions and works well with the target language's tools.
 */
interface DocStringFormatter {
    /**
     * Format the provided [docString] using the specified [indent]
     *
     * @return A string corresponding to the formatted docString
     */
    fun format(indent: String, docString: String?): String
}

object RustDocStringFormatter : DocStringFormatter {
    override fun format(indent: String, docString: String?): String {
        if (docString == null) {
            return ""
        }

        val builder = StringBuilder()
        val lines = docString.lines()
        lines.forEachIndexed { i, line ->
            if ((i == 0 || i == lines.size - 1) && line.trim().isEmpty()) {
                return@forEachIndexed
            }

            builder.append(indent)
            builder.append("///")
            builder.append(line)
            builder.append("\n")
        }

        return builder.toString()
    }
}

object PythonDocStringFormatter : DocStringFormatter {
    override fun format(indent: String, docString: String?): String {
        if (docString == null) {
            return ""
        }

        val tripleQuote = "\"\"\""

        val builder = StringBuilder()

        var lineCount = 0
        val lines = docString.lines()
        var firstNonEmptyLine = true
        lines.forEachIndexed { i, line ->
            if ((i == 0 || i == lines.size - 1) && line.isBlank()) {
                return@forEachIndexed
            }

            builder.append(indent)

            if (firstNonEmptyLine) {
                builder.append(tripleQuote)
                firstNonEmptyLine = false
            }

            builder.append(trimFirstSpace(line).trimEnd())
            builder.append("\n")
            lineCount++
        }

        if (builder.isNotEmpty()) {
            if (lineCount == 1) {
                builder.deleteAt(builder.length - 1)
            } else {
                builder.append(indent)
            }

            builder.append(tripleQuote)
            builder.append("\n")
        }

        return builder.toString()
    }

    fun trimFirstSpace(line: String): String {
        return if (line.firstOrNull() == ' ') {
            line.substring(1)
        } else {
            line
        }
    }
}
